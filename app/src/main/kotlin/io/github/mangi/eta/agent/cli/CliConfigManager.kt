package io.github.mangi.eta.agent.cli

import android.content.Context
import androidx.annotation.StringRes
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxFileExplorer
import io.github.mangi.eta.agent.terminal.ShellProcessSupervisor
import io.github.mangi.eta.data.model.AnthropicProviderSetting
import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.model.OpenAiCompatibleProviderSetting
import io.github.mangi.eta.data.model.selectedOrFirstModel
import io.github.mangi.eta.data.repository.LinuxEnvironmentSettingsRepository
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RemoteModelFetcher
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONException

/** 官方账号只需要登录文件快照；provider 配置不再整份保存，避免污染官方登录。 */
internal data class CliFiles(
    val authJson: String? = null,
    val credentialsJson: String? = null,
)

internal sealed interface CliCaptureResult {
    data class Success(val files: CliFiles) : CliCaptureResult
    data object NotReady : CliCaptureResult
    data object NoLogin : CliCaptureResult
    data class Failed(@StringRes val messageRes: Int) : CliCaptureResult
}

internal sealed interface CliApplyResult {
    data object Success : CliApplyResult
    data object NotReady : CliApplyResult
    data class Failed(@StringRes val messageRes: Int) : CliApplyResult
}

/**
 * Codex / Claude Code 的登录文件与 provider 配置读写。
 *
 * 参考 CC Switch 的切换语义：
 * - 只改连接信息（端点、Key、模型）和 Sta 自己的 provider 表，其余配置原样保留；
 * - 第一次改写前备份，备份失败就中止，不做半套写入；
 * - 官方账号只快照登录文件，不再保存整份 provider 配置。
 */
internal class CliConfigManager(private val context: Context) {
    private val supervisor = ShellProcessSupervisor()
    private val appContext = context.applicationContext

    fun close() = supervisor.beginClosing()

    /** 在线读取账号对应端点的 `/models`；官方 OAuth 账号没有可直接使用的 API Key。 */
    suspend fun fetchModels(account: CliAccount): Result<List<CliModelInfo>> = withContext(Dispatchers.IO) {
        try {
            val agent = CliAgent.fromId(account.agentId)
                ?: error("Unknown CLI")
            if (account.kind == CliAccountKind.OFFICIAL) error("Official accounts use the CLI login")
            if (account.apiKey.isBlank()) error("API key is empty")
            val baseUrl = when (agent) {
                CliAgent.CODEX -> account.baseUrl.ifBlank { "https://api.openai.com/v1" }
                CliAgent.CLAUDE_CODE -> account.baseUrl.ifBlank { "https://api.anthropic.com" }
            }
            val provider = when (agent) {
                CliAgent.CODEX -> OpenAiCompatibleProviderSetting(
                    id = "cli-${account.id}",
                    name = account.name,
                    baseUrl = baseUrl,
                    apiKey = account.apiKey,
                )
                CliAgent.CLAUDE_CODE -> AnthropicProviderSetting(
                    id = "cli-${account.id}",
                    name = account.name,
                    baseUrl = baseUrl,
                    apiKey = account.apiKey,
                )
            }
            Result.success(RemoteModelFetcher.fetch(provider).getOrThrow().map(::toCliModelInfo))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Throwable) {
            Result.failure(failure)
        }
    }

    /** 从 Eta 当前选中的 provider/model 填充连接信息、上下文和思考等级。 */
    suspend fun syncFromEta(account: CliAccount): CliAccount? = withContext(Dispatchers.IO) {
        val providerId = RuntimeConfigRepository.selectedProviderIdFlow().first()
        val modelId = RuntimeConfigRepository.selectedModelIdFlow().first()
        val provider = providerId?.let { ProviderRepository.providerById(it) } ?: return@withContext null
        val model = provider.selectedOrFirstModel(modelId) ?: return@withContext null
        account.copy(
            apiKey = provider.apiKey.ifBlank { account.apiKey },
            baseUrl = provider.baseUrl.ifBlank { account.baseUrl },
            model = model.modelId.ifBlank { account.model },
            contextWindow = model.contextWindowOverride ?: model.contextWindow ?: account.contextWindow,
            maxOutputTokens = model.maxOutputTokens ?: account.maxOutputTokens,
            reasoningEffort = model.effectiveReasoningCapabilities?.defaultEffort?.wireValue
                ?.takeUnless { it == "default" }
                ?: account.reasoningEffort,
        )
    }

    private fun toCliModelInfo(model: Model): CliModelInfo = CliModelInfo(
        id = model.modelId.ifBlank { model.id },
        displayName = model.displayName.ifBlank { model.modelId.ifBlank { model.id } },
        contextWindow = model.contextWindowOverride ?: model.contextWindow,
        maxOutputTokens = model.maxOutputTokens,
        reasoningEfforts = model.effectiveReasoningCapabilities
            ?.selectableEfforts
            ?.map { it.wireValue }
            .orEmpty(),
        defaultReasoningEffort = model.effectiveReasoningCapabilities
            ?.defaultEffort
            ?.wireValue
            .orEmpty(),
    )

    private fun rootfs(): File? {
        val distribution = LinuxEnvironmentSettingsRepository.current(appContext)
        val backend = LinuxEnvironmentSettingsRepository.backend(appContext, distribution)
        return LinuxEnvironmentPaths.rootfsDir(appContext, distribution, backend)
            .takeIf { LinuxEnvironmentPaths.rootfsReady(it.absolutePath) }
    }

    suspend fun capture(agent: CliAgent): CliCaptureResult = withContext(Dispatchers.IO) {
        try {
            val rootfs = rootfs() ?: return@withContext CliCaptureResult.NotReady
            val json = when (agent) {
                CliAgent.CODEX -> read(rootfs, agent.authFile)
                CliAgent.CLAUDE_CODE -> read(rootfs, agent.credentialsFile)
            } ?: return@withContext CliCaptureResult.NoLogin
            val official = when (agent) {
                CliAgent.CODEX -> CliAuthEditor.isOfficialCodexAuth(json)
                CliAgent.CLAUDE_CODE -> CliAuthEditor.isOfficialClaudeCredentials(json)
            }
            if (!official) return@withContext CliCaptureResult.Failed(R.string.cli_error_invalid_auth)
            CliCaptureResult.Success(
                CliFiles(
                    authJson = json.takeIf { agent == CliAgent.CODEX },
                    credentialsJson = json.takeIf { agent == CliAgent.CLAUDE_CODE },
                ),
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            CliCaptureResult.Failed(R.string.cli_error_unexpected)
        }
    }

    suspend fun apply(account: CliAccount): CliApplyResult = withContext(Dispatchers.IO) {
        try {
            val agent = CliAgent.fromId(account.agentId)
                ?: return@withContext CliApplyResult.Failed(R.string.cli_error_unknown_agent)
            val rootfs = rootfs() ?: return@withContext CliApplyResult.NotReady
            when (agent) {
                CliAgent.CODEX -> applyCodex(rootfs, account)
                CliAgent.CLAUDE_CODE -> applyClaude(rootfs, account)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            CliApplyResult.Failed(R.string.cli_error_unexpected)
        }
    }

    private fun applyCodex(rootfs: File, account: CliAccount): CliApplyResult {
        val authPath = CliAgent.CODEX.authFile
        val configPath = CliAgent.CODEX.configFile

        if (account.kind == CliAccountKind.OFFICIAL) {
            val auth = account.authJson ?: return CliApplyResult.Failed(R.string.cli_error_missing_snapshot)
            if (!CliAuthEditor.isOfficialCodexAuth(auth)) {
                return CliApplyResult.Failed(R.string.cli_error_invalid_auth)
            }
        } else if (account.apiKey.isBlank()) {
            return CliApplyResult.Failed(R.string.cli_error_api_key_empty)
        }
        if (account.kind == CliAccountKind.THIRD_PARTY && account.baseUrl.isBlank()) {
            return CliApplyResult.Failed(R.string.cli_error_base_url_required)
        }

        val existing = readRequired(rootfs, configPath)
            ?: return CliApplyResult.Failed(R.string.cli_error_read_config)
        val route = when (account.kind) {
            CliAccountKind.OFFICIAL -> CodexRoute.Official(
                model = account.model,
                contextWindow = account.contextWindow,
                reasoningEffort = account.reasoningEffort,
            )
            CliAccountKind.API_KEY -> CodexRoute.ApiKey(
                model = account.model,
                contextWindow = account.contextWindow,
                reasoningEffort = account.reasoningEffort,
            )
            CliAccountKind.THIRD_PARTY -> CodexRoute.ThirdParty(
                baseUrl = account.baseUrl,
                apiKey = account.apiKey,
                model = account.model,
                contextWindow = account.contextWindow,
                reasoningEffort = account.reasoningEffort,
            )
        }
        val updated = try {
            CodexConfigEditor.apply(existing, route)
        } catch (_: CodexProfileOverrideException) {
            return CliApplyResult.Failed(R.string.cli_error_profile_override)
        }

        if (!backupOnce(rootfs, configPath)) {
            return CliApplyResult.Failed(R.string.cli_error_backup_failed)
        }
        if (account.kind != CliAccountKind.THIRD_PARTY && !backupOnce(rootfs, authPath)) {
            return CliApplyResult.Failed(R.string.cli_error_backup_failed)
        }
        if (!write(rootfs, configPath, updated)) {
            return CliApplyResult.Failed(R.string.cli_error_write_config)
        }
        if (account.kind != CliAccountKind.THIRD_PARTY) {
            val auth = when (account.kind) {
                CliAccountKind.OFFICIAL -> account.authJson
                    ?: return CliApplyResult.Failed(R.string.cli_error_missing_snapshot)
                CliAccountKind.API_KEY -> CliAuthEditor.codexApiKey(account.apiKey)
                CliAccountKind.THIRD_PARTY -> error("unreachable")
            }
            if (!write(rootfs, authPath, auth)) {
                return CliApplyResult.Failed(R.string.cli_error_write_auth)
            }
        }
        return CliApplyResult.Success
    }

    private fun applyClaude(rootfs: File, account: CliAccount): CliApplyResult {
        val settingsPath = CliAgent.CLAUDE_CODE.settingsFile
        val credentialsPath = CliAgent.CLAUDE_CODE.credentialsFile

        if (account.kind == CliAccountKind.OFFICIAL) {
            val credentials = account.credentialsJson
                ?: return CliApplyResult.Failed(R.string.cli_error_missing_snapshot)
            if (!CliAuthEditor.isOfficialClaudeCredentials(credentials)) {
                return CliApplyResult.Failed(R.string.cli_error_invalid_auth)
            }
        } else if (account.apiKey.isBlank()) {
            return CliApplyResult.Failed(R.string.cli_error_api_key_empty)
        }
        if (account.kind == CliAccountKind.THIRD_PARTY && account.baseUrl.isBlank()) {
            return CliApplyResult.Failed(R.string.cli_error_base_url_required)
        }

        val existing = readRequired(rootfs, settingsPath)
            ?: return CliApplyResult.Failed(R.string.cli_error_read_config)
        val updated = try {
            when (account.kind) {
                CliAccountKind.OFFICIAL -> ClaudeSettingsEditor.merge(
                    existing, account.kind, "", "", account.model,
                    account.contextWindow, account.reasoningEffort,
                )
                CliAccountKind.API_KEY -> ClaudeSettingsEditor.merge(
                    existing, account.kind, account.apiKey, account.baseUrl, account.model,
                    account.contextWindow, account.reasoningEffort,
                )
                CliAccountKind.THIRD_PARTY -> ClaudeSettingsEditor.merge(
                    existing, account.kind, account.apiKey, account.baseUrl, account.model,
                    account.contextWindow, account.reasoningEffort,
                )
            }
        } catch (_: JSONException) {
            return CliApplyResult.Failed(R.string.cli_error_invalid_settings)
        }

        if (!backupOnce(rootfs, settingsPath)) {
            return CliApplyResult.Failed(R.string.cli_error_backup_failed)
        }
        if (account.kind == CliAccountKind.OFFICIAL && !backupOnce(rootfs, credentialsPath)) {
            return CliApplyResult.Failed(R.string.cli_error_backup_failed)
        }
        if (!write(rootfs, settingsPath, updated)) {
            return CliApplyResult.Failed(R.string.cli_error_write_config)
        }
        if (account.kind == CliAccountKind.OFFICIAL) {
            val credentials = account.credentialsJson
                ?: return CliApplyResult.Failed(R.string.cli_error_missing_snapshot)
            if (!write(rootfs, credentialsPath, credentials)) {
                return CliApplyResult.Failed(R.string.cli_error_write_auth)
            }
        }
        return CliApplyResult.Success
    }

    /** 读取可能不存在的配置文件；文件存在但读不出来时返回 null，由调用方失败关闭。 */
    private fun readRequired(rootfs: File, path: String): String? {
        if (path.isBlank()) return ""
        return when (exists(rootfs, path)) {
            false -> ""
            null -> null
            true -> read(rootfs, path)
        }
    }

    private fun read(rootfs: File, path: String): String? {
        if (path.isBlank()) return null
        return when (val result = LinuxFileExplorer.readText(supervisor, rootfs, path)) {
            is LinuxFileExplorer.ReadResult.Text ->
                if (result.truncated) null else result.content
            else -> null
        }
    }

    private fun write(rootfs: File, path: String, content: String): Boolean {
        if (path.isBlank()) return false
        return LinuxFileExplorer.writeText(
            supervisor = supervisor,
            rootfsDir = rootfs,
            linuxPath = path,
            content = content,
            privateMode = true,
        ) == LinuxFileExplorer.WriteResult.Success
    }

    /** 首次改写前备份；备份失败或已有备份但读不出来时返回 false，调用方不得继续写。 */
    private fun backupOnce(rootfs: File, path: String): Boolean {
        if (path.isBlank()) return true
        val backup = "$path.eta-backup"
        when (exists(rootfs, backup)) {
            true -> return true
            null -> return false
            false -> Unit
        }
        return when (exists(rootfs, path)) {
            false -> true
            null -> false
            true -> read(rootfs, path)?.let { write(rootfs, backup, it) } ?: false
        }
    }

    /** true = 存在；false = 不存在；null = 无法判断。 */
    private fun exists(rootfs: File, path: String): Boolean? {
        if (path.isBlank()) return false
        return when (LinuxFileExplorer.stat(supervisor, rootfs, path)) {
            is LinuxFileExplorer.StatResult.Success -> true
            LinuxFileExplorer.StatResult.NotFound -> false
            else -> null
        }
    }
}
