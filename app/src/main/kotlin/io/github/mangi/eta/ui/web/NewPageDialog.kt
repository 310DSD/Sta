package io.github.mangi.eta.ui.web

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.extension.ExtensionAgent
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.MiuixDialogActions
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 内置 WebView 的新建页面弹窗：扩展 Agent 快速入口 + 文件管理器 + 自定义 loopback 地址。
 */
@Composable
internal fun NewPageDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onOpenAgent: (ExtensionAgent) -> Unit,
    onOpenFileManager: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var url by remember { mutableStateOf("http://127.0.0.1:") }
    LaunchedEffect(show) {
        if (show) url = "http://127.0.0.1:"
    }
    EtaWindowDialog(
        show = show,
        title = stringResource(R.string.webview_new_page_title),
        summary = stringResource(R.string.webview_new_page_summary),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            QuickEntry(
                icon = Icons.Rounded.Terminal,
                title = ExtensionAgent.OPENCODE.displayName,
                summary = stringResource(R.string.webview_agent_summary),
                onClick = { onOpenAgent(ExtensionAgent.OPENCODE) },
            )
            QuickEntry(
                icon = Icons.Rounded.AutoAwesome,
                title = ExtensionAgent.DSH.displayName,
                summary = stringResource(R.string.webview_agent_summary),
                onClick = { onOpenAgent(ExtensionAgent.DSH) },
            )
            QuickEntry(
                icon = Icons.Rounded.Code,
                title = ExtensionAgent.KIMI.displayName,
                summary = stringResource(R.string.webview_agent_summary),
                onClick = { onOpenAgent(ExtensionAgent.KIMI) },
            )
            QuickEntry(
                icon = Icons.Rounded.Folder,
                title = stringResource(R.string.webview_file_manager),
                summary = stringResource(R.string.webview_file_manager_summary),
                onClick = onOpenFileManager,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.webview_custom_url),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            TextField(
                value = url,
                onValueChange = { url = it },
                label = stringResource(R.string.webview_url_hint),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            MiuixDialogActions(
                confirmText = stringResource(R.string.webview_open),
                onCancel = onDismiss,
                onConfirm = { onOpenUrl(url.trim()) },
                confirmEnabled = url.isNotBlank(),
            )
        }
    }
}

@Composable
private fun QuickEntry(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MiuixTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
