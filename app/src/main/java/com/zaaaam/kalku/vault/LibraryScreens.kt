package com.zaaaam.kalku.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zaaaam.kalku.core.Category
import com.zaaaam.kalku.core.Format
import com.zaaaam.kalku.data.FileEntity
import com.zaaaam.kalku.fs.VaultPaths
import com.zaaaam.kalku.ui.ConfirmDialog
import com.zaaaam.kalku.ui.EmptyState
import com.zaaaam.kalku.ui.iconFor
import com.zaaaam.kalku.ui.theme.categoryColor
import java.io.File

// ------------------------------------------------------------------- search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: VaultViewModel, onBack: () -> Unit, onOpenFile: (FileEntity) -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<Category?>(null) }
    var favoriteOnly by remember { mutableStateOf(false) }

    val results = remember(query, category, favoriteOnly, vm.allFiles.collectAsState().value) {
        vm.search(VaultViewModel.SearchFilter(query, category, favoriteOnly))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Cari nama / folder / tag…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(999.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackHost(vm) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Filter chips — horizontal scroll like HTML .filters
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = favoriteOnly,
                    onClick = { favoriteOnly = !favoriteOnly },
                    label = { Text("★ Favorite", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = favoriteOnly,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = MaterialTheme.colorScheme.secondary,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.dp,
                    ),
                    shape = RoundedCornerShape(999.dp),
                )
                FilterChip(
                    selected = category == null,
                    onClick = { category = null },
                    label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                        selectedLabelColor = MaterialTheme.colorScheme.background,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = category == null,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = MaterialTheme.colorScheme.onBackground,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.dp,
                    ),
                    shape = RoundedCornerShape(999.dp),
                )
                listOf(Category.IMAGE, Category.VIDEO, Category.AUDIO, Category.DOCUMENT, Category.CODE, Category.ARCHIVE).forEach { c ->
                    val isSel = category == c
                    FilterChip(
                        selected = isSel,
                        onClick = { category = if (isSel) null else c },
                        label = { Text(c.label, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(iconFor(false, c.name), null, tint = if (isSel) MaterialTheme.colorScheme.background else categoryColor(c.name), modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = categoryColor(c.name),
                            selectedLabelColor = MaterialTheme.colorScheme.surface,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSel,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = categoryColor(c.name),
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.dp,
                        ),
                        shape = RoundedCornerShape(999.dp),
                    )
                }
            }
            if (results.isEmpty()) {
                EmptyState("Tidak ada hasil", Icons.Default.Search, Modifier.fillMaxSize())
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(results, key = { it.id }) { entry ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).clickable { onOpenFile(entry) },
                        ) {
                            ListItem(
                                headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
                                supportingContent = { Text("/${entry.relPath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = {
                                    Box(
                                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).then(Modifier),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier.size(40.dp),
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                Icon(iconFor(entry.isFolder, entry.category), null, tint = categoryColor(entry.category), modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                },
                                trailingContent = { Text(Format.bytes(entry.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- favorites

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(vm: VaultViewModel, onBack: () -> Unit, onOpenFile: (FileEntity) -> Unit) {
    val favorites by vm.favorites.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Favorites", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackHost(vm) },
    ) { padding ->
        if (favorites.isEmpty()) {
            EmptyState("Belum ada favorit", Icons.Default.Search, Modifier.padding(padding).fillMaxSize())
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(favorites, key = { it.id }) { f ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).clickable { onOpenFile(f) },
                    ) {
                        ListItem(
                            headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
                            supportingContent = { Text(Format.bytes(f.size) + " • " + f.category.lowercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingContent = {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(iconFor(f.isFolder, f.category), null, tint = categoryColor(f.category), modifier = Modifier.size(20.dp))
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------- recycle bin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(vm: VaultViewModel, onBack: () -> Unit) {
    val items by vm.trashItems.collectAsState()
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Recycle Bin", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { confirmEmpty = true }) {
                        Icon(Icons.Default.DeleteForever, "Empty trash", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackHost(vm) },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState("Recycle Bin kosong", Icons.Default.DeleteOutline, Modifier.padding(padding).fillMaxSize())
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(items, key = { it.id }) { t ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    ) {
                        ListItem(
                            headlineContent = { Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
                            supportingContent = {
                                Text("${Format.bytes(t.size)} · deleted ${Format.date(t.deletedAt, "dd MMM HH:mm")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            leadingContent = {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    }
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { vm.restore(t.id) }) { Icon(Icons.Default.Restore, "Restore", tint = MaterialTheme.colorScheme.primary) }
                                    IconButton(onClick = { confirmDeleteId = t.id }) {
                                        Icon(Icons.Default.DeleteForever, "Delete forever", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        )
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = "Kosongkan Recycle Bin?",
            message = "Semua item dihapus permanen.",
            confirmText = "Delete all",
            destructive = true,
            onDismiss = { confirmEmpty = false },
            onConfirm = { confirmEmpty = false; vm.emptyTrash() },
        )
    }

    confirmDeleteId?.let { id ->
        val target = items.firstOrNull { it.id == id }
        ConfirmDialog(
            title = "Hapus permanen?",
            message = "\"${target?.name ?: "Item ini"}\" akan dihapus permanen dan tidak bisa dipulihkan.",
            confirmText = "Hapus permanen",
            destructive = true,
            onDismiss = { confirmDeleteId = null },
            onConfirm = {
                confirmDeleteId = null
                vm.permanentDelete(id)
            },
        )
    }
}

// ------------------------------------------------------------------ gallery

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    vm: VaultViewModel,
    onBack: () -> Unit,
    onOpenImage: (Long) -> Unit,
) {
    val images by vm.images.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Photos (${images.size})", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (images.isEmpty()) {
            EmptyState("Belum ada gambar", Icons.Default.Image, Modifier.padding(padding).fillMaxSize())
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.padding(padding).padding(horizontal = 14.dp, vertical = 10.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(images, key = { it.id }) { img ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        // Pass the id, not the index: the backing list can reorder
                        // (rescan) between render and tap.
                        modifier = Modifier.clickable { onOpenImage(img.id) },
                    ) {
                        val thumbFile by androidx.compose.runtime.produceState<File?>(
                            initialValue = null,
                            key1 = img.id,
                        ) {
                            value = vm.plainDisplayFile(img.relPath)
                        }
                        if (thumbFile != null) {
                            AsyncImage(
                                model = thumbFile,
                                contentDescription = img.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(18.dp)),
                            )
                        } else {
                            Box(
                                Modifier.fillMaxWidth().height(140.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(iconFor(false, img.category), null, tint = categoryColor(img.category))
                            }
                        }
                    }
                }
            }
        }
    }
}
