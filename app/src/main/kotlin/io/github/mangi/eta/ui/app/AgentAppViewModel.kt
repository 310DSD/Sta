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
        viewModelScope.launch {
            val agent = extensionAgent
            val status = withContext(Dispatchers.IO) {
                val distribution = LinuxEnvironmentSettingsRepository.current(getApplication())
                val rootfs = LinuxEnvironmentPaths.rootfsDir(getApplication(), distribution)
                val profile = io.github.mangi.eta.agent.terminal.LinuxPackageProfiles.ALL
                    .firstOrNull { it.id == agent.packageProfileId }
                if (profile == null || !linuxPackageProfileReady(rootfs, profile)) {
                    ExtensionAgentUiState(agent, ExtensionAgentPhase.NOT_INSTALLED)
                } else {
                    val runtime = extensionAgentLauncher.status(agent, distribution.terminalEnvironment)
                    when {
                        runtime.running -> ExtensionAgentUiState(agent, ExtensionAgentPhase.RUNNING)
                        runtime.code != null -> ExtensionAgentUiState(agent, ExtensionAgentPhase.FAILED, runtime.code)
                        extensionAgentState.phase == ExtensionAgentPhase.FAILED -> extensionAgentState
                        else -> ExtensionAgentUiState(agent, ExtensionAgentPhase.READY)
                    }
                }
            }
            if (extensionAgentJob?.isActive != true) extensionAgentState = status
        }
    }

    fun launchExtensionAgent(onFinished: (ExtensionAgentLaunchResult) -> Unit) {
        if (extensionAgentJob?.isActive == true) return
        val agent = extensionAgent
        extensionAgentState = ExtensionAgentUiState(agent, ExtensionAgentPhase.STARTING)
        extensionAgentJob = viewModelScope.launch {
            val distribution = LinuxEnvironmentSettingsRepository.current(getApplication())
            val result = try {
                extensionAgentLauncher.launch(agent, distribution.terminalEnvironment)
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
        val preparation = extensionAgentJob
        preparation?.cancel()
        extensionAgentJob = viewModelScope.launch {
            preparation?.join()
            val agent = extensionAgent
            val distribution = LinuxEnvironmentSettingsRepository.current(getApplication())
            val status = extensionAgentLauncher.status(agent, distribution.terminalEnvironment)
            val stopped = if (status.taskId == null) status.code == null
            else extensionAgentLauncher.stop(agent, distribution.terminalEnvironment)
            extensionAgentState = if (stopped) {
                ExtensionAgentUiState(agent, ExtensionAgentPhase.READY)
            } else {
                ExtensionAgentUiState(agent, ExtensionAgentPhase.FAILED, status.code ?: "STOP_FAILED")
            }
        }
    }
}
