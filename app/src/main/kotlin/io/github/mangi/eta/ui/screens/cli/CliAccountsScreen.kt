package io.github.mangi.eta.ui.screens.cli

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.cli.CliAccount
import io.github.mangi.eta.agent.cli.CliAccountKind
import io.github.mangi.eta.agent.cli.CliAccountStore
import io.github.mangi.eta.agent.cli.CliAgent
import io.github.mangi.eta.agent.cli.CliApplyResult
import io.github.mangi.eta.agent.cli.CliConfigManager
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxPackageProfiles
import io.github.mangi.eta.agent.terminal.linuxPackageProfileReady
import io.github.mangi.eta.data.repository.LinuxEnvironmentSettingsRepository
import io.github.mangi.eta.ui.components.EtaCard
import io.github.mangi.eta.ui.components.EtaTextButton
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID

/** Codex / Claude Code 的账号与第三方 provider 管理；手动切换，不自动轮换。 */
@Composable
internal fun CliAccountsScreen(
    context: Context,
    onBack: () -> Unit,
    onOpenTerminal: (CliAgent) -> Unit,
) {
    val appContext = context.applicationContext
    val store = remember { CliAccountStore(appContext) }
    val manager = remember { CliConfigManager(appContext) }
    DisposableEffect(Unit) { onDispose { manager.close() } }

    var state by remember { mutableStateOf(store.load()) }
    var selectedAgent by remember { mutableStateOf(CliAgent.CODEX) }
    var busy by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CliAccount?>(null) }
    val scope = rememberCoroutineScope()
    val toast = remember(appContext) { { text: String ->
        Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show()
    } }

    val distribution = LinuxEnvironmentSettingsRepository.current(appContext)
    val backend = LinuxEnvironmentSettingsRepository.backend(appContext, distribution)
    val rootfs = remember(distribution, backend) {
        LinuxEnvironmentPaths.rootfsDir(appContext, distribution, backend)
    }
    val installed = remember(selectedAgent, distribution, backend) {
        val profile = LinuxPackageProfiles.ALL.firstOrNull { it.id == selectedAgent.packageProfileId }
        profile != null && linuxPackageProfileReady(rootfs, profile)
    }

    val accounts = state.accounts.filter { it.agentId == selectedAgent.id }
    val activeId = state.activeByAgent[selectedAgent.id]

    fun switchTo(account: CliAccount) {
        if (busy) return
        busy = true
        scope.launch {
            val result = manager.apply(account)
            busy = false
            when (result) {
                CliApplyResult.Success -> {
                    state = store.setActive(account.agentId, account.id)
                    toast(appContext.getString(R.string.cli_account_applied))
                }
                CliApplyResult.NotReady -> toast(appContext.getString(R.string.cli_account_install_hint))
                is CliApplyResult.Failed -> toast(result.message)
            }
        }
    }

    MiuixScaffoldPage(
        title = stringResource(R.string.cli_agents_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = { showAdd = true }, enabled = !busy) {
                Icon(Icons.Rounded.Add, stringResource(R.string.cli_account_add), Modifier.size(22.dp))
            }
            IconButton(onClick = { onOpenTerminal(selectedAgent) }, enabled = !busy) {
                Icon(Icons.Rounded.Terminal, stringResource(R.string.cli_account_terminal), Modifier.size(21.dp))
            }
        },
    ) {
        item(key = "agents") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CliAgent.entries.forEach { agent ->
                    EtaTextButton(
                        text = agent.displayName,
                        onClick = { selectedAgent = agent },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (!installed) {
            item(key = "install-hint") {
                Text(
                    text = stringResource(R.string.cli_account_install_hint),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }
        if (accounts.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.cli_account_empty),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                )
            }
        }
        items(accounts, key = { it.id }) { account ->
            EtaCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(text = account.name, style = MiuixTheme.textStyles.body2)
                            Text(
                                text = account.kind.label(),
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        if (account.id == activeId) {
                            Text(
                                text = stringResource(R.string.cli_account_active),
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (account.kind != CliAccountKind.OFFICIAL) {
                        val detail = listOf(account.baseUrl, account.model).filter(String::isNotBlank).joinToString(" · ")
                        if (detail.isNotBlank()) {
                            Text(
                                text = detail,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EtaTextButton(
                            text = stringResource(R.string.cli_account_switch),
                            onClick = { switchTo(account) },
                            enabled = !busy && account.id != activeId,
                        )
                        EtaTextButton(
                            text = stringResource(R.string.cli_account_delete),
                            onClick = { deleteTarget = account },
                            enabled = !busy,
                        )
                    }
                }
            }
        }
        item(key = "official-hint") {
            Text(
                text = stringResource(R.string.cli_account_official_hint),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }

    if (showAdd) {
        AddCliAccountDialog(
            agent = selectedAgent,
            onDismiss = { showAdd = false },
            onSave = { account ->
                state = store.upsert(account)
                showAdd = false
                toast(appContext.getString(R.string.cli_account_saved))
            },
            manager = manager,
            toast = toast,
        )
    }

    deleteTarget?.let { account ->
        EtaWindowDialog(
            show = true,
            title = stringResource(R.string.cli_account_delete),
            summary = account.name,
            onDismissRequest = { deleteTarget = null },
        ) {
            MiuixDialogActions(
                confirmText = stringResource(R.string.cli_account_delete),
                onCancel = { deleteTarget = null },
                onConfirm = {
                    state = store.delete(account.id)
                    deleteTarget = null
                },
                destructive = true,
            )
        }
    }
}

@Composable
private fun AddCliAccountDialog(
    agent: CliAgent,
    onDismiss: () -> Unit,
    onSave: (CliAccount) -> Unit,
    manager: CliConfigManager,
    toast: (String) -> Unit,
) {
    var kind by remember { mutableStateOf(CliAccountKind.API_KEY) }
    var name by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val captureFailedText = stringResource(R.string.cli_account_capture_failed)

    EtaWindowDialog(
        show = true,
        title = stringResource(R.string.cli_account_add),
        summary = agent.displayName,
        onDismissRequest = onDismiss,
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CliAccountKind.entries.forEach { option ->
                    EtaTextButton(
                        text = option.shortLabel(),
                        onClick = { kind = option },
                        enabled = !importing,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.cli_account_name),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (kind != CliAccountKind.OFFICIAL) {
                Spacer(Modifier.height(6.dp))
                TextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = stringResource(R.string.cli_account_key),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == CliAccountKind.THIRD_PARTY) {
                    Spacer(Modifier.height(6.dp))
                    TextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = stringResource(R.string.cli_account_base_url),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                TextField(
                    value = model,
                    onValueChange = { model = it },
                    label = stringResource(R.string.cli_account_model),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = stringResource(R.string.cli_account_official_hint),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            MiuixDialogActions(
                confirmText = if (kind == CliAccountKind.OFFICIAL) {
                    stringResource(R.string.cli_account_import)
                } else {
                    stringResource(R.string.cli_account_add)
                },
                onCancel = onDismiss,
                onConfirm = {
                    if (kind == CliAccountKind.OFFICIAL) {
                        if (!importing) {
                            importing = true
                            scope.launch {
                                val files = manager.capture(agent)
                                importing = false
                                if (files == null ||
                                    (files.authJson == null && files.credentialsJson == null)
                                ) {
                                    toast(captureFailedText)
                                    return@launch
                                }
                                onSave(
                                    CliAccount(
                                        id = UUID.randomUUID().toString(),
                                        agentId = agent.id,
                                        name = name.ifBlank { "${agent.displayName} Official" },
                                        kind = CliAccountKind.OFFICIAL,
                                        authJson = files.authJson,
                                        configToml = files.configToml,
                                        credentialsJson = files.credentialsJson,
                                        settingsJson = files.settingsJson,
                                    ),
                                )
                            }
                        }
                    } else {
                        onSave(
                            CliAccount(
                                id = UUID.randomUUID().toString(),
                                agentId = agent.id,
                                name = name.ifBlank { agent.displayName },
                                kind = kind,
                                apiKey = apiKey.trim(),
                                baseUrl = baseUrl.trim(),
                                model = model.trim(),
                            ),
                        )
                    }
                },
                confirmEnabled = !importing && (kind == CliAccountKind.OFFICIAL || apiKey.isNotBlank()),
            )
        }
    }
}

private fun CliAccountKind.shortLabel(): String = when (this) {
    CliAccountKind.OFFICIAL -> "Official"
    CliAccountKind.API_KEY -> "Key"
    CliAccountKind.THIRD_PARTY -> "Third-party"
}

private fun CliAccountKind.label(): String = when (this) {
    CliAccountKind.OFFICIAL -> "Official account"
    CliAccountKind.API_KEY -> "API Key"
    CliAccountKind.THIRD_PARTY -> "Third-party provider"
}
