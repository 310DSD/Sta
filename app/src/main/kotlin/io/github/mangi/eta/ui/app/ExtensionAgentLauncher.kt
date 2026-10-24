package io.github.mangi.eta.ui.app

import android.content.Context
import android.content.Intent
import io.github.mangi.eta.agent.acp.AcpGuiInstaller
import io.github.mangi.eta.agent.extension.ExtensionAgent
import io.github.mangi.eta.ui.web.ExtensionAgentWebActivity
import io.github.mangi.eta.agent.runtime.AgentExecutionService
import io.github.mangi.eta.agent.terminal.DetachedTaskSupervisor
import io.github.mangi.eta.agent.terminal.InstallerShellRunner
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxExecutionBackend
import io.github.mangi.eta.agent.terminal.LinuxPackageProfiles
import io.github.mangi.eta.agent.terminal.TerminalEnvironment
import io.github.mangi.eta.agent.terminal.TerminalRuntime
import io.github.mangi.eta.agent.terminal.isLinux
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
    /** 允许调用方接管打开动作（例如在同一个 WebView 里新开标签）。 */
    private val openUrlOverride: ((String) -> Boolean)? = null,
) {
    private val lastUrls = ConcurrentHashMap<ExtensionAgent, String>()
    private val session = ExtensionAgentWebSession(
        tasks = object : ExtensionAgentWebSession.Tasks {
            override fun list() = daemonSupervisor.list()
            override fun start(agent: ExtensionAgent, environment: TerminalEnvironment, identity: String) =
                daemonSupervisor.start(
                    command = agent.webCommand,
                    cwd = resolveWebCwd(agent, environment, identity),
                    identity = identity,
                    environment = environment,
                )
            override fun logs(id: String) = daemonSupervisor.readLogs(id)
            override fun stop(id: String) { daemonSupervisor.stop(id) }
        },
        openUrl = { url -> openUrlOverride?.invoke(url) ?: openInNewActivity(url) },
    )

    private fun openInNewActivity(url: String): Boolean =
        try {
            context.startActivity(
                Intent(context, ExtensionAgentWebActivity::class.java)
                    .putExtra(ExtensionAgentWebActivity.EXTRA_URL, url)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: android.content.ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }

    /**
     * 免 Root Linux 里 `/root` 可能是 root:700，PRoot 的普通应用身份无法 `cd` 进去。
     * 这种情况下退回 PRoot 已挂载的 `/workspace`，避免守护进程在真正启动前就失败。
     */
    private fun resolveWebCwd(
        agent: ExtensionAgent,
        environment: TerminalEnvironment,
        identity: String,
    ): String {
        val requested = agent.webCwd
        if (identity != "user" || !environment.isLinux || requested == "/workspace") return requested
        val distribution = environment.linuxDistribution ?: return requested
        val hostRoot = java.io.File(
            LinuxEnvironmentPaths.rootfsDir(context, distribution, LinuxExecutionBackend.PROOT),
            requested.trimStart('/'),
        )
        return if (hostRoot.isDirectory && hostRoot.canRead() && hostRoot.canExecute()) requested else "/workspace"
    }

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
            if (agent == ExtensionAgent.ACP && !AcpGuiInstaller.install(context, distribution, backend)) {
                return@withContext ExtensionAgentLaunchResult.Failed("ACP_ASSETS_NOT_INSTALLED")
            }
            val identity = TerminalRuntime.defaultIdentity(environment, rootfs.absolutePath)
            // Stop old launcher forms before starting the current wrapper; otherwise an old
            // `dsh web`/`opencode serve` task can hold the fixed port and be reused without
            // the new mobile CSS / pairing logic.
            daemonSupervisor.list()
                .filter {
                    it.running && it.task.environment == environment && it.task.identity == identity &&
                        it.task.backend == backend &&
                        agent.legacyCommands.any { legacy -> it.task.command.contains(legacy) }
                }
                .forEach { daemonSupervisor.stop(it.task.id) }
            val job = currentCoroutineContext().job
            val launchId = "extension-agent-launch-${agent.id}"
            if (identity == "user" && !AgentExecutionService.acquire(
                    context,
                    launchId,
                    allowBoundFallback = true,
                ) { job.cancel() }) {
                return@withContext ExtensionAgentLaunchResult.Failed("BACKGROUND_START_NOT_ALLOWED")
            }
            try {
                currentCoroutineContext().ensureActive()
                val result = session.launch(
                    agent = agent,
                    environment = environment,
                    identity = identity,
                    backend = backend,
                    pairRequired = agent.pairCommand != null,
                ) { serverUrl ->
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
                if (result is ExtensionAgentLaunchResult.Opened) lastUrls[agent] = result.url
                result
            } finally {
                if (identity == "user") AgentExecutionService.release(launchId)
            }
        }
    }

    /**
     * 已运行任务快速路径：只读一次状态和日志，URL 已知时直接打开 WebView，
     * 不再走 launch 的安装检查、锁、日志轮询和配对流程。
     */
    suspend fun openRunning(
        agent: ExtensionAgent,
        environment: TerminalEnvironment,
        backend: LinuxExecutionBackend,
    ): String? = withContext(Dispatchers.IO) {
        val status = status(agent, environment, backend)
        if (!status.running) return@withContext null
        val url = status.url ?: lastUrls[agent]
        if (url.isNullOrBlank()) return@withContext null
        if (session.open(url)) url else null
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

    /** Returns null on success, otherwise a stable error code suitable for [ExtensionAgentUiState]. */
    suspend fun stop(
        agent: ExtensionAgent,
        environment: TerminalEnvironment,
        backend: LinuxExecutionBackend,
    ): String? =
        mutexFor(agent).withLock {
            withContext(Dispatchers.IO) {
                val status = status(agent, environment, backend)
                if (status.taskId == null) return@withContext status.code
                if (daemonSupervisor.stop(status.taskId)) null else "STOP_FAILED"
            }
        }

    private companion object {
        val launchMutexes = ConcurrentHashMap<ExtensionAgent, Mutex>()
        val PAIRING_URL_REGEX = Regex("""http://(?:127\.0\.0\.1|localhost):\d+/auth/connect/[A-Za-z0-9_-]+""")

        fun mutexFor(agent: ExtensionAgent): Mutex = launchMutexes.getOrPut(agent) { Mutex() }
    }
}
