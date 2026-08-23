package com.zaaaam.kalku.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zaaaam.kalku.data.FileEntity
import com.zaaaam.kalku.ui.ConfirmDialog
import com.zaaaam.kalku.ui.EmptyState
import com.zaaaam.kalku.ui.TextEntryDialog

/** Multi-select bookkeeping for one screen instance. */
class SelectionState {
    var selected by mutableStateOf<Set<Long>>(emptySet())

    val isActive: Boolean get() = selected.isNotEmpty()

    fun toggle(id: Long) {
        selected = if (selected.contains(id)) selected - id else selected + id
    }

    fun selectAll(ids: Collection<Long>) { selected = ids.toSet() }
    fun clear() { selected = emptySet() }
}

private enum class CopyMove { COPY, MOVE }

private sealed interface BrowseDialog {
    data object Sort : BrowseDialog
    data class Rename(val entry: FileEntity) : BrowseDialog
    data class Details(val entry: FileEntity) : BrowseDialog
    data object Delete : BrowseDialog
    data object Zip : BrowseDialog
    data class PickFolder(val mode: CopyMove) : BrowseDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    vm: VaultViewModel,
    folder: String,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (FileEntity) -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(folder) { vm.openFolder(folder) }

    val children by vm.children.collectAsState()
    val allFolders by vm.allFiles.collectAsState()
    var sortSpec by remember { mutableStateOf(SortSpec()) }

    val selection = remember(folder) { SelectionState() }
    var gridView by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<BrowseDialog?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.import(uris, folder) }

    val pendingExportRef = remember { mutableStateOf<FileEntity?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destUri ->
        val target = pendingExportRef.value
        if (destUri != null && target != null) vm.export(target, destUri)
        pendingExportRef.value = null
    }

    Scaffold(
        topBar = {
            if (selection.isActive) {
                SelectionTopBar(
                    count = selection.selected.size,
                    onClose = { selection.clear() },
                    onCopy = { dialog = BrowseDialog.PickFolder(CopyMove.COPY) },
                    onMove = { dialog = BrowseDialog.PickFolder(CopyMove.MOVE) },
                    onShare = {
                        val targets = children.filter { it.id in selection.selected }
                        vm.shareEntries(targets)
                        selection.clear()
                    },
                    onZip = { dialog = BrowseDialog.Zip },
                    onDelete = { dialog = BrowseDialog.Delete },
                )
            } else {
                BrowserTopBar(
                    title = folder.substringAfterLast('/').ifEmpty { "Vault" },
                    canGoBack = folder.isNotEmpty(),
                    onBack = onBack,
                    gridView = gridView,
                    onToggleView = { gridView = !gridView },
                    onSortClick = { dialog = BrowseDialog.Sort },
                    onNewFolder = { vm.createFolder(folder, "New folder") },
                    onNewTextFile = { vm.createTextFile(folder, "note.txt") },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = androidx.compose.ui.Modifier.size(18.dp))
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.size(8.dp))
                Text("Impor file", style = MaterialTheme.typography.labelLarge)
            }
        },
        snackbarHost = { SnackHost(vm) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            val sorted = remember(children, sortSpec) { VaultViewModel.sortEntries(children, sortSpec) }
            if (sorted.isEmpty()) {
                EmptyState(
                    text = "Folder kosong — tekan Import untuk menambah file",
                    icon = Icons.Default.CreateNewFolder,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (gridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(sorted, key = { it.id }) { entry ->
                        GridEntry(
                            entry = entry,
                            selected = entry.id in selection.selected,
                            selecting = selection.isActive,
                            onTap = { handleTap(entry, selection, onOpenFolder, onOpenFile) },
                            onLongPress = { selection.toggle(entry.id) },
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(sorted, key = { it.id }) { entry ->
                        ListRow(
                            entry = entry,
                            selected = entry.id in selection.selected,
                            selecting = selection.isActive,
                            onTap = { handleTap(entry, selection, onOpenFolder, onOpenFile) },
                            onLongPress = { selection.toggle(entry.id) },
                            onFavorite = { vm.toggleFavorite(entry) },
                            onRename = { dialog = BrowseDialog.Rename(entry) },
                            onDetails = { dialog = BrowseDialog.Details(entry) },
                            onDelete = { vm.trash(listOf(entry.id)) },
                            onExport = {
                                pendingExportRef.value = entry
                                exportLauncher.launch(entry.name)
                            },
                        )
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        BrowseDialog.Sort -> SortDialog(sortSpec, { dialog = null }) { sortSpec = it; dialog = null }
        is BrowseDialog.Rename -> TextEntryDialog(
            title = "Rename", initial = d.entry.name,
            onDismiss = { dialog = null },
            onConfirm = { vm.rename(d.entry.id, it); dialog = null },
        )
        is BrowseDialog.Details -> DetailsDialog(d.entry, vm) { dialog = null }
        BrowseDialog.Delete -> ConfirmDialog(
            title = "Hapus ${selection.selected.size} item?",
            message = "Item dipindah ke Recycle Bin.",
            confirmText = "Delete",
            destructive = true,
            onDismiss = { dialog = null },
            onConfirm = {
                vm.trash(selection.selected.toList())
                selection.clear(); dialog = null
            },
        )
        BrowseDialog.Zip -> TextEntryDialog(
            title = "Buat ZIP", label = "Archive name", initial = "archive.zip",
            confirmText = "Create",
            onDismiss = { dialog = null },
            onConfirm = { name ->
                val targets = children.filter { it.id in selection.selected }
                vm.zipEntries(targets, folder, name)
                selection.clear(); dialog = null
            },
        )
        is BrowseDialog.PickFolder -> FolderPickerDialog(
            allFolders = allFolders.filter { it.isFolder }.map { it.relPath } + "",
            startAt = folder,
            actionLabel = if (d.mode == CopyMove.COPY) "Copy ke sini" else "Move ke sini",
            onDismiss = { dialog = null },
            onPick = { dest ->
                if (d.mode == CopyMove.COPY) vm.copy(selection.selected.toList(), dest)
                else vm.move(selection.selected.toList(), dest)
                selection.clear(); dialog = null
            },
        )
        null -> {}
    }
}

private fun handleTap(
    entry: FileEntity,
    selection: SelectionState,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (FileEntity) -> Unit,
) {
    if (selection.isActive) selection.toggle(entry.id)
    else if (entry.isFolder) onOpenFolder(entry.relPath)
    else onOpenFile(entry)
}
