package io.github.mangi.eta.ui.screens.cli

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.cli.CliAccount
import io.github.mangi.eta.agent.cli.CliAccountKind
import io.github.mangi.eta.agent.cli.CliAgent
import io.github.mangi.eta.agent.cli.CliConfigManager
import io.github.mangi.eta.ui.components.EtaChoiceChip
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.EtaTextButton
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.insets.imePaddingSafe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 账号的模型/上下文/思考等级设置；模型目录可在线拉取，也可从 Eta 当前 provider 同步。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CliAccountSettingsDialog(
    account: CliAccount,
    manager: CliConfigManager,
    onDismiss: () -> Unit,
    onSave: (CliAccount) -> Unit,
    toast: (String) -> Unit,
) {
    var baseUrl by remember(account.id) { mutableStateOf(account.baseUrl) }
    var apiKey by remember(account.id) { mutableStateOf(account.apiKey) }
    var model by remember(account.id) { mutableStateOf(account.model) }
    var contextWindow by remember(account.id) { mutableStateOf(account.contextWindow?.toString().orEmpty()) }
    var maxOutput by remember(account.id) { mutableStateOf(account.maxOutputTokens?.toString().orEmpty()) }
    var effort by remember(account.id) { mutableStateOf(account.reasoningEffort) }
    var models by remember(account.id) { mutableStateOf(account.models) }
    var fetching by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fetchFailedText = stringResource(R.string.cli_error_fetch_models)
    val syncFailedText = stringResource(R.string.cli_error_sync_eta)
    val fetchingText = stringResource(R.string.cli_account_fetching)
    val syncingText = stringResource(R.string.cli_account_syncing)
    val official = account.kind == CliAccountKind.OFFICIAL
    val defaultEfforts = if (account.agentId == CliAgent.CODEX.id) {
        listOf("low", "medium", "high", "xhigh")
    } else {
        listOf("low", "medium", "high", "xhigh", "max")
    }

    fun currentAccount(): CliAccount = account.copy(
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        models = models,
        contextWindow = contextWindow.toIntOrNull()?.takeIf { it > 0 },
        maxOutputTokens = maxOutput.toIntOrNull()?.takeIf { it > 0 },
        reasoningEffort = effort,
    )

    fun fetchModels() {
        if (fetching || syncing) return
        fetching = true
        scope.launch {
            try {
                manager.fetchModels(currentAccount())
                    .onSuccess { fetched ->
                        models = fetched
                        if (model.isBlank() && fetched.isNotEmpty()) model = fetched.first().id
                    }
                    .onFailure { toast(fetchFailedText) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                toast(fetchFailedText)
            } finally {
                fetching = false
            }
        }
    }

    fun syncFromEta() {
        if (fetching || syncing) return
        syncing = true
        scope.launch {
            try {
                val synced = manager.syncFromEta(currentAccount())
                if (synced == null) {
                    toast(syncFailedText)
                } else {
                    baseUrl = synced.baseUrl
                    apiKey = synced.apiKey
                    model = synced.model
                    contextWindow = synced.contextWindow?.toString().orEmpty()
                    maxOutput = synced.maxOutputTokens?.toString().orEmpty()
                    effort = synced.reasoningEffort
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                toast(syncFailedText)
            } finally {
                syncing = false
            }
        }
    }

    val selectedModel = models.firstOrNull { it.id == model }
    val efforts = selectedModel?.reasoningEfforts?.ifEmpty { defaultEfforts } ?: defaultEfforts

    EtaWindowDialog(
        show = true,
        title = stringResource(R.string.cli_account_model_settings_title),
        summary = account.name,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .imePaddingSafe()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EtaTextButton(
                    text = if (fetching) fetchingText else stringResource(R.string.cli_account_fetch_models),
                    onClick = ::fetchModels,
                    enabled = !fetching && !syncing && !official && apiKey.isNotBlank() &&
                        (account.kind != CliAccountKind.THIRD_PARTY || baseUrl.isNotBlank()),
                    modifier = Modifier.weight(1f),
                )
                EtaTextButton(
                    text = if (syncing) syncingText else stringResource(R.string.cli_account_sync_eta),
                    onClick = ::syncFromEta,
                    enabled = !fetching && !syncing,
                    modifier = Modifier.weight(1f),
                )
            }
            if (official) {
                Text(
                    text = stringResource(R.string.cli_account_official_model_hint),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (!official) {
                TextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = stringResource(R.string.cli_account_base_url),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                Spacer(Modifier.height(6.dp))
            }
            TextField(
                value = model,
                onValueChange = { model = it },
                label = stringResource(R.string.cli_account_model),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (models.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.cli_account_models_available),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    models.forEach { info ->
                        EtaChoiceChip(
                            text = info.displayName,
                            selected = model == info.id,
                            onClick = {
                                model = info.id
                                contextWindow = info.contextWindow?.toString().orEmpty()
                                maxOutput = info.maxOutputTokens?.toString().orEmpty()
                                if (info.defaultReasoningEffort.isNotBlank()) {
                                    effort = info.defaultReasoningEffort
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            TextField(
                value = contextWindow,
                onValueChange = { contextWindow = it.filter(Char::isDigit) },
                label = stringResource(R.string.cli_account_context_window),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            TextField(
                value = maxOutput,
                onValueChange = { maxOutput = it.filter(Char::isDigit) },
                label = stringResource(R.string.cli_account_max_output),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.cli_account_reasoning),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                efforts.forEach { value ->
                    EtaChoiceChip(
                        text = value,
                        selected = effort == value,
                        onClick = { effort = value },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            MiuixDialogActions(
                confirmText = stringResource(R.string.action_save),
                onCancel = onDismiss,
                onConfirm = { onSave(currentAccount()) },
                confirmEnabled = !fetching && !syncing && when (account.kind) {
                    CliAccountKind.OFFICIAL -> true
                    CliAccountKind.API_KEY -> apiKey.isNotBlank()
                    CliAccountKind.THIRD_PARTY -> apiKey.isNotBlank() && baseUrl.isNotBlank()
                },
            )
        }
    }
}
