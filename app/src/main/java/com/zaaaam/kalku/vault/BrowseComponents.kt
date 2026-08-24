package com.zaaaam.kalku.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zaaaam.kalku.core.Format
import com.zaaaam.kalku.data.FileEntity
import com.zaaaam.kalku.ui.iconFor
import com.zaaaam.kalku.ui.theme.categoryColor

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
        title = {
            Text(
                title.ifEmpty { "Vault" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        navigationIcon = {
            if (canGoBack) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }
        },
        actions = {
            IconButton(onClick = onSortClick) { Icon(Icons.Default.Sort, "Sort", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onToggleView) {
                Icon(
                    Icons.Default.GridView,
                    "Toggle view",
                    tint = if (gridView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("New folder") }, onClick = { menu = false; onNewFolder() })
                DropdownMenuItem(text = { Text("New text file") }, onClick = { menu = false; onNewTextFile() })
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.height(64.dp),
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
    // HTML v4: .topbar.sel background var(--ink) #121412 (dark charcoal) even in light,
    // v5 dark: #0B0F0E. Keep dark for both themes to match spec — use ink token.
    val selContainer = Color(0xFF121412)
    val selContent = Color(0xFFF8F3EB)
    TopAppBar(
        title = {
            Column {
                Text("$count dipilih", style = MaterialTheme.typography.titleMedium, color = selContent)
                Text("dari vault", style = MaterialTheme.typography.bodySmall, color = selContent.copy(alpha = 0.7f))
            }
        },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Clear selection", tint = selContent) }
        },
        actions = {
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy", tint = selContent) }
            IconButton(onClick = onMove) { Icon(Icons.Default.DriveFileMove, "Move", tint = selContent) }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share", tint = selContent) }
            IconButton(onClick = onZip) { Text("ZIP", style = MaterialTheme.typography.labelLarge, color = selContent) }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = selContainer,
            titleContentColor = selContent,
            navigationIconContentColor = selContent,
            actionIconContentColor = selContent,
        ),
        modifier = Modifier.height(64.dp),
    )
}

@Composable
private fun tileHeadBackground(category: String): Color {
    // Use categoryColor with pastel wash — light uses 0.14 alpha over surface, dark uses deeper wash.
    // Hardcoded pastel mapping per HTML v4/v5 for closer 1:1 while still derived from token hue:
    // We blend categoryColor into surfaceVariant level to create distinct pastel per category.
    val base = categoryColor(category)
    val isDark = isSystemInDarkTheme()
    return if (isDark) {
        // Dark wash — low lightness, keep hue visible
        when (category) {
            "IMAGE" -> Color(0xFF2B1E19)
            "VIDEO" -> Color(0xFF1E2A22)
            "AUDIO" -> Color(0xFF241E14)
            "DOCUMENT" -> Color(0xFF252821)
            "CODE" -> Color(0xFF1A1E2A)
            "ARCHIVE" -> Color(0xFF232622)
            else -> base.copy(alpha = 0.22f)
        }
    } else {
        when (category) {
            "IMAGE" -> Color(0xFFFFF0EB)
            "VIDEO" -> Color(0xFFE8EFE6)
            "AUDIO" -> Color(0xFFF5E8C8)
            "DOCUMENT" -> Color(0xFFEDE8DF)
            "CODE" -> Color(0xFFEDE2F0)
            "ARCHIVE" -> Color(0xFFFFF7E8)
            else -> base.copy(alpha = 0.12f)
        }
    }
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
    val catColor = categoryColor(entry.category)
    val headBg = tileHeadBackground(entry.category)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (selected) 2.dp else 1.dp
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(borderWidth, borderColor),
        shadowElevation = if (selected) 6.dp else 0.dp,
        modifier = Modifier.combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(headBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    iconFor(entry.isFolder, entry.category),
                    contentDescription = entry.name,
                    tint = catColor,
                    modifier = Modifier.size(36.dp),
                )
                if (selecting || selected) {
                    // Check at TopStart 10dp like HTML .check — 22dp circle, 2px border
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 10.dp, top = 10.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.92f))
                            .border(
                                BorderStroke(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.2f)),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                // Also show check overlay for selected state when not in selecting mode? HTML shows check only when selected
                if (selected && !selecting) {
                    // Already handled above - but ensure visible
                }
            }
            // Divider line like HTML border-top
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Text(
                entry.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }
    }
    // Add selected shadow for 1.5px border effect: already border 2dp primary
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
    val containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    ListItem(
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = {
            Text(
                if (entry.isFolder) "Folder" else "${Format.bytes(entry.size)} · ${Format.date(entry.modifiedAt, "dd MMM HH:mm")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting || selected) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onTap() },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                    )
                } else {
                    val catColor = categoryColor(entry.category)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                            Icon(iconFor(entry.isFolder, entry.category), null, tint = catColor, modifier = Modifier.size(20.dp))
                        }
                    }
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
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
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
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
    )
}

@Composable
internal fun SortDialog(current: SortSpec, onDismiss: () -> Unit, onPick: (SortSpec) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort", style = MaterialTheme.typography.titleLarge) },
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
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = checked, onClick = onPickSpec)
        Text("${by.label} ${if (ascending) "↑" else "↓"}", style = MaterialTheme.typography.bodyMedium)
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
        title = { Text("/${at.substringAfterLast('/').ifEmpty { "Vault" }}", style = MaterialTheme.typography.titleMedium) },
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
        title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine("Type", if (entry.isFolder) "Folder" else entry.mime.ifEmpty { entry.category })
                InfoLine("Path", "/${entry.relPath}")
                if (!entry.isFolder) InfoLine("Size", Format.bytes(entry.size))
                InfoLine("Created", Format.date(entry.createdAt))
                InfoLine("Modified", Format.date(entry.modifiedAt))
                Spacer(Modifier.size(4.dp))
                Text("Tags", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
        label = { Text("comma separated", style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun SnackHost(vm: VaultViewModel) {
    val event by vm.toast.collectAsState()
    // remember the state so recompositions don't cancel an in-flight snackbar
    val state = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    // Key on the event id: two identical messages back-to-back must both show.
    androidx.compose.runtime.LaunchedEffect(event?.id) {
        event?.let {
            state.showSnackbar(it.msg)
            vm.dismissToast()
        }
    }
    androidx.compose.material3.SnackbarHost(hostState = state)
}
