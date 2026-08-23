package com.zaaaam.kalku.vault

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zaaaam.kalku.core.Format
import com.zaaaam.kalku.data.FileEntity
import com.zaaaam.kalku.ui.iconFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserTopBar(
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    gridView: Boolean,
    onToggleView: () -> Unit,
    onSortClick: () -> Unit,
    onNewFolder: () -> Unit,
    onNewTextFile: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (canGoBack) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }
        },
        actions = {
            IconButton(onClick = onSortClick) { Icon(Icons.Default.Sort, "Sort") }
            IconButton(onClick = onToggleView) {
                Icon(Icons.Default.GridView, "Toggle view", tint = if (gridView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("New folder") }, onClick = { menu = false; onNewFolder() })
                DropdownMenuItem(text = { Text("New text file") }, onClick = { menu = false; onNewTextFile() })
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onZip: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text("$count dipilih") },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Clear selection") }
        },
        actions = {
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy") }
            IconButton(onClick = onMove) { Icon(Icons.Default.DriveFileMove, "Move") }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share") }
            IconButton(onClick = onZip) { Text("ZIP", style = MaterialTheme.typography.labelLarge) }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GridEntry(
    entry: FileEntity,
    selected: Boolean,
    selecting: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val catColor = com.zaaaam.kalku.ui.theme.categoryColor(entry.category)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(catColor.copy(alpha = 0.14f))
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    iconFor(entry.isFolder, entry.category),
                    contentDescription = entry.name,
                    tint = catColor,
                    modifier = Modifier.size(30.dp),
                )
                if (selecting || selected) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onTap() },
                        modifier = Modifier.align(Alignment.TopEnd).size(26.dp),
                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            Text(
                entry.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ListRow(
    entry: FileEntity,
    selected: Boolean,
    selecting: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                if (entry.isFolder) "Folder" else "${Format.bytes(entry.size)} · ${Format.date(entry.modifiedAt)}",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting || selected) {
                    Checkbox(checked = selected, onCheckedChange = { onTap() })
                } else {
                    Icon(iconFor(entry.isFolder, entry.category), null, tint = com.zaaaam.kalku.ui.theme.categoryColor(entry.category))
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (entry.favorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        "Favorite",
                        tint = if (entry.favorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More", modifier = Modifier.size(20.dp)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!entry.isFolder) {
                            DropdownMenuItem(text = { Text("Export / Save copy") }, onClick = { menu = false; onExport() })
                        }
                        DropdownMenuItem(text = { Text("Details") }, onClick = { menu = false; onDetails() })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
    )
}

@Composable
internal fun SortDialog(current: SortSpec, onDismiss: () -> Unit, onPick: (SortSpec) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort") },
        text = {
            LazyColumn {
                items(SortBy.entries) { by ->
                    SortOptionRow(by, current.by == by && current.ascending, true, onPickSpec = {
                        onPick(SortSpec(by, true))
                    })
                    SortOptionRow(by, current.by == by && !current.ascending, false, onPickSpec = {
                        onPick(SortSpec(by, false))
                    })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SortOptionRow(by: SortBy, checked: Boolean, ascending: Boolean, onPickSpec: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = checked, onClick = onPickSpec)
        Text("${by.label} ${if (ascending) "↑" else "↓"}")
    }
}

@Composable
internal fun FolderPickerDialog(
    allFolders: List<String>,
    startAt: String,
    actionLabel: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var at by remember { mutableStateOf(startAt) }
    val subs = allFolders.filter { it.startsWith(if (at.isEmpty()) "" else "$at/") }
        .map { it.removePrefix(at).trim('/') }
        .filter { it.isNotEmpty() }
        .map { it.substringBefore('/') }
        .distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("/${at.substringAfterLast('/').ifEmpty { "Vault" }}") },
        text = {
            Column {
                if (at.isNotEmpty()) {
                    TextButton(onClick = { at = at.substringBeforeLast('/', "") }) { Text(".. up") }
                }
                LazyColumn {
                    items(subs) { s ->
                        TextButton(onClick = { at = if (at.isEmpty()) s else "$at/$s" }) { Text("> $s") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(at) }) { Text(actionLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun DetailsDialog(entry: FileEntity, vm: VaultViewModel, onDismiss: () -> Unit) {
    var tags by remember { mutableStateOf(entry.tags) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                InfoLine("Type", if (entry.isFolder) "Folder" else entry.mime.ifEmpty { entry.category })
                InfoLine("Path", "/${entry.relPath}")
                if (!entry.isFolder) InfoLine("Size", Format.bytes(entry.size))
                InfoLine("Created", Format.date(entry.createdAt))
                InfoLine("Modified", Format.date(entry.modifiedAt))
                Spacer(Modifier.size(8.dp))
                Text("Tags", style = MaterialTheme.typography.labelMedium)
                TagsField(tags) { tags = it }
                TextButton(onClick = { vm.setTags(entry, tags.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()) }) {
                    Text("Save tags")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun InfoLine(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(v, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun TagsField(initial: String, onChange: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { value = it; onChange(it) },
        label = { Text("comma separated") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun SnackHost(vm: VaultViewModel) {
    val toast by vm.toast.collectAsState()
    val state = androidx.compose.material3.SnackbarHostState()
    androidx.compose.runtime.LaunchedEffect(toast) {
        toast?.let {
            state.showSnackbar(it)
            vm.dismissToast()
        }
    }
    androidx.compose.material3.SnackbarHost(hostState = state)
}
