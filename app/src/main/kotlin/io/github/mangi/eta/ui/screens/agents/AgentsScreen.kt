package io.github.mangi.eta.ui.screens.agents

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Web
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.cli.CliAgent
import io.github.mangi.eta.agent.extension.ExtensionAgent
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxPackageProfiles
import io.github.mangi.eta.agent.terminal.linuxPackageProfileReady
import io.github.mangi.eta.data.repository.LinuxEnvironmentSettingsRepository
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.EtaTextButton
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 首页二级菜单的智能体总入口：Web 与 CLI 分类，组内按名称排序。 */
@Composable
internal fun AgentsScreen(
    onBack: () -> Unit,
    onOpenWebAgent: (ExtensionAgent) -> Unit,
    onOpenCliAgent: (CliAgent) -> Unit,
    onOpenCliAccounts: () -> Unit,
    onOpenCloudCli: () -> Unit,
    onOpenAcp: () -> Unit,
    onOpenLinuxEnvironment: () -> Unit,
    runningCliTags: Set<String>,
    runningExtensionAgents: Set<ExtensionAgent>,
    onStartCliAgent: (CliAgent) -> Unit,
    onStopCliAgent: (CliAgent) -> Unit,
    onStopCloudCli: () -> Unit,
    onStopAcp: () -> Unit,
) {
    val context = LocalContext.current
    val webAgents = remember {
        ExtensionAgent.entries.filter { it != ExtensionAgent.CLOUDCLI && it != ExtensionAgent.ACP }
            .sortedBy { it.displayName.lowercase() }
    }
    val cliAgents = remember { CliAgent.entries.sortedBy { it.displayName.lowercase() } }
    val distribution = LinuxEnvironmentSettingsRepository.current(context)
    val backend = LinuxEnvironmentSettingsRepository.backend(context, distribution)
    val rootfs = remember(distribution, backend) {
        LinuxEnvironmentPaths.rootfsDir(context, distribution, backend)
    }
    val cliInstalled by produceState(emptyMap<CliAgent, Boolean>(), cliAgents, distribution, backend) {
        value = withContext(Dispatchers.IO) {
            cliAgents.associateWith { agent ->
                val profile = LinuxPackageProfiles.ALL.firstOrNull { it.id == agent.packageProfileId }
                profile != null && linuxPackageProfileReady(rootfs, profile)
            }
        }
    }
    val cloudCliInstalled by produceState(false, distribution, backend) {
        value = withContext(Dispatchers.IO) {
            linuxPackageProfileReady(rootfs, LinuxPackageProfiles.CLOUDCLI)
        }
    }
    val acpInstalled by produceState(false, distribution, backend) {
        value = withContext(Dispatchers.IO) {
            linuxPackageProfileReady(rootfs, LinuxPackageProfiles.ACP)
        }
    }

    MiuixScaffoldPage(title = stringResource(R.string.agents_title), onBack = onBack) {
        item(key = "web") {
            EtaPreferenceGroupTitle(stringResource(R.string.agents_web))
            EtaPreferenceGroup {
                webAgents.forEachIndexed { index, agent ->
                    if (index > 0) EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = agent.displayName,
                        summary = stringResource(R.string.agents_web_summary),
                        startAction = { WebAgentIcon(agent) },
                        onClick = { onOpenWebAgent(agent) },
                    )
                }
            }
        }
        item(key = "cli") {
            EtaPreferenceGroupTitle(stringResource(R.string.agents_cli))
            EtaPreferenceGroup {
                cliAgents.forEachIndexed { index, agent ->
                    if (index > 0) EtaPreferenceDivider()
                    val installed = cliInstalled[agent] == true
                    val running = agent.id in runningCliTags
                    val summary = when {
                        !installed -> stringResource(R.string.cli_account_install_hint)
                        running -> stringResource(R.string.cli_agent_running)
                        else -> stringResource(R.string.agents_cli_summary)
                    }
                    val actions: (@Composable () -> Unit)? = if (installed) {
                        {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                EtaTextButton(
                                    text = stringResource(
                                        if (running) R.string.cli_stop_running else R.string.cli_start_running,
                                    ),
                                    onClick = if (running) {
                                        { onStopCliAgent(agent) }
                                    } else {
                                        { onStartCliAgent(agent) }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                EtaTextButton(
                                    text = stringResource(R.string.cli_account_terminal),
                                    onClick = { onOpenCliAgent(agent) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    } else {
                        null
                    }
                    EtaArrowPreference(
                        title = agent.displayName,
                        summary = summary,
                        startAction = {
                            EtaPreferenceIcon(
                                icon = if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                tint = if (running) EtaPreferenceColors.Red else EtaPreferenceColors.Blue,
                            )
                        },
                        bottomAction = actions,
                        onClick = {
                            if (installed) onOpenCliAgent(agent) else onOpenLinuxEnvironment()
                        },
                    )
                }
                EtaPreferenceDivider()
                val cloudCliRunning = ExtensionAgent.CLOUDCLI in runningExtensionAgents
                val cloudCliSummary = when {
                    !cloudCliInstalled -> stringResource(R.string.cli_account_install_hint)
                    cloudCliRunning -> stringResource(R.string.cli_agent_running)
                    else -> stringResource(R.string.agents_cloudcli_summary)
                }
                val cloudCliActions: (@Composable () -> Unit)? = if (cloudCliInstalled) {
                    {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EtaTextButton(
                                text = stringResource(
                                    if (cloudCliRunning) R.string.cli_stop_running else R.string.cli_start_running,
                                ),
                                onClick = if (cloudCliRunning) onStopCloudCli else onOpenCloudCli,
                                modifier = Modifier.weight(1f),
                            )
                            EtaTextButton(
                                text = stringResource(R.string.cli_account_open),
                                onClick = onOpenCloudCli,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else {
                    null
                }
                EtaArrowPreference(
                    title = stringResource(R.string.agents_cloudcli),
                    summary = cloudCliSummary,
                    startAction = {
                        EtaPreferenceIcon(
                            icon = if (cloudCliRunning) Icons.Rounded.Stop else Icons.Rounded.Web,
                            tint = if (cloudCliRunning) EtaPreferenceColors.Red else EtaPreferenceColors.Blue,
                        )
                    },
                    bottomAction = cloudCliActions,
                    onClick = {
                        if (cloudCliInstalled) onOpenCloudCli() else onOpenLinuxEnvironment()
                    },
                )
                EtaPreferenceDivider()
                val acpRunning = ExtensionAgent.ACP in runningExtensionAgents
                val acpSummary = when {
                    !acpInstalled -> stringResource(R.string.agents_acp_install_hint)
                    acpRunning -> stringResource(R.string.cli_agent_running)
                    else -> stringResource(R.string.agents_acp_summary)
                }
                val acpActions: (@Composable () -> Unit)? = if (acpInstalled) {
                    {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EtaTextButton(
                                text = stringResource(
                                    if (acpRunning) R.string.cli_stop_running else R.string.cli_start_running,
                                ),
                                onClick = if (acpRunning) onStopAcp else onOpenAcp,
                                modifier = Modifier.weight(1f),
                            )
                            EtaTextButton(
                                text = stringResource(R.string.cli_account_open),
                                onClick = onOpenAcp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else {
                    null
                }
                EtaArrowPreference(
                    title = stringResource(R.string.agents_acp),
                    summary = acpSummary,
                    startAction = {
                        EtaPreferenceIcon(
                            icon = if (acpRunning) Icons.Rounded.Stop else Icons.Rounded.Web,
                            tint = if (acpRunning) EtaPreferenceColors.Red else EtaPreferenceColors.Blue,
                        )
                    },
                    bottomAction = acpActions,
                    onClick = {
                        if (acpInstalled) onOpenAcp() else onOpenLinuxEnvironment()
                    },
                )
            }
        }
        item(key = "accounts") {
            EtaPreferenceGroupTitle(stringResource(R.string.agents_accounts))
            EtaPreferenceGroup {
                EtaArrowPreference(
                    title = stringResource(R.string.agents_cli_accounts),
                    summary = stringResource(R.string.agents_cli_accounts_summary),
                    startAction = {
                        EtaPreferenceIcon(Icons.Rounded.AccountCircle, tint = EtaPreferenceColors.Blue)
                    },
                    onClick = onOpenCliAccounts,
                )
            }
        }
    }
}

@Composable
private fun WebAgentIcon(agent: ExtensionAgent) {
    val res = when (agent) {
        ExtensionAgent.OPENCODE -> R.drawable.ic_opencode
        ExtensionAgent.DSH -> R.drawable.provider_logo_deepseek
        ExtensionAgent.KIMI -> R.drawable.provider_logo_kimi
        ExtensionAgent.CLOUDCLI -> R.drawable.ic_opencode
        ExtensionAgent.ACP -> R.drawable.ic_opencode
    }
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp)),
    )
}
