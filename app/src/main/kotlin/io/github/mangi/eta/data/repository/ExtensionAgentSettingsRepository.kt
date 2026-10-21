package io.github.mangi.eta.data.repository

import android.content.Context
import io.github.mangi.eta.agent.extension.ExtensionAgent
import io.github.mangi.eta.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Selected extension agent shown by the home-screen web launcher. */
internal object ExtensionAgentSettingsRepository {
    @Volatile
    private var cached: ExtensionAgent? = null

    fun current(context: Context): ExtensionAgent = cached ?: ExtensionAgent.OPENCODE

    fun flow(context: Context): Flow<ExtensionAgent> =
        SettingsDataStore.extensionAgentFlow().map { id ->
            ExtensionAgent.fromId(id).also { cached = it }
        }

    suspend fun select(agent: ExtensionAgent) {
        cached = agent
        SettingsDataStore.setExtensionAgent(agent.id)
    }
}
