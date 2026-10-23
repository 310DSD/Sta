package io.github.mangi.eta.agent.cli

import android.content.Context
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** CLI 账号配置的本地存储；文件位于 App 私有 noBackup 目录。 */
internal class CliAccountStore(context: Context) {
    private val file = File(context.applicationContext.noBackupFilesDir, "cli_accounts.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Synchronized
    fun load(): CliAccountState = runCatching {
        if (!file.isFile) CliAccountState() else json.decodeFromString<CliAccountState>(file.readText())
    }.getOrDefault(CliAccountState())

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
