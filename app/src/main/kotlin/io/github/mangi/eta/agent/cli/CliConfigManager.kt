package io.github.mangi.eta.agent.cli

import android.content.Context
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxFileExplorer
import io.github.mangi.eta.agent.terminal.ShellProcessSupervisor
import io.github.mangi.eta.data.repository.LinuxEnvironmentSettingsRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class CliFiles(
    val authJson: String? = null,
    val configToml: String? = null,
    val credentialsJson: String? = null,
    val settingsJson: String? = null,
)

internal sealed interface CliApplyResult {
    data object Success : CliApplyResult
    data object NotReady : CliApplyResult
    data class Failed(val message: String) : CliApplyResult
}

/**
 * 读取/写入 Codex、Claude Code 的登录文件与 provider 配置。
 *
 * 官方账号只做文件快照/恢复，不解析、不刷新 token；API Key/第三方账号只改 provider 相关字段，
 * 尽量保留用户已有的 hooks、MCP 与主题设置。
 */
internal class CliConfigManager(private val context: Context) {
    private val supervisor = ShellProcessSupervisor()
    private val appContext = context.applicationContext

    fun close() = supervisor.beginClosing()

    private fun rootfs(): File? {
        val distribution = LinuxEnvironmentSettingsRepository.current(appContext)
        val backend = LinuxEnvironmentSettingsRepository.backend(appContext, distribution)
        return LinuxEnvironmentPaths.rootfsDir(appContext, distribution, backend)
            .takeIf { LinuxEnvironmentPaths.rootfsReady(it.absolutePath) }
    }

    suspend fun capture(agent: CliAgent): CliFiles? = withContext(Dispatchers.IO) {
        val rootfs = rootfs() ?: return@withContext null
        CliFiles(
            authJson = read(rootfs, agent.authFile),
            configToml = read(rootfs, agent.configFile),
            credentialsJson = read(rootfs, agent.credentialsFile),
            settingsJson = read(rootfs, agent.settingsFile),
        )
    }

    suspend fun apply(account: CliAccount): CliApplyResult = withContext(Dispatchers.IO) {
        val agent = CliAgent.fromId(account.agentId) ?: return@withContext CliApplyResult.Failed("未知的 CLI")
        val rootfs = rootfs() ?: return@withContext CliApplyResult.NotReady
        when (agent) {
            CliAgent.CODEX -> applyCodex(rootfs, account)
            CliAgent.CLAUDE_CODE -> applyClaude(rootfs, account)
        }
    }

    private fun applyCodex(rootfs: File, account: CliAccount): CliApplyResult {
        if (account.kind == CliAccountKind.OFFICIAL) {
            val auth = account.authJson ?: return CliApplyResult.Failed("没有可恢复的官方登录快照，请先在终端登录并导入")
            backupOnce(rootfs, CliAgent.CODEX.authFile)
            backupOnce(rootfs, CliAgent.CODEX.configFile)
            if (!write(rootfs, CliAgent.CODEX.authFile, auth)) return CliApplyResult.Failed("写入 Codex 登录文件失败")
            account.configToml?.let {
                if (!write(rootfs, CliAgent.CODEX.configFile, it)) return CliApplyResult.Failed("写入 Codex 配置失败")
            }
            return CliApplyResult.Success
        }
        if (account.apiKey.isBlank()) return CliApplyResult.Failed("API Key 不能为空")
        backupOnce(rootfs, CliAgent.CODEX.authFile)
        backupOnce(rootfs, CliAgent.CODEX.configFile)
        val auth = JSONObject().put("OPENAI_API_KEY", account.apiKey).toString(2)
        if (!write(rootfs, CliAgent.CODEX.authFile, auth)) return CliApplyResult.Failed("写入 Codex 登录文件失败")
        if (account.kind == CliAccountKind.API_KEY) {
            if (account.model.isBlank()) return CliApplyResult.Success
            val existing = read(rootfs, CliAgent.CODEX.configFile).orEmpty()
            if (!write(rootfs, CliAgent.CODEX.configFile, updateTomlModel(existing, account.model))) {
                return CliApplyResult.Failed("写入 Codex 配置失败")
            }
            return CliApplyResult.Success
        }
        if (account.baseUrl.isBlank()) return CliApplyResult.Failed("第三方提供商需要 baseURL")
        val config = buildString {
            if (account.model.isNotBlank()) appendLine("model = ${tomlQuote(account.model)}")
            appendLine("model_provider = \"eta\"")
            appendLine("[model_providers.eta]")
            appendLine("name = \"Eta\"")
            appendLine("base_url = ${tomlQuote(account.baseUrl.trimEnd('/'))}")
            appendLine("env_key = \"OPENAI_API_KEY\"")
            appendLine("wire_api = \"responses\"")
        }
        if (!write(rootfs, CliAgent.CODEX.configFile, config)) return CliApplyResult.Failed("写入 Codex 配置失败")
        return CliApplyResult.Success
    }

    private fun applyClaude(rootfs: File, account: CliAccount): CliApplyResult {
        if (account.kind == CliAccountKind.OFFICIAL) {
            val credentials = account.credentialsJson
                ?: return CliApplyResult.Failed("没有可恢复的官方登录快照，请先在终端登录并导入")
            backupOnce(rootfs, CliAgent.CLAUDE_CODE.credentialsFile)
            backupOnce(rootfs, CliAgent.CLAUDE_CODE.settingsFile)
            if (!write(rootfs, CliAgent.CLAUDE_CODE.credentialsFile, credentials)) {
                return CliApplyResult.Failed("写入 Claude Code 登录文件失败")
            }
            if (account.settingsJson != null) {
                if (!write(rootfs, CliAgent.CLAUDE_CODE.settingsFile, account.settingsJson)) {
                    return CliApplyResult.Failed("写入 Claude Code 配置失败")
                }
            } else {
                val settings = read(rootfs, CliAgent.CLAUDE_CODE.settingsFile)
                if (!write(rootfs, CliAgent.CLAUDE_CODE.settingsFile, removeClaudeManagedEnv(settings))) {
                    return CliApplyResult.Failed("写入 Claude Code 配置失败")
                }
            }
            return CliApplyResult.Success
        }
        if (account.apiKey.isBlank()) return CliApplyResult.Failed("API Key 不能为空")
        if (account.kind == CliAccountKind.THIRD_PARTY && account.baseUrl.isBlank()) {
            return CliApplyResult.Failed("第三方提供商需要 baseURL")
        }
        val existing = read(rootfs, CliAgent.CLAUDE_CODE.settingsFile)
        val merged = mergeClaudeSettings(existing, account)
        if (!write(rootfs, CliAgent.CLAUDE_CODE.settingsFile, merged)) {
            return CliApplyResult.Failed("写入 Claude Code 配置失败")
        }
        return CliApplyResult.Success
    }

    private fun mergeClaudeSettings(existing: String?, account: CliAccount): String {
        val root = runCatching { JSONObject(existing ?: "{}") }.getOrElse { JSONObject() }
        val env = root.optJSONObject("env") ?: JSONObject()
        listOf("ANTHROPIC_BASE_URL", "ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_API_KEY", "ANTHROPIC_MODEL").forEach(env::remove)
        when (account.kind) {
            CliAccountKind.API_KEY -> env.put("ANTHROPIC_API_KEY", account.apiKey)
            CliAccountKind.THIRD_PARTY -> {
                env.put("ANTHROPIC_BASE_URL", account.baseUrl.trimEnd('/'))
                env.put("ANTHROPIC_AUTH_TOKEN", account.apiKey)
            }
            CliAccountKind.OFFICIAL -> Unit
        }
        if (account.model.isNotBlank()) env.put("ANTHROPIC_MODEL", account.model)
        root.put("env", env)
        return root.toString(2)
    }

    private fun removeClaudeManagedEnv(existing: String?): String {
        val root = runCatching { JSONObject(existing ?: "{}") }.getOrElse { JSONObject() }
        val env = root.optJSONObject("env") ?: return root.toString(2)
        listOf("ANTHROPIC_BASE_URL", "ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_API_KEY", "ANTHROPIC_MODEL").forEach(env::remove)
        root.put("env", env)
        return root.toString(2)
    }

    private fun read(rootfs: File, path: String): String? {
        if (path.isBlank()) return null
        return when (val result = LinuxFileExplorer.readText(supervisor, rootfs, path)) {
            is LinuxFileExplorer.ReadResult.Text -> result.content
            else -> null
        }
    }

    private fun write(rootfs: File, path: String, content: String): Boolean {
        if (path.isBlank()) return false
        return LinuxFileExplorer.writeText(supervisor, rootfs, path, content) == LinuxFileExplorer.WriteResult.Success
    }

    private fun backupOnce(rootfs: File, path: String) {
        if (path.isBlank()) return
        val backup = "$path.eta-backup"
        if (read(rootfs, backup) != null) return
        read(rootfs, path)?.let { write(rootfs, backup, it) }
    }

    private fun updateTomlModel(existing: String, model: String): String {
        val line = "model = ${tomlQuote(model)}"
        val lines = existing.lines().toMutableList()
        val index = lines.indexOfFirst { it.trimStart().startsWith("model =") || it.trimStart().startsWith("model=") }
        if (index >= 0) lines[index] = line else lines.add(0, line)
        return lines.joinToString("\n")
    }

    private fun tomlQuote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
