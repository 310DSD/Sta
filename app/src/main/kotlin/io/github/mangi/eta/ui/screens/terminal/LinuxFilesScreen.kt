@file:OptIn(ExperimentalFoundationApi::class)

package io.github.mangi.eta.ui.screens.terminal

import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Sort
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class FileSort { NAME, SIZE, DATE }
private enum class ClipboardMode { COPY, MOVE }
private enum class NameDialogKind { FOLDER, FILE, RENAME }

private data class FileClipboard(val paths: List<String>, val mode: ClipboardMode)
private data class NameDialogState(val kind: NameDialogKind, val initial: String = "")

/**
 * MT 风格的 Linux 文件管理器：浏览 rootfs、搜索当前目录、多选、复制/剪切/粘贴、
 * 重命名、删除、新建、属性与文本编辑。所有文件操作都经 root shell / PRoot，
 * 保留 Linux 的属主、符号链接与权限语义。
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

    var currentPath by remember { mutableStateOf("/workspace") }
    var entries by remember { mutableStateOf<List<LinuxFileExplorer.Entry>?>(null) }
    var listError by remember { mutableStateOf<Int?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var clipboard by remember { mutableStateOf<FileClipboard?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(FileSort.NAME) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var nameDialog by remember { mutableStateOf<NameDialogState?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var deleteTargets by remember { mutableStateOf<List<String>?>(null) }
    var propertiesTarget by remember { mutableStateOf<String?>(null) }
    var properties by remember { mutableStateOf<LinuxFileExplorer.Metadata?>(null) }
    var pathDialog by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf(currentPath) }

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

    fun refresh() { refreshKey += 1 }

    LaunchedEffect(currentPath, refreshKey, linuxDistribution, installed) {
        if (!installed || rootfsDir == null) return@LaunchedEffect
        entries = null
        listError = null
        val result = withContext(Dispatchers.IO) {
            LinuxFileExplorer.list(shellSupervisor, rootfsDir, currentPath)
        }
        when (result) {
            is LinuxFileExplorer.ListResult.Success -> entries = result.entries
            LinuxFileExplorer.ListResult.NotDirectory -> listError = R.string.linux_files_error_not_directory
            LinuxFileExplorer.ListResult.Unreadable,
            LinuxFileExplorer.ListResult.CommandFailed,
            LinuxFileExplorer.ListResult.NotInstalled -> listError = R.string.linux_files_error_unreadable
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

    fun navigate(path: String) {
        val normalized = normalizeDirectory(path)
        if (normalized != currentPath) {
            currentPath = normalized
            selected = emptySet()
            searchQuery = ""
            searchOpen = false
        }
        refresh()
    }

    fun mutate(
        operation: (ShellProcessSupervisor, java.io.File) -> LinuxFileExplorer.MutateResult,
        onSuccess: () -> Unit = {},
    ) {
        val dir = rootfsDir ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) { operation(shellSupervisor, dir) }
            when (result) {
                LinuxFileExplorer.MutateResult.Success -> {
                    onSuccess()
                    refresh()
                }
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

    val visibleEntries = remember(entries, showHidden, sort, searchQuery) {
        entries.orEmpty()
            .filter { showHidden || !it.name.startsWith(".") }
            .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
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
    }

    val selectionMode = selected.isNotEmpty()
    val parentPath = parentDirectory(currentPath)

    val viewerBack = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = viewerBack,
        isBackEnabled = viewerPath != null || selectionMode || searchOpen,
        onBackCompleted = {
            when {
                viewerPath != null -> closeViewer()
                selectionMode -> selected = emptySet()
                else -> searchOpen = false
            }
        },
    )

    val headerTitle = when {
        viewerPath != null -> viewerPath.orEmpty().substringAfterLast('/').ifBlank { "/" }
        selectionMode -> stringResource(R.string.file_manager_selected, selected.size)
        else -> currentPath.substringAfterLast('/').ifBlank { "/" }
    }
    val scrollBehavior = top.yukonga.miuix.kmp.basic.MiuixScrollBehavior()
    top.yukonga.miuix.kmp.basic.Scaffold(
        topBar = {
            top.yukonga.miuix.kmp.basic.SmallTopAppBar(
                title = headerTitle,
                color = MiuixTheme.colorScheme.surface,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixBackButton(
                        onClick = {
                            when {
                                viewerPath != null -> closeViewer()
                                selectionMode -> selected = emptySet()
                                searchOpen -> searchOpen = false
                                currentPath != "/" -> navigate(parentPath)
                                else -> onBack()
                            }
                        },
                    )
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = { selected = emptySet() }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.file_manager_clear_selection),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else if (viewerPath == null) {
                        IconButton(onClick = { searchOpen = !searchOpen }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.file_manager_search),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        AddMenu(
                            onNewFolder = {
                                nameDialog = NameDialogState(NameDialogKind.FOLDER)
                                nameInput = ""
                            },
                            onNewFile = {
                                nameDialog = NameDialogState(NameDialogKind.FILE)
                                nameInput = ""
                            },
                        )
                        MoreMenu(
                            sort = sort,
                            showHidden = showHidden,
                            onSort = { sort = it },
                            onToggleHidden = { showHidden = !showHidden },
                            onSelectAll = {
                                selected = visibleEntries.map { it.name }.toSet()
                            },
                            onRefresh = ::refresh,
                        )
                    }
                },
            )
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
                    PathBar(
                        path = currentPath,
                        onUp = { if (currentPath != "/") navigate(parentPath) },
                        onPathClick = {
                            pathInput = currentPath
                            pathDialog = true
                        },
                        onRefresh = ::refresh,
                    )
                    if (searchOpen) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = stringResource(R.string.file_manager_search_hint),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    StorageChips(
                        currentPath = currentPath,
                        onNavigate = ::navigate,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        val currentEntries = visibleEntries
                        if (currentPath != "/") {
                            item(key = "..") {
                                FileRow(
                                    context = appContext,
                                    name = "../",
                                    isDir = true,
                                    summary = stringResource(R.string.file_manager_parent),
                                    selected = false,
                                    selectionMode = false,
                                    onClick = { navigate(parentPath) },
                                    onLongClick = {},
                                )
                            }
                        }
                        when {
                            listError != null -> item(key = "error") {
                                StateMessage(
                                    stringResource(listError ?: R.string.linux_files_error_unreadable),
                                    Icons.Rounded.ErrorOutline,
                                )
                            }
                            entries == null -> item(key = "loading") {
                                Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                                    InfiniteProgressIndicator(size = 24.dp)
                                }
                            }
                            currentEntries.isEmpty() -> item(key = "empty") {
                                StateMessage(stringResource(R.string.linux_files_empty), Icons.Rounded.FolderOpen)
                            }
                            else -> itemsIndexed(currentEntries, key = { _, entry -> entry.name }) { index, entry ->
                                val isSelected = entry.name in selected
                                FileRow(
                                    context = appContext,
                                    name = entry.name,
                                    isDir = entry.isDir,
                                    summary = fileSummary(appContext, entry),
                                    selected = isSelected,
                                    selectionMode = selectionMode,
                                    onClick = {
                                        if (selectionMode) {
                                            selected = selected.toggle(entry.name)
                                        } else if (entry.isDir) {
                                            navigate(joinPath(currentPath, entry.name))
                                        } else {
                                            viewerPath = joinPath(currentPath, entry.name)
                                        }
                                    },
                                    onLongClick = {
                                        selected = selected + entry.name
                                    },
                                )
                            }
                        }
                    }
                    if (selectionMode) {
                        SelectionBar(
                            canRename = selected.size == 1,
                            onCopy = {
                                clipboard = FileClipboard(selected.map { joinPath(currentPath, it) }, ClipboardMode.COPY)
                                selected = emptySet()
                            },
                            onMove = {
                                clipboard = FileClipboard(selected.map { joinPath(currentPath, it) }, ClipboardMode.MOVE)
                                selected = emptySet()
                            },
                            onRename = {
                                val name = selected.firstOrNull() ?: return@SelectionBar
                                nameDialog = NameDialogState(NameDialogKind.RENAME, name)
                                nameInput = name
                            },
                            onDelete = { deleteTargets = selected.toList() },
                            onProperties = {
                                val name = selected.firstOrNull() ?: return@SelectionBar
                                propertiesTarget = joinPath(currentPath, name)
                            },
                        )
                    } else {
                        val clip = clipboard
                        if (clip != null) {
                            ClipboardBar(
                            count = clip.paths.size,
                            move = clip.mode == ClipboardMode.MOVE,
                            onPaste = {
                                mutate(
                                    operation = { supervisor, dir ->
                                        if (clip.mode == ClipboardMode.COPY) {
                                            LinuxFileExplorer.copyInto(supervisor, dir, clip.paths, currentPath)
                                        } else {
                                            LinuxFileExplorer.moveInto(supervisor, dir, clip.paths, currentPath)
                                        }
                                    },
                                    onSuccess = { clipboard = null },
                                )
                            },
                                onCancel = { clipboard = null },
                            )
                        } else {
                            QuickBar(
                            showHidden = showHidden,
                            onNewFolder = {
                                nameDialog = NameDialogState(NameDialogKind.FOLDER)
                                nameInput = ""
                            },
                            onNewFile = {
                                nameDialog = NameDialogState(NameDialogKind.FILE)
                                nameInput = ""
                            },
                            onToggleHidden = { showHidden = !showHidden },
                            onRefresh = ::refresh,
                        )
                    }
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
                    operation = { supervisor, dir -> LinuxFileExplorer.createDirectory(supervisor, dir, currentPath, value) },
                    onSuccess = { nameDialog = null },
                )
                NameDialogKind.FILE -> mutate(
                    operation = { supervisor, dir -> LinuxFileExplorer.createFile(supervisor, dir, currentPath, value) },
                    onSuccess = { nameDialog = null },
                )
                NameDialogKind.RENAME -> {
                    val oldName = dialog.initial
                    mutate(
                        operation = { supervisor, dir ->
                            LinuxFileExplorer.rename(supervisor, dir, joinPath(currentPath, oldName), value)
                        },
                        onSuccess = {
                            nameDialog = null
                            selected = emptySet()
                            if (viewerPath == joinPath(currentPath, oldName)) closeViewer()
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
                            LinuxFileExplorer.delete(supervisor, dir, targets.map { joinPath(currentPath, it) })
                        },
                        onSuccess = {
                            deleteTargets = null
                            selected = emptySet()
                        },
                    )
                },
                destructive = true,
            )
        }
    }

    if (pathDialog) {
        EtaWindowDialog(
            show = true,
            title = stringResource(R.string.file_manager_path),
            onDismissRequest = { pathDialog = false },
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
                    onCancel = { pathDialog = false },
                    onConfirm = {
                        pathDialog = false
                        if (pathInput.trim().startsWith("/")) navigate(pathInput)
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
}

@Composable
private fun PathBar(
    path: String,
    onUp: () -> Unit,
    onPathClick: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onUp, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.file_manager_parent), Modifier.size(18.dp))
        }
        Text(
            text = path,
            style = MiuixTheme.textStyles.footnote1.copy(fontFamily = EtaMonospace),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(onClick = onPathClick, onLongClick = onPathClick)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        )
        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Refresh, stringResource(R.string.file_manager_refresh), Modifier.size(18.dp))
        }
    }
}

@Composable
private fun StorageChips(currentPath: String, onNavigate: (String) -> Unit) {
    val places = listOf(
        "/" to stringResource(R.string.file_manager_root),
        "/workspace" to stringResource(R.string.file_manager_workspace),
        "/root" to stringResource(R.string.file_manager_home),
        "/tmp" to stringResource(R.string.file_manager_tmp),
        "/workspace/mounts" to stringResource(R.string.file_manager_mounts),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(places, key = { it.first }) { (path, label) ->
            val selected = currentPath == path
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MiuixTheme.colorScheme.surfaceContainer,
                    )
                    .combinedClickable(onClick = { onNavigate(path) }, onLongClick = { onNavigate(path) })
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (selected) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = MiuixTheme.colorScheme.primary)
                }
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.footnote2,
                    color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    context: Context,
    name: String,
    isDir: Boolean,
    summary: String?,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: (() -> Unit)? = null,
) {
    val background = if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent
    Column(Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(
                state = if (selected) ToggleableState.On else ToggleableState.Off,
                onClick = { onClick() },
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            imageVector = fileIcon(name, isDir),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = fileTint(name, isDir),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MiuixTheme.textStyles.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onMore != null) {
            IconButton(onClick = onMore, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Rounded.Info, stringResource(R.string.file_manager_properties), Modifier.size(16.dp))
            }
        } else if (isDir) {
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(17.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
    HorizontalDivider(
        thickness = 0.33.dp,
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.08f),
        modifier = Modifier.padding(start = 50.dp),
    )
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
private fun QuickBar(
    showHidden: Boolean,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    onToggleHidden: () -> Unit,
    onRefresh: () -> Unit,
) {
    BottomActionBar {
        ActionItem(Icons.Rounded.CreateNewFolder, stringResource(R.string.file_manager_new_folder), onNewFolder, modifier = Modifier.weight(1f))
        ActionItem(Icons.Rounded.NoteAdd, stringResource(R.string.file_manager_new_file), onNewFile, modifier = Modifier.weight(1f))
        ActionItem(
            if (showHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            if (showHidden) stringResource(R.string.file_manager_hide_hidden) else stringResource(R.string.file_manager_show_hidden),
            onToggleHidden,
            modifier = Modifier.weight(1f),
        )
        ActionItem(Icons.Rounded.Refresh, stringResource(R.string.file_manager_refresh), onRefresh, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SelectionBar(
    canRename: Boolean,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
) {
    BottomActionBar {
        ActionItem(Icons.Rounded.ContentCopy, stringResource(R.string.file_manager_copy), onCopy, modifier = Modifier.weight(1f))
        ActionItem(Icons.Rounded.DriveFileMove, stringResource(R.string.file_manager_move), onMove, modifier = Modifier.weight(1f))
        ActionItem(Icons.Rounded.Edit, stringResource(R.string.file_manager_rename), onRename, enabled = canRename, modifier = Modifier.weight(1f))
        ActionItem(Icons.Rounded.Info, stringResource(R.string.file_manager_properties), onProperties, modifier = Modifier.weight(1f))
        ActionItem(Icons.Rounded.Delete, stringResource(R.string.file_manager_delete), onDelete, danger = true, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ClipboardBar(count: Int, move: Boolean, onPaste: () -> Unit, onCancel: () -> Unit) {
    BottomActionBar {
        Text(
            text = stringResource(
                if (move) R.string.file_manager_clipboard_move else R.string.file_manager_clipboard_copy,
                count,
            ),
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.weight(1f),
        )
        EtaTextButton(text = stringResource(R.string.action_cancel), onClick = onCancel)
        EtaTextButton(text = stringResource(R.string.file_manager_paste), onClick = onPaste)
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
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = when {
                !enabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
                danger -> MiuixTheme.colorScheme.error
                else -> MiuixTheme.colorScheme.onSurface
            },
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = when {
                !enabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
                danger -> MiuixTheme.colorScheme.error
                else -> MiuixTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddMenu(onNewFolder: () -> Unit, onNewFile: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { show = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Add, stringResource(R.string.file_manager_new), Modifier.size(21.dp))
        }
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
                            icon = { modifier -> Icon(icon, null, modifier.size(20.dp)) },
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
private fun MoreMenu(
    sort: FileSort,
    showHidden: Boolean,
    onSort: (FileSort) -> Unit,
    onToggleHidden: () -> Unit,
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
                    val selected = when (index) {
                        0 -> sort == FileSort.NAME
                        1 -> sort == FileSort.SIZE
                        2 -> sort == FileSort.DATE
                        else -> false
                    }
                    DropdownImpl(
                        item = DropdownItem(
                            text = text,
                            icon = { modifier -> Icon(icon, null, modifier.size(20.dp)) },
                        ),
                        optionSize = items.size,
                        isSelected = selected,
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

private fun fileSummary(context: Context, entry: LinuxFileExplorer.Entry): String {
    val size = if (entry.isDir) context.getString(R.string.file_manager_type_folder)
    else Formatter.formatShortFileSize(context, entry.sizeBytes)
    return "$size · ${formatTime(entry.mtimeEpochSeconds)}"
}

private fun formatTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return "-"
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
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
    if (isDir) return EtaPreferenceColors.Blue
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
