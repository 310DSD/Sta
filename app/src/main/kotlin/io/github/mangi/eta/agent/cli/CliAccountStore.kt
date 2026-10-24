package io.github.mangi.eta.agent.cli

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** CLI 账号配置的本地存储；文件位于 App 私有 noBackup 目录。 */
internal class CliAccountStore(context: Context) {
    private val file = File(context.applicationContext.noBackupFilesDir, "cli_accounts.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Synchronized
    fun load(): CliAccountState = runCatching {
        if (!file.isFile) CliAccountState() else normalize(json.decodeFromString<CliAccountState>(file.readText()))
    }.getOrDefault(CliAccountState())

    private fun normalizeAgent(agentId: String): CliAgent? =
        CliAgent.fromId(agentId) ?: when (agentId.trim().lowercase()) {
            "claude", "claude_code", "claudecode" -> CliAgent.CLAUDE_CODE
            "codex-cli", "openai-codex" -> CliAgent.CODEX
            else -> null
        }

    /**
     * 第一版账号文件可能只有旧字段；保留它并补齐今天界面依赖的 id/agent/name，
     * 避免旧账号在列表里“看得见但点不动”。
     */
    private fun normalize(state: CliAccountState): CliAccountState {
        val accounts = state.accounts.mapNotNull { account ->
            val agent = normalizeAgent(account.agentId)
                ?: if (account.credentialsJson != null) CliAgent.CLAUDE_CODE else CliAgent.CODEX
            account.copy(
                id = account.id.trim().ifBlank { UUID.randomUUID().toString() },
                agentId = agent.id,
                name = account.name.trim().ifBlank { agent.displayName },
            )
        }
        val ids = accounts.mapTo(mutableSetOf()) { it.id }
        val active = buildMap {
            state.activeByAgent.forEach { (agentId, accountId) ->
                val agent = normalizeAgent(agentId) ?: return@forEach
                if (accountId in ids) put(agent.id, accountId)
            }
        }
        return state.copy(accounts = accounts, activeByAgent = active)
    }

    /** 保存失败返回 false，调用方必须提示用户，不能假装成功。 */
    @Synchronized
    fun save(state: CliAccountState): Boolean = runCatching {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(state))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
        true
    }.getOrDefault(false)

    fun upsert(account: CliAccount): CliAccountState? {
        val state = load()
        val accounts = state.accounts.filterNot { it.id == account.id } + account
        val next = state.copy(accounts = accounts)
        return next.takeIf { save(it) }
    }

    fun delete(accountId: String): CliAccountState? {
        val state = load()
        val account = state.accounts.firstOrNull { it.id == accountId }
        val active = state.activeByAgent.toMutableMap()
        if (account != null && active[account.agentId] == accountId) active.remove(account.agentId)
        val next = state.copy(
            accounts = state.accounts.filterNot { it.id == accountId },
            activeByAgent = active,
        )
        return next.takeIf { save(it) }
    }

    fun setActive(agentId: String, accountId: String): CliAccountState? {
        val state = load()
        val next = state.copy(activeByAgent = state.activeByAgent + (agentId to accountId))
        return next.takeIf { save(it) }
    }
}
