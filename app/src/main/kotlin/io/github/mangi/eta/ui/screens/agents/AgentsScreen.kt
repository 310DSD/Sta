package io.github.mangi.eta.ui.screens.agents

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Terminal
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
    onOpenT3Code: () -> Unit,
    onOpenLinuxEnvironment: () -> Unit,
) {
    val context = LocalContext.current
    val webAgents = remember {
        ExtensionAgent.entries.filter { it != ExtensionAgent.T3_CODE }.sortedBy { it.displayName.lowercase() }
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
    val t3Installed by produceState(false, distribution, backend) {
        value = withContext(Dispatchers.IO) {
            linuxPackageProfileReady(rootfs, LinuxPackageProfiles.T3_CODE)
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
                    EtaArrowPreference(
                        title = agent.displayName,
                        summary = stringResource(
                            if (installed) R.string.agents_cli_summary else R.string.cli_account_install_hint,
                        ),
                        startAction = {
                            EtaPreferenceIcon(Icons.Rounded.Terminal, tint = EtaPreferenceColors.Blue)
                        },
                        onClick = {
                            if (installed) onOpenCliAgent(agent) else onOpenLinuxEnvironment()
                        },
                    )
                }
                EtaPreferenceDivider()
                EtaArrowPreference(
                    title = stringResource(R.string.agents_t3_code),
                    summary = stringResource(
                        if (t3Installed) R.string.agents_t3_code_summary else R.string.cli_account_install_hint,
                    ),
                    startAction = {
                        EtaPreferenceIcon(Icons.Rounded.Web, tint = EtaPreferenceColors.Blue)
                    },
                    onClick = {
                        if (t3Installed) onOpenT3Code() else onOpenLinuxEnvironment()
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
        ExtensionAgent.T3_CODE -> R.drawable.ic_opencode
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
