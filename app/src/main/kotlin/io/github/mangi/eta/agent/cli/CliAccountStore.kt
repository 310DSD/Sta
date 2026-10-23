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

    @Synchronized
    fun save(state: CliAccountState) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(state))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    fun upsert(account: CliAccount): CliAccountState {
        val state = load()
        val accounts = state.accounts.filterNot { it.id == account.id } + account
        return state.copy(accounts = accounts).also(::save)
    }

    fun delete(accountId: String): CliAccountState {
        val state = load()
        val account = state.accounts.firstOrNull { it.id == accountId }
        val active = state.activeByAgent.toMutableMap()
        if (account != null && active[account.agentId] == accountId) active.remove(account.agentId)
        return state.copy(accounts = state.accounts.filterNot { it.id == accountId }, activeByAgent = active)
            .also(::save)
    }

    fun setActive(agentId: String, accountId: String): CliAccountState {
        val state = load()
        return state.copy(activeByAgent = state.activeByAgent + (agentId to accountId)).also(::save)
    }
}
