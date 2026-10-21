package io.github.mangi.eta.ui.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mangi.eta.agent.extension.ExtensionAgent
import io.github.mangi.eta.agent.terminal.DetachedTaskSupervisor
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.SharedFolderMounts
import io.github.mangi.eta.agent.terminal.linuxPackageProfileReady
import io.github.mangi.eta.agent.terminal.terminalEnvironment
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.data.repository.ExtensionAgentSettingsRepository
import io.github.mangi.eta.data.repository.LinuxEnvironmentSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Activity 级状态所有者；配置变更只重建 UI，不替换正在运行的 Agent 会话。 */
internal class AgentAppViewModel(application: Application) : AndroidViewModel(application) {
    val state = AgentAppState(
        context = application,
        scope = viewModelScope,
    )
    val skills = AgentSkillsStore(application, viewModelScope)
    val memory = AgentMemoryStore(application, viewModelScope)
    val permissionHealth = PermissionHealthStore(application, viewModelScope)
    private val terminalHost = TerminalSessionHost.get(application)
    val terminalStore = terminalHost.terminal
    val consoleStore = terminalHost.console
    val extensionAgentLauncher = ExtensionAgentLauncher(
        context = application,
        daemonSupervisor = DetachedTaskSupervisor(
            logger = AndroidAgentLogger,
            recordsFile = DetachedTaskSupervisor.defaultRecordsFile(application),
            linuxRootfsPathProvider = { environment ->
                environment.linuxDistribution?.let { distribution ->
                    LinuxEnvironmentPaths.rootfsDir(application, distribution).absolutePath
                }
            },
            linuxSharedMountsProvider = { SharedFolderMounts.current() },
        ),
    )

    var extensionAgent by mutableStateOf(ExtensionAgentSettingsRepository.current(application))
        private set
    var extensionAgentState by mutableStateOf(ExtensionAgentUiState(agent = extensionAgent))
        private set
    private var extensionAgentJob: Job? = null
    private var extensionAgentRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            ExtensionAgentSettingsRepository.flow(application).collect { selected ->
                if (selected != extensionAgent) {
                    extensionAgent = selected
                    extensionAgentState = ExtensionAgentUiState(agent = selected)
                    refreshExtensionAgent()
                }
            }
        }
    }

    fun selectExtensionAgent(agent: ExtensionAgent) {
        if (agent == extensionAgent) return
        viewModelScope.launch {
            ExtensionAgentSettingsRepository.select(agent)
            extensionAgent = agent
            extensionAgentState = ExtensionAgentUiState(agent = agent)
            refreshExtensionAgent()
        }
    }

    fun refreshExtensionAgent() {
        if (extensionAgentJob?.isActive == true) return
        // 同一时刻只保留一个刷新协程：切换发行版/后端时旧结果不能覆盖新状态。
        extensionAgentRefreshJob?.cancel()
        extensionAgentRefreshJob = viewModelScope.launch {
            val agent = extensionAgent
            val status = withContext(Dispatchers.IO) {
                val distribution = LinuxEnvironmentSettingsRepository.current(getApplication())
                val backend = LinuxEnvironmentSettingsRepository.backend(getApplication(), distribution)
                val rootfs = LinuxEnvironmentPaths.rootfsDir(getApplication(), distribution, backend)
                val profile = io.github.mangi.eta.agent.terminal.LinuxPackageProfiles.ALL
                    .firstOrNull { it.id == agent.packageProfileId }
                if (profile == null || !linuxPackageProfileReady(rootfs, profile)) {
                    ExtensionAgentUiState(agent, ExtensionAgentPhase.NOT_INSTALLED)
                } else {
                    val runtime = extensionAgentLauncher.status(agent, distribution.terminalEnvironment, backend)
                    when {
                        runtime.running -> ExtensionAgentUiState(agent, ExtensionAgentPhase.RUNNING)
                        runtime.code != null -> ExtensionAgentUiState(agent, ExtensionAgentPhase.FAILED, runtime.code)
                        extensionAgentState.phase == ExtensionAgentPhase.FAILED -> extensionAgentState
                        else -> ExtensionAgentUiState(agent, ExtensionAgentPhase.READY)
                    }
                }
            }
            if (extensionAgent == agent) extensionAgentState = status
        }
    }

    fun launchExtensionAgent(onFinished: (ExtensionAgentLaunchResult) -> Unit) {
        if (extensionAgentJob?.isActive == true) return
        extensionAgentRefreshJob?.cancel()
        val agent = extensionAgent
        extensionAgentState = ExtensionAgentUiState(agent, ExtensionAgentPhase.STARTING)
        extensionAgentJob = viewModelScope.launch {
            val distribution = LinuxEnvironmentSettingsRepository.current(getApplication())
            val backend = LinuxEnvironmentSettingsRepository.backend(getApplication(), distribution)
            val result = try {
                extensionAgentLauncher.launch(agent, distribution.terminalEnvironment, backend)
            } finally {
                if (extensionAgentState.phase == ExtensionAgentPhase.STARTING) {
                    extensionAgentState = ExtensionAgentUiState(agent, ExtensionAgentPhase.READY)
                }
            }
            extensionAgentState = when (result) {
                is ExtensionAgentLaunchResult.Opened -> ExtensionAgentUiState(agent, ExtensionAgentPhase.RUNNING)
                is ExtensionAgentLaunchResult.Failed -> ExtensionAgentUiState(agent, ExtensionAgentPhase.FAILED, result.code)
            }
            onFinished(result)
        }
    }

    fun stopExtensionAgent() {
        extensionAgentRefreshJob?.cancel()
        val preparation = extensionAgentJob
        preparation?.cancel()
        extensionAgentJob = viewModelScope.launch {
            preparation?.join()
            val agent = extensionAgent
            val distribution = LinuxEnvironmentSettingsRepository.current(getApplication())
            val backend = LinuxEnvironmentSettingsRepository.backend(getApplication(), distribution)
            val stopped = extensionAgentLauncher.stop(agent, distribution.terminalEnvironment, backend)
            extensionAgentState = if (stopped) {
                ExtensionAgentUiState(agent, ExtensionAgentPhase.READY)
            } else {
                ExtensionAgentUiState(agent, ExtensionAgentPhase.FAILED, "STOP_FAILED")
            }
        }
    }
}
