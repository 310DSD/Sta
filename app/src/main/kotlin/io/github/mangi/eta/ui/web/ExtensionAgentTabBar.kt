package io.github.mangi.eta.ui.web

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

internal data class ExtensionAgentTabUi(
    val id: Long,
    val title: String,
)

@Composable
internal fun ExtensionAgentTabBar(
    tabs: List<ExtensionAgentTabUi>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onAdd: () -> Unit,
    onSyncProviders: () -> Unit,
    onReload: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.surface)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(tabs, key = { it.id }) { tab ->
                val selected = tab.id == selectedId
                Row(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onSelect(tab.id) }
                        .padding(start = 10.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = tab.title.ifBlank { stringResource(R.string.webview_new_page) },
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (selected) colors.primary else colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp),
                    )
                    IconButton(
                        onClick = { onClose(tab.id) },
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.webview_close_tab),
                            modifier = Modifier.size(15.dp),
                            tint = if (selected) colors.primary else colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
        TabBarAction(Icons.Rounded.Add, stringResource(R.string.webview_new_page), onAdd)
        TabBarMenu(
            onSyncProviders = onSyncProviders,
            onReload = onReload,
            onCloseTab = { selectedId?.let(onClose) },
        )
    }
}

@Composable
private fun TabBarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TabBarMenu(
    onSyncProviders: () -> Unit,
    onReload: () -> Unit,
    onCloseTab: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        TabBarAction(Icons.Rounded.MoreVert, stringResource(R.string.webview_page_menu)) { showMenu = true }
        WindowListPopup(
            show = showMenu,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { showMenu = false },
        ) {
            val items = listOf(
                stringResource(R.string.webview_sync_opencode) to Icons.Rounded.Sync,
                stringResource(R.string.webview_reload) to Icons.Rounded.Refresh,
                stringResource(R.string.webview_close_current_tab) to Icons.Rounded.Close,
            )
            ListPopupColumn {
                items.forEachIndexed { index, (text, icon) ->
                    DropdownImpl(
                        item = DropdownItem(
                            text = text,
                            icon = { modifier ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = modifier.size(20.dp),
                                )
                            },
                        ),
                        optionSize = items.size,
                        isSelected = false,
                        index = index,
                        onSelectedIndexChange = {
                            showMenu = false
                            when (index) {
                                0 -> onSyncProviders()
                                1 -> onReload()
                                2 -> onCloseTab()
                            }
                        },
                    )
                }
            }
        }
    }
}
