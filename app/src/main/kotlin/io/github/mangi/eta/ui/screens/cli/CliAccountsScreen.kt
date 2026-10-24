package io.github.mangi.eta.ui.screens.cli

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.cli.CliAccount
import io.github.mangi.eta.agent.cli.CliAccountKind
import io.github.mangi.eta.agent.cli.CliAccountStore
import io.github.mangi.eta.agent.cli.CliAgent
import io.github.mangi.eta.agent.cli.CliApplyResult
import io.github.mangi.eta.agent.cli.CliCaptureResult
import io.github.mangi.eta.agent.cli.CliConfigManager
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxPackageProfiles
import io.github.mangi.eta.agent.terminal.linuxPackageProfileReady
import io.github.mangi.eta.data.repository.LinuxEnvironmentSettingsRepository
import io.github.mangi.eta.ui.insets.imePaddingSafe
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaChoiceChip
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.EtaPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDefaults
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.EtaTextButton
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onOpenLinuxEnvironment: () -> Unit,
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
    var settingsTarget by remember { mutableStateOf<CliAccount?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val toast = remember(appContext) { { text: String ->
        Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show()
    } }

    val distribution = LinuxEnvironmentSettingsRepository.current(appContext)
    val backend = LinuxEnvironmentSettingsRepository.backend(appContext, distribution)
    val rootfs = remember(distribution, backend) {
        LinuxEnvironmentPaths.rootfsDir(appContext, distribution, backend)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val installed by produceState(false, selectedAgent, distribution, backend, refreshKey) {
        value = withContext(Dispatchers.IO) {
            try {
                val profile = LinuxPackageProfiles.ALL.firstOrNull { it.id == selectedAgent.packageProfileId }
                profile != null && linuxPackageProfileReady(rootfs, profile)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                false
            }
        }
    }

    val accounts = state.accounts.filter { it.agentId == selectedAgent.id }
    val activeId = state.activeByAgent[selectedAgent.id]
    val appliedText = stringResource(R.string.cli_account_applied)
    val saveFailedText = stringResource(R.string.cli_error_save_failed)
    val installHintText = stringResource(R.string.cli_account_install_hint)
    val unexpectedText = stringResource(R.string.cli_error_unexpected)

    fun switchTo(account: CliAccount) {
        if (busy) return
        busy = true
        scope.launch {
            val result = try {
                manager.apply(account)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                CliApplyResult.Failed(R.string.cli_error_unexpected)
            } finally {
                busy = false
            }
            when (result) {
                CliApplyResult.Success -> {
                    val next = runCatching { store.setActive(account.agentId, account.id) }.getOrNull()
                    if (next == null) {
                        toast(saveFailedText)
                    } else {
                        state = next
                        toast(appliedText)
                    }
                }
                CliApplyResult.NotReady -> toast(installHintText)
                is CliApplyResult.Failed -> toast(appContext.getString(result.messageRes))
            }
        }
    }

    MiuixScaffoldPage(
        title = stringResource(R.string.cli_accounts_title),
        onBack = onBack,
        modifier = Modifier.imePaddingSafe(),
        actions = {
            IconButton(
                onClick = { showAdd = true },
                enabled = !busy,
            ) {
                Icon(Icons.Rounded.Add, stringResource(R.string.cli_account_add), Modifier.size(21.dp))
            }
            IconButton(
                onClick = {
                    if (installed) onOpenTerminal(selectedAgent) else toast(installHintText)
                },
                enabled = !busy,
            ) {
                Icon(Icons.Rounded.Terminal, stringResource(R.string.cli_account_terminal), Modifier.size(21.dp))
            }
        },
    ) {
        item(key = "agents") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EtaPreferenceDefaults.SidePadding)
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CliAgent.entries.forEach { agent ->
                    EtaChoiceChip(
                        text = agent.displayName,
                        selected = selectedAgent == agent,
                        onClick = { selectedAgent = agent },
                        enabled = !busy,
                    )
                }
            }
        }

        if (!installed) {
            item(key = "install") {
                EtaPreferenceGroupTitle(stringResource(R.string.cli_account_install_section))
                EtaPreferenceGroup {
                    EtaArrowPreference(
                        title = stringResource(R.string.cli_account_install_action),
                        summary = installHintText,
                        startAction = {
                            EtaPreferenceIcon(Icons.Rounded.Download, tint = EtaPreferenceColors.Blue)
                        },
                        onClick = onOpenLinuxEnvironment,
                    )
                }
            }
        }

        item(key = "accounts") {
            EtaPreferenceGroupTitle(stringResource(R.string.cli_account_list_section))
            EtaPreferenceGroup {
                if (accounts.isEmpty()) {
                    ListEmptyState(
                        title = stringResource(R.string.cli_account_empty),
                        summary = stringResource(R.string.cli_account_empty_summary),
                        icon = Icons.Rounded.Terminal,
                    )
                } else {
                    accounts.forEachIndexed { index, account ->
                        if (index > 0) EtaPreferenceDivider()
                        AccountPreference(
                            account = account,
                            active = account.id == activeId,
                            busy = busy,
                            onSwitch = { switchTo(account) },
                            onSettings = { settingsTarget = account },
                            onDelete = { deleteTarget = account },
                        )
                    }
                }
            }
        }

        item(key = "add") {
            EtaPreferenceGroupTitle(stringResource(R.string.cli_account_add_section))
            EtaPreferenceGroup {
                EtaArrowPreference(
                    title = stringResource(R.string.cli_account_add),
                    summary = stringResource(R.string.cli_account_add_summary, selectedAgent.displayName),
                    startAction = {
                        EtaPreferenceIcon(Icons.Rounded.Add, tint = EtaPreferenceColors.Blue)
                    },
                    onClick = { showAdd = true },
                )
            }
        }

        item(key = "hints") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(top = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.cli_account_official_hint),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.cli_account_security_hint),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }

    if (showAdd) {
        AddCliAccountDialog(
            agent = selectedAgent,
            onDismiss = { showAdd = false },
            onSave = { account ->
                val next = runCatching { store.upsert(account) }.getOrNull()
                if (next == null) {
                    toast(saveFailedText)
                } else {
                    state = next
                    showAdd = false
                    toast(appContext.getString(R.string.cli_account_saved))
                }
            },
            manager = manager,
            toast = toast,
            unexpectedText = unexpectedText,
        )
    }

    settingsTarget?.let { account ->
        CliAccountSettingsDialog(
            account = account,
            manager = manager,
            onDismiss = { settingsTarget = null },
            onSave = { updated ->
                val next = runCatching { store.upsert(updated) }.getOrNull()
                if (next == null) {
                    toast(saveFailedText)
                } else {
                    state = next
                    settingsTarget = null
                    if (next.activeByAgent[updated.agentId] == updated.id) {
                        switchTo(updated)
                    } else {
                        toast(appContext.getString(R.string.cli_account_saved))
                    }
                }
            },
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
                    val next = runCatching { store.delete(account.id) }.getOrNull()
                    if (next == null) toast(saveFailedText) else state = next
                    deleteTarget = null
                },
                destructive = true,
            )
        }
    }
}

@Composable
private fun AccountPreference(
    account: CliAccount,
    active: Boolean,
    busy: Boolean,
    onSwitch: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit,
) {
    val icon = when (account.kind) {
        CliAccountKind.OFFICIAL -> Icons.Rounded.AccountCircle
        CliAccountKind.API_KEY -> Icons.Rounded.Key
        CliAccountKind.THIRD_PARTY -> Icons.Rounded.Cloud
    }
    val detail = buildString {
        append(account.kind.label())
        val extra = listOf(account.baseUrl, account.model)
            .filter(String::isNotBlank)
            .joinToString(" · ")
        if (extra.isNotBlank()) append(" · ").append(extra)
    }
    // 操作按钮放在 EtaPreference 的 clickable 容器之外：旧账号也能稳定打开设置/删除，
    // 不再依赖嵌套 clickable 的事件分发。
    Column(Modifier.fillMaxWidth()) {
        EtaPreference(
            title = account.name,
            summary = detail,
            startAction = { EtaPreferenceIcon(icon, tint = EtaPreferenceColors.Blue) },
            endActions = {
                if (active) {
                    Text(
                        text = stringResource(R.string.cli_account_active),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            },
            onClick = if (!busy) onSettings else null,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = EtaPreferenceDefaults.SidePadding,
                    end = EtaPreferenceDefaults.SidePadding,
                    bottom = 10.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EtaTextButton(
                text = stringResource(R.string.cli_account_switch),
                onClick = onSwitch,
                enabled = !busy && !active,
                modifier = Modifier.weight(1f),
            )
            EtaTextButton(
                text = stringResource(R.string.cli_account_model_settings),
                onClick = onSettings,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
            EtaTextButton(
                text = stringResource(R.string.cli_account_delete),
                onClick = onDelete,
                enabled = !busy,
                modifier = Modifier.weight(1f),
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
    unexpectedText: String,
) {
    var kind by remember { mutableStateOf(CliAccountKind.API_KEY) }
    var name by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val captureFailedText = stringResource(R.string.cli_account_capture_failed)
    val installHintText = stringResource(R.string.cli_account_install_hint)
    val defaultOfficialName = stringResource(R.string.cli_account_official_default, agent.displayName)
    val invalidAuthText = stringResource(R.string.cli_error_invalid_auth)
    val captureErrors = mapOf(
        R.string.cli_error_invalid_auth to invalidAuthText,
        R.string.cli_error_unexpected to unexpectedText,
    )

    EtaWindowDialog(
        show = true,
        title = stringResource(R.string.cli_account_add),
        summary = agent.displayName,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .imePaddingSafe()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CliAccountKind.entries.forEach { option ->
                    EtaChoiceChip(
                        text = option.shortLabel(),
                        selected = kind == option,
                        onClick = { kind = option },
                        enabled = !importing,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
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
                                try {
                                    when (val captured = manager.capture(agent)) {
                                        CliCaptureResult.NotReady -> toast(installHintText)
                                        CliCaptureResult.NoLogin -> toast(captureFailedText)
                                        is CliCaptureResult.Failed ->
                                            toast(captureErrors[captured.messageRes] ?: unexpectedText)
                                        is CliCaptureResult.Success -> onSave(
                                            CliAccount(
                                                id = UUID.randomUUID().toString(),
                                                agentId = agent.id,
                                                name = name.ifBlank { defaultOfficialName },
                                                kind = CliAccountKind.OFFICIAL,
                                                authJson = captured.files.authJson,
                                                credentialsJson = captured.files.credentialsJson,
                                            ),
                                        )
                                    }
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (_: Throwable) {
                                    toast(unexpectedText)
                                } finally {
                                    importing = false
                                }
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
                confirmEnabled = !importing && when (kind) {
                    CliAccountKind.OFFICIAL -> true
                    CliAccountKind.API_KEY -> apiKey.isNotBlank()
                    CliAccountKind.THIRD_PARTY -> apiKey.isNotBlank() && baseUrl.isNotBlank()
                },
            )
        }
    }
}

@Composable
private fun CliAccountKind.shortLabel(): String = stringResource(
    when (this) {
        CliAccountKind.OFFICIAL -> R.string.cli_kind_official_short
        CliAccountKind.API_KEY -> R.string.cli_kind_api_key_short
        CliAccountKind.THIRD_PARTY -> R.string.cli_kind_third_party_short
    },
)

@Composable
private fun CliAccountKind.label(): String = stringResource(
    when (this) {
        CliAccountKind.OFFICIAL -> R.string.cli_kind_official
        CliAccountKind.API_KEY -> R.string.cli_kind_api_key
        CliAccountKind.THIRD_PARTY -> R.string.cli_kind_third_party
    },
)
