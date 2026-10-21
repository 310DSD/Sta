package io.github.mangi.eta.ui.app

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.github.mangi.eta.agent.extension.ExtensionAgent
import io.github.mangi.eta.agent.runtime.AgentExecutionService
import io.github.mangi.eta.agent.terminal.DetachedTaskSupervisor
import io.github.mangi.eta.agent.terminal.InstallerShellRunner
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxExecutionBackend
import io.github.mangi.eta.agent.terminal.LinuxPackageProfiles
import io.github.mangi.eta.agent.terminal.TerminalEnvironment
import io.github.mangi.eta.agent.terminal.TerminalRuntime
import io.github.mangi.eta.agent.terminal.linuxPackageProfileReady
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class ExtensionAgentRuntimeStatus(
    val taskId: String? = null,
    val running: Boolean = false,
    val url: String? = null,
    val code: String? = null,
)

/**
 * Starts an extension agent's web server as a daemon inside Eta's Linux rootfs, waits for the
 * loopback URL printed in its logs, and hands that URL to the system browser.
 */
internal class ExtensionAgentLauncher(
    private val context: Context,
    private val daemonSupervisor: DetachedTaskSupervisor,
) {
    private val session = ExtensionAgentWebSession(
        tasks = object : ExtensionAgentWebSession.Tasks {
            override fun list() = daemonSupervisor.list()
            override fun start(agent: ExtensionAgent, environment: TerminalEnvironment, identity: String) =
                daemonSupervisor.start(
                    command = agent.webCommand,
                    cwd = "/workspace",
                    identity = identity,
                    environment = environment,
                )
            override fun logs(id: String) = daemonSupervisor.readLogs(id)
            override fun stop(id: String) { daemonSupervisor.stop(id) }
        },
        openUrl = { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (_: android.content.ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        },
    )

    suspend fun launch(
        agent: ExtensionAgent,
        environment: TerminalEnvironment,
        backend: LinuxExecutionBackend,
    ): ExtensionAgentLaunchResult = mutexFor(agent).withLock {
        withContext(Dispatchers.IO) {
            val distribution = environment.linuxDistribution
                ?: return@withContext ExtensionAgentLaunchResult.Failed("INVALID_ENVIRONMENT")
            val rootfs = LinuxEnvironmentPaths.rootfsDir(context, distribution, backend)
            if (!LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath)) {
                return@withContext ExtensionAgentLaunchResult.Failed("LINUX_ENVIRONMENT_NOT_READY")
            }
            val profile = LinuxPackageProfiles.ALL.firstOrNull { it.id == agent.packageProfileId }
            if (profile == null || !linuxPackageProfileReady(rootfs, profile)) {
                return@withContext ExtensionAgentLaunchResult.Failed("PROFILE_NOT_INSTALLED")
            }
            val identity = TerminalRuntime.defaultIdentity(environment, rootfs.absolutePath)
            val job = currentCoroutineContext().job
            val launchId = "extension-agent-launch-${agent.id}"
            if (identity == "user" && !AgentExecutionService.acquire(context, launchId) { job.cancel() }) {
                return@withContext ExtensionAgentLaunchResult.Failed("BACKGROUND_START_NOT_ALLOWED")
            }
            try {
                currentCoroutineContext().ensureActive()
                session.launch(agent, environment, identity, backend) { serverUrl ->
                    val pairCommand = agent.pairCommand?.invoke(serverUrl)
                    if (pairCommand == null) {
                        null
                    } else {
                        val pairResult = InstallerShellRunner.run(
                            command = pairCommand,
                            timeoutSeconds = 20,
                            environment = environment,
                            linuxRootfsPath = rootfs.absolutePath,
                            identity = identity,
                        )
                        if (pairResult.exitCode == 0) {
                            PAIRING_URL_REGEX.find(pairResult.output)?.value
                        } else {
                            null
                        }
                    }
                }
            } finally {
                if (identity == "user") AgentExecutionService.release(launchId)
            }
        }
    }

    suspend fun status(
        agent: ExtensionAgent,
        environment: TerminalEnvironment,
        backend: LinuxExecutionBackend,
    ): ExtensionAgentRuntimeStatus = withContext(Dispatchers.IO) {
        val distribution = environment.linuxDistribution
            ?: return@withContext ExtensionAgentRuntimeStatus(code = "INVALID_ENVIRONMENT")
        val rootfs = LinuxEnvironmentPaths.rootfsDir(context, distribution, backend)
        if (!LinuxEnvironmentPaths.rootfsReady(rootfs.path)) {
            return@withContext ExtensionAgentRuntimeStatus(code = "LINUX_ENVIRONMENT_NOT_READY")
        }
        val identity = TerminalRuntime.defaultIdentity(environment, rootfs.path)
        if (identity == "root" && !TerminalRuntime.rootAvailable) {
            return@withContext ExtensionAgentRuntimeStatus(code = "ROOT_REQUIRED")
        }
        val matches = daemonSupervisor.list().filter {
            it.task.environment == environment && it.task.identity == identity &&
                it.task.backend == backend &&
                it.task.command.trim() in agent.acceptedCommands
        }
        val task = matches.lastOrNull { it.running } ?: matches.lastOrNull()
            ?: return@withContext ExtensionAgentRuntimeStatus()
        if (!task.running) {
            return@withContext ExtensionAgentRuntimeStatus(taskId = task.task.id, code = "AGENT_EXITED")
        }
        val logs = daemonSupervisor.readLogs(task.task.id)
        ExtensionAgentRuntimeStatus(
            taskId = task.task.id,
            running = true,
            url = if (logs.ok) ExtensionAgentWebSession.addressFromLogs(agent, logs.text) else null,
            code = if (logs.ok) null else logs.code,
        )
    }

    suspend fun stop(
        agent: ExtensionAgent,
        environment: TerminalEnvironment,
        backend: LinuxExecutionBackend,
    ): Boolean =
        mutexFor(agent).withLock {
            withContext(Dispatchers.IO) {
                val status = status(agent, environment, backend)
                if (status.taskId == null) return@withContext status.code == null
                daemonSupervisor.stop(status.taskId)
            }
        }

    private companion object {
        val launchMutexes = ConcurrentHashMap<ExtensionAgent, Mutex>()
        val PAIRING_URL_REGEX = Regex("""http://(?:127\.0\.0\.1|localhost):\d+/auth/connect/[A-Za-z0-9_-]+""")

        fun mutexFor(agent: ExtensionAgent): Mutex = launchMutexes.getOrPut(agent) { Mutex() }
    }
}
