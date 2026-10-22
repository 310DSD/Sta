package io.github.mangi.eta.ui.app

import io.github.mangi.eta.agent.extension.ExtensionAgent
import io.github.mangi.eta.agent.terminal.DaemonLogsResult
import io.github.mangi.eta.agent.terminal.DaemonStartResult
import io.github.mangi.eta.agent.terminal.DetachedTaskStatus
import io.github.mangi.eta.agent.terminal.LinuxExecutionBackend
import io.github.mangi.eta.agent.terminal.TerminalEnvironment
import kotlinx.coroutines.delay

internal sealed interface ExtensionAgentLaunchResult {
    data class Opened(val url: String) : ExtensionAgentLaunchResult
    data class Failed(val code: String, val detail: String? = null) : ExtensionAgentLaunchResult
}

/**
 * Launch/reuse transaction for an extension agent's web mode: only a task created by this call
 * and not yet handed to a browser is stopped on failure.
 */
internal class ExtensionAgentWebSession(
    private val tasks: Tasks,
    private val openUrl: (String) -> Boolean,
    private val waitAttempts: Int = 60,
    private val waitIntervalMs: Long = 1_000,
) {
    interface Tasks {
        fun list(): List<DetachedTaskStatus>
        fun start(agent: ExtensionAgent, environment: TerminalEnvironment, identity: String): DaemonStartResult
        fun logs(id: String): DaemonLogsResult
        fun stop(id: String)
    }

    fun open(url: String): Boolean = openUrl(url)

    suspend fun launch(
        agent: ExtensionAgent,
        environment: TerminalEnvironment,
        identity: String,
        backend: LinuxExecutionBackend,
        pairRequired: Boolean = false,
        resolveUrl: suspend (String) -> String? = { null },
    ): ExtensionAgentLaunchResult {
        var createdTaskId: String? = null
        var opened = false
        try {
            val existing = tasks.list().lastOrNull {
                it.running && it.task.environment == environment && it.task.identity == identity &&
                    it.task.backend == backend && it.task.command.trim() in agent.acceptedCommands
            }
            val taskId = existing?.task?.id ?: when (
                val started = tasks.start(agent, environment, identity)
            ) {
                is DaemonStartResult.Started -> started.task.id.also { createdTaskId = it }
                is DaemonStartResult.Failed -> return ExtensionAgentLaunchResult.Failed(started.code)
            }
            var lastLogs: String? = null
            repeat(waitAttempts) {
                val status = tasks.list().firstOrNull { it.task.id == taskId }
                val logs = tasks.logs(taskId)
                if (logs.ok) lastLogs = logs.text
                if (status == null || !status.running) {
                    return ExtensionAgentLaunchResult.Failed(
                        "AGENT_EXITED",
                        lastLogs?.takeLast(MAX_ERROR_DETAIL_CHARS),
                    )
                }
                if (!logs.ok) {
                    return ExtensionAgentLaunchResult.Failed(logs.code.ifBlank { "LOGS_UNAVAILABLE" })
                }
                val url = addressFromLogs(agent, logs.text)
                if (url != null) {
                    val resolved = resolveUrl(url)
                    if (pairRequired && resolved == null) {
                        return ExtensionAgentLaunchResult.Failed("PAIRING_FAILED")
                    }
                    val targetUrl = resolved ?: url
                    opened = openUrl(targetUrl)
                    return if (opened) {
                        ExtensionAgentLaunchResult.Opened(targetUrl)
                    } else {
                        ExtensionAgentLaunchResult.Failed("BROWSER_UNAVAILABLE")
                    }
                }
                delay(waitIntervalMs)
            }
            return ExtensionAgentLaunchResult.Failed(
                "URL_TIMEOUT",
                lastLogs?.takeLast(MAX_ERROR_DETAIL_CHARS),
            )
        } finally {
            if (!opened) createdTaskId?.let(tasks::stop)
        }
    }

    companion object {
        private const val MAX_ERROR_DETAIL_CHARS = 800

        fun addressFromLogs(agent: ExtensionAgent, text: String): String? =
            agent.webUrlPattern.find(text)?.value
    }
}
