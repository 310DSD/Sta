@file:OptIn(ExperimentalFoundationApi::class)

package io.github.mangi.eta.ui.screens.terminal

import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.terminal.LinuxDistribution
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxFileExplorer
import io.github.mangi.eta.agent.terminal.ShellProcessSupervisor
import io.github.mangi.eta.ui.components.EtaCard
import io.github.mangi.eta.ui.components.EtaMonospace
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaTextButton
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.MiuixBackButton
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.layout.horizontalCutoutPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class FileSort { NAME, SIZE, DATE }
private enum class NameDialogKind { FOLDER, FILE, RENAME }

private class FilePaneState(initialPath: String) {
    var path by mutableStateOf(initialPath)
    var entries by mutableStateOf<List<LinuxFileExplorer.Entry>?>(null)
    var error by mutableStateOf<Int?>(null)
    var selected by mutableStateOf<Set<String>>(emptySet())
    var history by mutableStateOf(listOf(initialPath))
    var historyIndex by mutableStateOf(0)
    var refreshKey by mutableStateOf(0)
}

private data class NameDialogState(val kind: NameDialogKind, val initial: String = "")

private data class ContextTarget(
    val paneIndex: Int,
    val entry: LinuxFileExplorer.Entry,
)

/**
 * MT 管理器风格的双窗口 Linux 文件管理器。
 *
 * 左右窗口各自维护路径、历史、选择与列表；复制/移动直接从当前窗口到另一窗口，
 * 没有单独的粘贴步骤。文件操作优先走 root shell / PRoot，shell 不可用时回退到
 * App 进程直接访问 rootfs。
 */
@Composable
internal fun LinuxFilesScreen(
    context: Context,
    distribution: String,
    onBack: () -> Unit,
) {
    val appContext = context.applicationContext
    val linuxDistribution = remember(distribution) {
        LinuxDistribution.entries.firstOrNull { it.wireName == distribution }
    }
    val rootfsDir = remember(linuxDistribution) {
        linuxDistribution?.let { LinuxEnvironmentPaths.rootfsDir(appContext, it) }
    }
    val installed = rootfsDir != null && LinuxEnvironmentPaths.rootfsReady(rootfsDir.absolutePath)
    val shellSupervisor = remember { ShellProcessSupervisor() }
    DisposableEffect(Unit) { onDispose { shellSupervisor.beginClosing() } }

    val panes = remember { listOf(FilePaneState("/"), FilePaneState("/workspace")) }
    var activePane by remember { mutableStateOf(1) }
    var showHidden by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(FileSort.NAME) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var pathDialogPane by remember { mutableStateOf<Int?>(null) }
    var pathInput by remember { mutableStateOf("") }
    var nameDialog by remember { mutableStateOf<NameDialogState?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var contextTarget by remember { mutableStateOf<ContextTarget?>(null) }
    var deleteTargets by remember { mutableStateOf<List<String>?>(null) }
    var propertiesTarget by remember { mutableStateOf<String?>(null) }
    var properties by remember { mutableStateOf<LinuxFileExplorer.Metadata?>(null) }

    var viewerPath by remember { mutableStateOf<String?>(null) }
    var fileResult by remember { mutableStateOf<LinuxFileExplorer.ReadResult?>(null) }
    var editing by remember { mutableStateOf(false) }
    var editorText by remember { mutableStateOf("") }
    var editorDirty by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val toast = remember(appContext) { { message: String ->
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    } }

    suspend fun loadPane(pane: FilePaneState) {
        if (!installed || rootfsDir == null) return
        pane.entries = null
        pane.error = null
        val result = withContext(Dispatchers.IO) {
            LinuxFileExplorer.list(shellSupervisor, rootfsDir, pane.path)
        }
        when (result) {
            is LinuxFileExplorer.ListResult.Success -> pane.entries = result.entries
            LinuxFileExplorer.ListResult.NotDirectory -> pane.error = R.string.linux_files_error_not_directory
            LinuxFileExplorer.ListResult.Unreadable,
            LinuxFileExplorer.ListResult.CommandFailed,
            LinuxFileExplorer.ListResult.NotInstalled -> pane.error = R.string.linux_files_error_unreadable
        }
    }

    LaunchedEffect(installed, panes[0].path, panes[0].refreshKey) { loadPane(panes[0]) }
    LaunchedEffect(installed, panes[1].path, panes[1].refreshKey) { loadPane(panes[1]) }

    fun activate(index: Int) {
        if (activePane != index) panes[activePane].selected = emptySet()
        activePane = index
    }

    fun navigate(index: Int, path: String, recordHistory: Boolean = true, activatePane: Boolean = true) {
        val pane = panes[index]
        val normalized = normalizeDirectory(path)
        if (activatePane) activate(index)
        if (normalized == pane.path) {
            pane.refreshKey += 1
            return
        }
        if (recordHistory) {
            val history = pane.history.take(pane.historyIndex + 1) + normalized
            pane.history = history
            pane.historyIndex = history.lastIndex
        }
        pane.path = normalized
        pane.selected = emptySet()
    }

    fun goBack(index: Int) {
        val pane = panes[index]
        if (pane.historyIndex <= 0) return
        pane.historyIndex -= 1
        pane.path = pane.history[pane.historyIndex]
        pane.selected = emptySet()
    }

    fun goForward(index: Int) {
        val pane = panes[index]
        if (pane.historyIndex >= pane.history.lastIndex) return
        pane.historyIndex += 1
        pane.path = pane.history[pane.historyIndex]
        pane.selected = emptySet()
    }

    fun visibleEntries(index: Int): List<LinuxFileExplorer.Entry> =
        panes[index].entries.orEmpty()
            .filter { showHidden || !it.name.startsWith(".") }
            .filter {
                index != activePane || searchQuery.isBlank() ||
                    it.name.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith(
                compareBy<LinuxFileExplorer.Entry> { !it.isDir }
                    .thenBy {
                        when (sort) {
                            FileSort.NAME -> it.name.lowercase(Locale.getDefault())
                            FileSort.SIZE -> it.sizeBytes.toString().padStart(20, '0')
                            FileSort.DATE -> it.mtimeEpochSeconds.toString().padStart(20, '0')
                        }
                    },
            )

    fun refreshPane(index: Int) { panes[index].refreshKey += 1 }

    fun mutate(
        operation: (ShellProcessSupervisor, java.io.File) -> LinuxFileExplorer.MutateResult,
        onSuccess: () -> Unit,
    ) {
        val dir = rootfsDir ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) { operation(shellSupervisor, dir) }
            when (result) {
                LinuxFileExplorer.MutateResult.Success -> onSuccess()
                LinuxFileExplorer.MutateResult.NotInstalled ->
                    toast(context.getString(R.string.linux_files_not_installed))
                LinuxFileExplorer.MutateResult.InvalidPath ->
                    toast(context.getString(R.string.file_manager_error_invalid_name))
                LinuxFileExplorer.MutateResult.NotFound ->
                    toast(context.getString(R.string.file_manager_error_not_found))
                LinuxFileExplorer.MutateResult.AlreadyExists ->
                    toast(context.getString(R.string.file_manager_error_exists))
                LinuxFileExplorer.MutateResult.Unreadable ->
                    toast(context.getString(R.string.file_manager_error_permission))
                LinuxFileExplorer.MutateResult.CommandFailed ->
                    toast(context.getString(R.string.file_manager_error_operation))
            }
        }
    }

    LaunchedEffect(viewerPath) {
        val path = viewerPath ?: return@LaunchedEffect
        val dir = rootfsDir ?: return@LaunchedEffect
        fileResult = null
        editing = false
        editorDirty = false
        fileResult = withContext(Dispatchers.IO) { LinuxFileExplorer.readText(shellSupervisor, dir, path) }
    }

    LaunchedEffect(propertiesTarget) {
        val path = propertiesTarget ?: return@LaunchedEffect
        val dir = rootfsDir ?: return@LaunchedEffect
        properties = null
        properties = withContext(Dispatchers.IO) {
            when (val result = LinuxFileExplorer.stat(shellSupervisor, dir, path)) {
                is LinuxFileExplorer.StatResult.Success -> result.metadata
                else -> null
            }
        }
    }

    fun closeViewer() {
        viewerPath = null
        fileResult = null
        editing = false
        editorDirty = false
    }

    val active = panes[activePane]
    val other = panes[1 - activePane]
    val selectionMode = active.selected.isNotEmpty()
    val paneEntries = listOf(visibleEntries(0), visibleEntries(1))

    val viewerBack = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = viewerBack,
        isBackEnabled = viewerPath != null || selectionMode || searchOpen,
        onBackCompleted = {
            when {
                viewerPath != null -> closeViewer()
                selectionMode -> active.selected = emptySet()
                else -> searchOpen = false
            }
        },
    )

    val activeEntries = paneEntries[activePane]
    val folderCount = activeEntries.count { it.isDir }
    val fileCount = activeEntries.size - folderCount
    val headerTitle = when {
        viewerPath != null -> viewerPath.orEmpty().substringAfterLast('/').ifBlank { "/" }
        selectionMode -> stringResource(R.string.file_manager_selected, active.selected.size)
        else -> active.path
    }
    val headerSubtitle = if (viewerPath == null && !selectionMode) {
        stringResource(R.string.file_manager_stats, folderCount, fileCount)
    } else {
        null
    }

    Scaffold(
        topBar = {
            FileTopBar(
                title = headerTitle,
                subtitle = headerSubtitle,
                onBack = {
                    when {
                        viewerPath != null -> closeViewer()
                        selectionMode -> active.selected = emptySet()
                        searchOpen -> searchOpen = false
                        else -> onBack()
                    }
                },
                onTitleClick = {
                    if (viewerPath == null) {
                        pathDialogPane = activePane
                        pathInput = active.path
                    }
                },
            ) {
                if (viewerPath == null) {
                    IconButton(onClick = { searchOpen = !searchOpen }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.file_manager_search),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    StorageMenu(onPick = { path -> navigate(activePane, path) })
                    HeaderMoreMenu(
                        showHidden = showHidden,
                        onToggleHidden = { showHidden = !showHidden },
                        onSort = { sort = it },
                        onSelectAll = { active.selected = paneEntries[activePane].map { it.name }.toSet() },
                        onRefresh = {
                            refreshPane(0)
                            refreshPane(1)
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalCutoutPadding()
                .padding(top = padding.calculateTopPadding()),
        ) {
            when {
                linuxDistribution == null -> {
                    StateMessage(stringResource(R.string.linux_files_invalid_distribution), Icons.Rounded.ErrorOutline)
                }
                !installed -> {
                    StateMessage(stringResource(R.string.linux_files_not_installed), Icons.Rounded.Terminal)
                }
                viewerPath != null -> {
                    ViewerPane(
                        context = appContext,
                        path = viewerPath.orEmpty(),
                        result = fileResult,
                        editing = editing,
                        editorText = editorText,
                        editorDirty = editorDirty,
                        saving = saving,
                        onEdit = {
                            val text = (fileResult as? LinuxFileExplorer.ReadResult.Text)?.content.orEmpty()
                            editorText = text
                            editing = true
                        },
                        onTextChange = {
                            editorText = it
                            editorDirty = true
                        },
                        onSave = {
                            val path = viewerPath ?: return@ViewerPane
                            val dir = rootfsDir ?: return@ViewerPane
                            saving = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    LinuxFileExplorer.writeText(shellSupervisor, dir, path, editorText)
                                }
                                saving = false
                                if (result == LinuxFileExplorer.WriteResult.Success) {
                                    editorDirty = false
                                    editing = false
                                    fileResult = LinuxFileExplorer.ReadResult.Text(editorText, truncated = false)
                                    refreshPane(activePane)
                                    toast(context.getString(R.string.file_manager_saved))
                                } else {
                                    toast(context.getString(R.string.file_manager_error_operation))
                                }
                            }
                        },
                        onClose = ::closeViewer,
                    )
                }
                else -> {
                    if (searchOpen) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = stringResource(R.string.file_manager_search_hint),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        PaneView(
                            pane = panes[0],
                            entries = paneEntries[0],
                            active = activePane == 0,
                            selectionMode = activePane == 0 && selectionMode,
                            modifier = Modifier.weight(1f),
                            onActivate = { activate(0) },
                            onUp = { navigate(0, parentDirectory(panes[0].path)) },
                            onOpen = { entry ->
                                activate(0)
                                if (entry.isDir) {
                                    navigate(0, joinPath(panes[0].path, entry.name))
                                } else {
                                    viewerPath = joinPath(panes[0].path, entry.name)
                                }
                            },
                            onSelect = { name ->
                                activate(0)
                                panes[0].selected = panes[0].selected.toggle(name)
                            },
                            onLongPress = { entry ->
                                activate(0)
                                contextTarget = ContextTarget(0, entry)
                            },
                        )
                        Box(
                            modifier = Modifier
                                .width(0.5.dp)
                                .fillMaxHeight()
                                .background(MiuixTheme.colorScheme.onBackground.copy(alpha = 0.10f)),
                        )
                        PaneView(
                            pane = panes[1],
                            entries = paneEntries[1],
                            active = activePane == 1,
                            selectionMode = activePane == 1 && selectionMode,
                            modifier = Modifier.weight(1f),
                            onActivate = { activate(1) },
                            onUp = { navigate(1, parentDirectory(panes[1].path)) },
                            onOpen = { entry ->
                                activate(1)
                                if (entry.isDir) {
                                    navigate(1, joinPath(panes[1].path, entry.name))
                                } else {
                                    viewerPath = joinPath(panes[1].path, entry.name)
                                }
                            },
                            onSelect = { name ->
                                activate(1)
                                panes[1].selected = panes[1].selected.toggle(name)
                            },
                            onLongPress = { entry ->
                                activate(1)
                                contextTarget = ContextTarget(1, entry)
                            },
                        )
                    }
                    if (selectionMode) {
                        SelectionBar(
                            canRename = active.selected.size == 1,
                            onCopy = {
                                mutate(
                                    operation = { supervisor, dir ->
                                        LinuxFileExplorer.copyInto(
                                            supervisor,
                                            dir,
                                            active.selected.map { joinPath(active.path, it) },
                                            other.path,
                                        )
                                    },
                                    onSuccess = {
                                        active.selected = emptySet()
                                        refreshPane(activePane)
                                        refreshPane(1 - activePane)
                                    },
                                )
                            },
                            onMove = {
                                mutate(
                                    operation = { supervisor, dir ->
                                        LinuxFileExplorer.moveInto(
                                            supervisor,
                                            dir,
                                            active.selected.map { joinPath(active.path, it) },
                                            other.path,
                                        )
                                    },
                                    onSuccess = {
                                        active.selected = emptySet()
                                        refreshPane(activePane)
                                        refreshPane(1 - activePane)
                                    },
                                )
                            },
                            onRename = {
                                val name = active.selected.firstOrNull() ?: return@SelectionBar
                                nameDialog = NameDialogState(NameDialogKind.RENAME, name)
                                nameInput = name
                            },
                            onProperties = {
                                val name = active.selected.firstOrNull() ?: return@SelectionBar
                                propertiesTarget = joinPath(active.path, name)
                            },
                            onDelete = { deleteTargets = active.selected.toList() },
                        )
                    } else {
                        BottomToolbar(
                            canBack = active.historyIndex > 0,
                            canForward = active.historyIndex < active.history.lastIndex,
                            canUp = active.path != "/",
                            onBack = { goBack(activePane) },
                            onForward = { goForward(activePane) },
                            onUp = { navigate(activePane, parentDirectory(active.path)) },
                            onSync = { navigate(1 - activePane, active.path, activatePane = false) },
                            onNewFolder = {
                                nameDialog = NameDialogState(NameDialogKind.FOLDER)
                                nameInput = ""
                            },
                            onNewFile = {
                                nameDialog = NameDialogState(NameDialogKind.FILE)
                                nameInput = ""
                            },
                        )
                    }
                }
            }
        }
    }

    NameDialog(
        state = nameDialog,
        input = nameInput,
        onInputChange = { nameInput = it },
        onDismiss = { nameDialog = null },
        onConfirm = {
            val dialog = nameDialog ?: return@NameDialog
            val value = nameInput.trim()
            when (dialog.kind) {
                NameDialogKind.FOLDER -> mutate(
                    operation = { supervisor, dir ->
                        LinuxFileExplorer.createDirectory(supervisor, dir, active.path, value)
                    },
                    onSuccess = {
                        nameDialog = null
                        refreshPane(activePane)
                    },
                )
                NameDialogKind.FILE -> mutate(
                    operation = { supervisor, dir ->
                        LinuxFileExplorer.createFile(supervisor, dir, active.path, value)
                    },
                    onSuccess = {
                        nameDialog = null
                        refreshPane(activePane)
                    },
                )
                NameDialogKind.RENAME -> {
                    val oldName = dialog.initial
                    mutate(
                        operation = { supervisor, dir ->
                            LinuxFileExplorer.rename(supervisor, dir, joinPath(active.path, oldName), value)
                        },
                        onSuccess = {
                            nameDialog = null
                            active.selected = emptySet()
                            refreshPane(activePane)
                        },
                    )
                }
            }
        },
    )

    deleteTargets?.let { targets ->
        EtaWindowDialog(
            show = true,
            title = stringResource(R.string.file_manager_delete),
            summary = stringResource(R.string.file_manager_delete_summary, targets.size),
            onDismissRequest = { deleteTargets = null },
        ) {
            MiuixDialogActions(
                confirmText = stringResource(R.string.file_manager_delete),
                onCancel = { deleteTargets = null },
                onConfirm = {
                    mutate(
                        operation = { supervisor, dir ->
                            LinuxFileExplorer.delete(supervisor, dir, targets.map { joinPath(active.path, it) })
                        },
                        onSuccess = {
                            deleteTargets = null
                            active.selected = emptySet()
                            refreshPane(activePane)
                        },
                    )
                },
                destructive = true,
            )
        }
    }

    pathDialogPane?.let { index ->
        EtaWindowDialog(
            show = true,
            title = stringResource(R.string.file_manager_path),
            onDismissRequest = { pathDialogPane = null },
        ) {
            Column {
                TextField(
                    value = pathInput,
                    onValueChange = { pathInput = it },
                    label = stringResource(R.string.file_manager_path_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                MiuixDialogActions(
                    confirmText = stringResource(R.string.file_manager_confirm),
                    onCancel = { pathDialogPane = null },
                    onConfirm = {
                        pathDialogPane = null
                        if (pathInput.trim().startsWith("/")) navigate(index, pathInput)
                    },
                )
            }
        }
    }

    propertiesTarget?.let { target ->
        val metadata = properties
        EtaWindowDialog(
            show = true,
            title = stringResource(R.string.file_manager_properties),
            onDismissRequest = { propertiesTarget = null },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (metadata == null) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        InfiniteProgressIndicator(size = 22.dp)
                    }
                } else {
                    PropertyRow(stringResource(R.string.file_manager_name), metadata.name)
                    PropertyRow(stringResource(R.string.file_manager_path), target)
                    PropertyRow(
                        stringResource(R.string.file_manager_type),
                        if (metadata.permissions.startsWith("d")) {
                            stringResource(R.string.file_manager_type_folder)
                        } else {
                            stringResource(R.string.file_manager_type_file)
                        },
                    )
                    PropertyRow(stringResource(R.string.file_manager_size), Formatter.formatShortFileSize(appContext, metadata.sizeBytes))
                    PropertyRow(stringResource(R.string.file_manager_modified), formatTime(metadata.mtimeEpochSeconds))
                    PropertyRow(stringResource(R.string.file_manager_permissions), metadata.permissions)
                    PropertyRow(stringResource(R.string.file_manager_owner), "${metadata.owner} / ${metadata.group}")
                }
            }
        }
    }

    contextTarget?.let { target ->
        val pane = panes[target.paneIndex]
        val targetPath = joinPath(pane.path, target.entry.name)
        val otherPath = panes[1 - target.paneIndex].path
        FileContextMenu(
            name = target.entry.name,
            onDismiss = { contextTarget = null },
            onOpen = {
                contextTarget = null
                activate(target.paneIndex)
                if (target.entry.isDir) {
                    navigate(target.paneIndex, targetPath)
                } else {
                    viewerPath = targetPath
                }
            },
            onSelect = {
                contextTarget = null
                activate(target.paneIndex)
                panes[target.paneIndex].selected = setOf(target.entry.name)
            },
            onCopy = {
                contextTarget = null
                mutate(
                    operation = { supervisor, dir ->
                        LinuxFileExplorer.copyInto(supervisor, dir, listOf(targetPath), otherPath)
                    },
                    onSuccess = {
                        refreshPane(target.paneIndex)
                        refreshPane(1 - target.paneIndex)
                    },
                )
            },
            onMove = {
                contextTarget = null
                mutate(
                    operation = { supervisor, dir ->
                        LinuxFileExplorer.moveInto(supervisor, dir, listOf(targetPath), otherPath)
                    },
                    onSuccess = {
                        refreshPane(target.paneIndex)
                        refreshPane(1 - target.paneIndex)
                    },
                )
            },
            onDelete = {
                contextTarget = null
                activate(target.paneIndex)
                deleteTargets = listOf(target.entry.name)
            },
            onRename = {
                contextTarget = null
                activate(target.paneIndex)
                nameDialog = NameDialogState(NameDialogKind.RENAME, target.entry.name)
                nameInput = target.entry.name
            },
            onProperties = {
                contextTarget = null
                activate(target.paneIndex)
                propertiesTarget = targetPath
            },
        )
    }
}

@Composable
private fun FileContextMenu(
    name: String,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .shadow(24.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                    }
                }
                Text(
                    text = stringResource(R.string.file_manager_context_hint),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                HorizontalDivider(
                    thickness = 0.33.dp,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ContextAction(Icons.Rounded.FolderOpen, stringResource(R.string.file_manager_open), onOpen, Modifier.weight(1f))
                    ContextAction(Icons.Rounded.Check, stringResource(R.string.file_manager_select), onSelect, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ContextAction(Icons.Rounded.ContentCopy, stringResource(R.string.file_manager_copy), onCopy, Modifier.weight(1f))
                    ContextAction(Icons.Rounded.DriveFileMove, stringResource(R.string.file_manager_move), onMove, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ContextAction(Icons.Rounded.Delete, stringResource(R.string.file_manager_delete), onDelete, Modifier.weight(1f), danger = true)
                    ContextAction(Icons.Rounded.Edit, stringResource(R.string.file_manager_rename), onRename, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ContextAction(Icons.Rounded.Info, stringResource(R.string.file_manager_properties), onProperties, Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ContextAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val color = if (danger) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(21.dp), tint = color)
        Text(text = label, style = MiuixTheme.textStyles.footnote1, color = color, maxLines = 1)
    }
}

@Composable
private fun FileTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onTitleClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiuixBackButton(onClick = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onTitleClick)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.footnote1.copy(fontFamily = EtaMonospace, fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
        actions()
    }
}

@Composable
private fun HeaderMoreMenu(
    showHidden: Boolean,
    onToggleHidden: () -> Unit,
    onSort: (FileSort) -> Unit,
    onSelectAll: () -> Unit,
    onRefresh: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { show = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.MoreVert, stringResource(R.string.file_manager_more), Modifier.size(20.dp))
        }
        WindowListPopup(
            show = show,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { show = false },
        ) {
            val items = listOf(
                stringResource(R.string.file_manager_sort_name) to Icons.Rounded.Sort,
                stringResource(R.string.file_manager_sort_size) to Icons.Rounded.Sort,
                stringResource(R.string.file_manager_sort_date) to Icons.Rounded.Sort,
                (if (showHidden) R.string.file_manager_hide_hidden else R.string.file_manager_show_hidden).let { stringResource(it) } to
                    (if (showHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility),
                stringResource(R.string.file_manager_select_all) to Icons.Rounded.SelectAll,
                stringResource(R.string.file_manager_refresh) to Icons.Rounded.Refresh,
            )
            ListPopupColumn {
                items.forEachIndexed { index, (text, icon) ->
                    DropdownImpl(
                        item = DropdownItem(
                            text = text,
                            icon = { iconModifier -> Icon(icon, null, iconModifier.size(20.dp)) },
                        ),
                        optionSize = items.size,
                        isSelected = false,
                        index = index,
                        onSelectedIndexChange = {
                            show = false
                            when (index) {
                                0 -> onSort(FileSort.NAME)
                                1 -> onSort(FileSort.SIZE)
                                2 -> onSort(FileSort.DATE)
                                3 -> onToggleHidden()
                                4 -> onSelectAll()
                                5 -> onRefresh()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PaneView(
    pane: FilePaneState,
    entries: List<LinuxFileExplorer.Entry>,
    active: Boolean,
    selectionMode: Boolean,
    modifier: Modifier = Modifier,
    onActivate: () -> Unit,
    onUp: () -> Unit,
    onOpen: (LinuxFileExplorer.Entry) -> Unit,
    onSelect: (String) -> Unit,
    onLongPress: (LinuxFileExplorer.Entry) -> Unit,
) {
    val context = LocalContext.current
    val background = if (active) MiuixTheme.colorScheme.surface else MiuixTheme.colorScheme.surfaceContainer
    Column(
        modifier = modifier
            .fillMaxHeight()
            .zIndex(if (active) 1f else 0f)
            .shadow(elevation = if (active) 20.dp else 0.dp, clip = false)
            .background(background)
            .combinedClickable(onClick = onActivate, onLongClick = onActivate),
    ) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (pane.path != "/") {
                item(key = "..") {
                    ParentRow(onClick = onUp)
                }
            }
            when {
                pane.error != null -> item(key = "error") {
                    PaneMessage(
                        text = stringResource(pane.error ?: R.string.linux_files_error_unreadable),
                        icon = Icons.Rounded.ErrorOutline,
                    )
                }
                pane.entries == null -> item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        InfiniteProgressIndicator(size = 20.dp)
                    }
                }
                entries.isEmpty() -> item(key = "empty") {
                    PaneMessage(
                        text = stringResource(R.string.linux_files_empty),
                        icon = Icons.Rounded.FolderOpen,
                    )
                }
                else -> itemsIndexed(entries, key = { _, entry -> entry.name }) { _, entry ->
                    CompactFileRow(
                        context = context,
                        entry = entry,
                        selected = entry.name in pane.selected,
                        selectionMode = selectionMode,
                        active = active,
                        onClick = {
                            if (selectionMode) onSelect(entry.name) else onOpen(entry)
                        },
                        onLongClick = { onLongPress(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ParentRow(onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onClick)
                .padding(horizontal = 7.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "..",
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 14.sp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        HorizontalDivider(
            thickness = 0.33.dp,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.06f),
            modifier = Modifier.padding(start = 38.dp),
        )
    }
}

@Composable
private fun CompactFileRow(
    context: Context,
    entry: LinuxFileExplorer.Entry,
    selected: Boolean,
    selectionMode: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val background = if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 7.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    state = if (selected) ToggleableState.On else ToggleableState.Off,
                    onClick = onClick,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(3.dp))
            }
            Icon(
                imageVector = fileIcon(entry.name, entry.isDir),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = fileTint(entry.name, entry.isDir),
            )
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = compactSummary(context, entry),
                    style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.isDir) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        HorizontalDivider(
            thickness = 0.33.dp,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.06f),
            modifier = Modifier.padding(start = 32.dp),
        )
    }
}

@Composable
private fun PaneMessage(text: String, icon: ImageVector) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BottomToolbar(
    canBack: Boolean,
    canForward: Boolean,
    canUp: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onUp: () -> Unit,
    onSync: () -> Unit,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
) {
    BottomActionBar {
        ToolItem(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.file_manager_back), onBack, enabled = canBack, modifier = Modifier.weight(1f))
        ToolItem(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.file_manager_forward), onForward, enabled = canForward, modifier = Modifier.weight(1f))
        ToolbarAddMenu(onNewFolder = onNewFolder, onNewFile = onNewFile, modifier = Modifier.weight(1f))
        ToolItem(Icons.Rounded.Sync, stringResource(R.string.file_manager_sync), onSync, modifier = Modifier.weight(1f))
        ToolItem(Icons.Rounded.ArrowUpward, stringResource(R.string.file_manager_up), onUp, enabled = canUp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ToolbarAddMenu(
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var show by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        ToolItem(Icons.Rounded.Add, stringResource(R.string.file_manager_new), onClick = { show = true }, modifier = Modifier.fillMaxWidth())
        WindowListPopup(
            show = show,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { show = false },
        ) {
            val items = listOf(
                stringResource(R.string.file_manager_new_folder) to Icons.Rounded.CreateNewFolder,
                stringResource(R.string.file_manager_new_file) to Icons.Rounded.NoteAdd,
            )
            ListPopupColumn {
                items.forEachIndexed { index, (text, icon) ->
                    DropdownImpl(
                        item = DropdownItem(
                            text = text,
                            icon = { iconModifier -> Icon(icon, null, iconModifier.size(20.dp)) },
                        ),
                        optionSize = items.size,
                        isSelected = false,
                        index = index,
                        onSelectedIndexChange = {
                            show = false
                            when (index) {
                                0 -> onNewFolder()
                                1 -> onNewFile()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    canRename: Boolean,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit,
) {
    BottomActionBar {
        ToolItem(Icons.Rounded.ContentCopy, stringResource(R.string.file_manager_copy), onCopy, modifier = Modifier.weight(1f))
        ToolItem(Icons.Rounded.DriveFileMove, stringResource(R.string.file_manager_move), onMove, modifier = Modifier.weight(1f))
        ToolItem(Icons.Rounded.Edit, stringResource(R.string.file_manager_rename), onRename, enabled = canRename, modifier = Modifier.weight(1f))
        ToolItem(Icons.Rounded.Info, stringResource(R.string.file_manager_properties), onProperties, modifier = Modifier.weight(1f))
        ToolItem(Icons.Rounded.Delete, stringResource(R.string.file_manager_delete), onDelete, danger = true, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ToolItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val color = when {
        !enabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.35f)
        danger -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, Modifier.size(28.dp), tint = color)
    }
}

@Composable
private fun BottomActionBar(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(
            thickness = 0.33.dp,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.08f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun StorageMenu(onPick: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { show = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.FolderOpen, stringResource(R.string.file_manager_storage), Modifier.size(20.dp))
        }
        WindowListPopup(
            show = show,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { show = false },
        ) {
            val places = listOf(
                "/" to stringResource(R.string.file_manager_root),
                "/workspace" to stringResource(R.string.file_manager_workspace),
                "/root" to stringResource(R.string.file_manager_home),
                "/tmp" to stringResource(R.string.file_manager_tmp),
                "/workspace/mounts" to stringResource(R.string.file_manager_mounts),
            )
            ListPopupColumn {
                places.forEachIndexed { index, (path, label) ->
                    DropdownImpl(
                        item = DropdownItem(
                            text = label,
                            icon = { iconModifier -> Icon(Icons.Rounded.Folder, null, iconModifier.size(20.dp)) },
                        ),
                        optionSize = places.size,
                        isSelected = false,
                        index = index,
                        onSelectedIndexChange = {
                            show = false
                            onPick(path)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerPane(
    context: Context,
    path: String,
    result: LinuxFileExplorer.ReadResult?,
    editing: Boolean,
    editorText: String,
    editorDirty: Boolean,
    saving: Boolean,
    onEdit: () -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = path,
                style = MiuixTheme.textStyles.footnote2.copy(fontFamily = EtaMonospace),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            when {
                editing -> EtaTextButton(
                    text = stringResource(R.string.file_manager_save),
                    onClick = onSave,
                    enabled = editorDirty && !saving,
                )
                result is LinuxFileExplorer.ReadResult.Text -> EtaTextButton(
                    text = stringResource(R.string.file_manager_edit),
                    onClick = onEdit,
                )
            }
        }
        when {
            result == null -> Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                InfiniteProgressIndicator(size = 24.dp)
            }
            result is LinuxFileExplorer.ReadResult.Text -> {
                if (!editing && result.truncated) {
                    Text(
                        text = stringResource(R.string.linux_files_truncated_hint),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (editing) {
                    EtaCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        insideMargin = PaddingValues(14.dp),
                    ) {
                        BasicTextField(
                            value = editorText,
                            onValueChange = onTextChange,
                            textStyle = MiuixTheme.textStyles.footnote1.copy(
                                fontFamily = EtaMonospace,
                                color = MiuixTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    EtaCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        insideMargin = PaddingValues(14.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                text = result.content,
                                style = MiuixTheme.textStyles.footnote1.copy(fontFamily = EtaMonospace),
                            )
                        }
                    }
                }
            }
            result is LinuxFileExplorer.ReadResult.Binary -> StateMessage(
                stringResource(R.string.linux_files_binary_hint),
                Icons.AutoMirrored.Rounded.InsertDriveFile,
            )
            else -> StateMessage(
                stringResource(R.string.linux_files_error_unreadable),
                Icons.Rounded.ErrorOutline,
            )
        }
    }
}

@Composable
private fun NameDialog(
    state: NameDialogState?,
    input: String,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (state == null) return
    EtaWindowDialog(
        show = true,
        title = when (state.kind) {
            NameDialogKind.FOLDER -> stringResource(R.string.file_manager_new_folder)
            NameDialogKind.FILE -> stringResource(R.string.file_manager_new_file)
            NameDialogKind.RENAME -> stringResource(R.string.file_manager_rename)
        },
        onDismissRequest = onDismiss,
    ) {
        Column {
            TextField(
                value = input,
                onValueChange = onInputChange,
                label = stringResource(R.string.file_manager_name),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            MiuixDialogActions(
                confirmText = stringResource(R.string.file_manager_confirm),
                onCancel = onDismiss,
                onConfirm = onConfirm,
                confirmEnabled = input.trim().isNotEmpty(),
            )
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.footnote2,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StateMessage(message: String, icon: ImageVector) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(32.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

private fun normalizeDirectory(path: String): String {
    val trimmed = path.trim()
    if (trimmed.isEmpty() || !trimmed.startsWith("/")) return "/"
    val segments = mutableListOf<String>()
    trimmed.split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
            else -> segments += segment
        }
    }
    return "/" + segments.joinToString("/")
}

private fun parentDirectory(path: String): String {
    val normalized = normalizeDirectory(path)
    if (normalized == "/") return "/"
    return normalized.substringBeforeLast('/').ifBlank { "/" }
}

private fun joinPath(parent: String, name: String): String =
    if (parent == "/") "/$name" else "${parent.trimEnd('/')}/$name"

private fun compactSummary(context: Context, entry: LinuxFileExplorer.Entry): String {
    val time = formatTime(entry.mtimeEpochSeconds)
    return if (entry.isDir) time else "$time  ${Formatter.formatShortFileSize(context, entry.sizeBytes)}"
}

private fun formatTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return "-"
    val formatter = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(epochSeconds * 1000))
}

private fun fileIcon(name: String, isDir: Boolean): ImageVector {
    if (isDir) return Icons.Rounded.Folder
    val ext = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return when (ext) {
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico" -> Icons.Rounded.Image
        "mp4", "mkv", "webm", "avi", "mov", "3gp" -> Icons.Rounded.VideoFile
        "mp3", "flac", "wav", "ogg", "m4a", "aac" -> Icons.Rounded.AudioFile
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz" -> Icons.Rounded.Archive
        "apk", "xapk", "apks" -> Icons.Rounded.Android
        "kt", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "hpp", "rs", "go", "sh",
        "bash", "zsh", "json", "jsonc", "yaml", "yml", "toml", "xml", "html", "css", "sql" -> Icons.Rounded.Code
        "txt", "md", "log", "conf", "ini", "properties", "csv" -> Icons.Rounded.Description
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }
}

@Composable
private fun fileTint(name: String, isDir: Boolean): Color {
    if (isDir) return MiuixTheme.colorScheme.onSurface
    val ext = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return when (ext) {
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico" -> Color(0xFF16A34A)
        "mp4", "mkv", "webm", "avi", "mov", "3gp" -> Color(0xFFDB2777)
        "mp3", "flac", "wav", "ogg", "m4a", "aac" -> Color(0xFF9333EA)
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz" -> Color(0xFFF59E0B)
        "apk", "xapk", "apks" -> Color(0xFF16A34A)
        else -> MiuixTheme.colorScheme.onSurfaceVariantActions
    }
}
