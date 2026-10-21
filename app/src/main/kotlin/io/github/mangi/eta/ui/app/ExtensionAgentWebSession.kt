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
    data class Failed(val code: String) : ExtensionAgentLaunchResult
}

/**
 * Launch/reuse transaction for an extension agent's web mode: only a task created by this call
 * and not yet handed to a browser is stopped on failure.
 */
internal class ExtensionAgentWebSession(
    private val tasks: Tasks,
    private val openUrl: (String) -> Boolean,
    private val waitAttempts: Int = 90,
    private val waitIntervalMs: Long = 500,
) {
    interface Tasks {
        fun list(): List<DetachedTaskStatus>
        fun start(agent: ExtensionAgent, environment: TerminalEnvironment, identity: String): DaemonStartResult
        fun logs(id: String): DaemonLogsResult
        fun stop(id: String)
    }

    suspend fun launch(
        agent: ExtensionAgent,
        environment: TerminalEnvironment,
        identity: String,
        backend: LinuxExecutionBackend,
    ): ExtensionAgentLaunchResult {
        var createdTaskId: String? = null
        var opened = false
        try {
            val existing = tasks.list().firstOrNull {
                it.running && it.task.environment == environment && it.task.identity == identity &&
                    it.task.backend == backend && it.task.command.trim() in agent.acceptedCommands
            }
            val taskId = existing?.task?.id ?: when (
                val started = tasks.start(agent, environment, identity)
            ) {
                is DaemonStartResult.Started -> started.task.id.also { createdTaskId = it }
                is DaemonStartResult.Failed -> return ExtensionAgentLaunchResult.Failed(started.code)
            }
            repeat(waitAttempts) {
                val status = tasks.list().firstOrNull { it.task.id == taskId }
                if (status == null || !status.running) {
                    return ExtensionAgentLaunchResult.Failed("AGENT_EXITED")
                }
                val logs = tasks.logs(taskId)
                if (!logs.ok) {
                    return ExtensionAgentLaunchResult.Failed(logs.code.ifBlank { "LOGS_UNAVAILABLE" })
                }
                val url = addressFromLogs(agent, logs.text)
                if (url != null) {
                    opened = openUrl(url)
                    return if (opened) {
                        ExtensionAgentLaunchResult.Opened(url)
                    } else {
                        ExtensionAgentLaunchResult.Failed("BROWSER_UNAVAILABLE")
                    }
                }
                delay(waitIntervalMs)
            }
            return ExtensionAgentLaunchResult.Failed("URL_TIMEOUT")
        } finally {
            if (!opened) createdTaskId?.let(tasks::stop)
        }
    }

    companion object {
        fun addressFromLogs(agent: ExtensionAgent, text: String): String? =
            agent.webUrlPattern.find(text)?.value
    }
}
