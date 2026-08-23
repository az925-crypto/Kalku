package com.zaaaam.kalku.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import coil.compose.AsyncImage
import com.zaaaam.kalku.core.Category
import com.zaaaam.kalku.core.Format
import com.zaaaam.kalku.data.FileEntity
import com.zaaaam.kalku.fs.VaultPaths
import com.zaaaam.kalku.ui.ConfirmDialog
import com.zaaaam.kalku.ui.EmptyState
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

    Scaffold(topBar = {
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Cari nama / folder / tag…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, null) } },
        )
    }, snackbarHost = { SnackHost(vm) }) { padding ->
        Column(Modifier.padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(selected = favoriteOnly, onClick = { favoriteOnly = !favoriteOnly }, label = { Text("★ Favorite") })
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                listOf(Category.IMAGE, Category.VIDEO, Category.AUDIO, Category.DOCUMENT, Category.CODE, Category.ARCHIVE).forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = if (category == c) null else c },
                        label = { Text(c.label) },
                    )
                }
            }
            if (results.isEmpty()) {
                EmptyState("Tidak ada hasil", Icons.Default.Search, Modifier.fillMaxSize())
            } else {
                LazyColumn {
                    items(results, key = { it.id }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("/${entry.relPath}", style = MaterialTheme.typography.bodySmall, maxLines = 1) },
                            leadingContent = { Icon(com.zaaaam.kalku.ui.iconFor(entry.isFolder, entry.category), null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { Text(Format.bytes(entry.size), style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.clickable { onOpenFile(entry) },
                        )
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
        topBar = { TopAppBar(title = { Text("Favorites") }) },
        snackbarHost = { SnackHost(vm) },
    ) { padding ->
        if (favorites.isEmpty()) {
            EmptyState("Belum ada favorit", Icons.Default.Search, Modifier.padding(padding).fillMaxSize())
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(favorites, key = { it.id }) { f ->
                    ListItem(
                        headlineContent = { Text(f.name, maxLines = 1) },
                        leadingContent = { Icon(com.zaaaam.kalku.ui.iconFor(f.isFolder, f.category), null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { onOpenFile(f) },
                    )
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recycle Bin") },
                actions = {
                    IconButton(onClick = { confirmEmpty = true }) {
                        Icon(Icons.Default.DeleteForever, "Empty trash", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
        snackbarHost = { SnackHost(vm) },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState("Recycle Bin kosong", Icons.Default.DeleteOutline, Modifier.padding(padding).fillMaxSize())
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(items, key = { it.id }) { t ->
                    ListItem(
                        headlineContent = { Text(t.name, maxLines = 1) },
                        supportingContent = {
                            Text("${Format.bytes(t.size)} · deleted ${Format.date(t.deletedAt)}", style = MaterialTheme.typography.bodySmall)
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { vm.restore(t.id) }) { Icon(Icons.Default.Restore, "Restore") }
                                IconButton(onClick = { vm.permanentDelete(t.id) }) {
                                    Icon(Icons.Default.DeleteForever, "Delete forever", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        },
                    )
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
}

// ------------------------------------------------------------------ gallery

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    vm: VaultViewModel,
    onBack: () -> Unit,
    onOpenImage: (Int) -> Unit,
) {
    val images by vm.images.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Photos (${images.size})") }) }) { padding ->
        if (images.isEmpty()) {
            EmptyState("Belum ada gambar", Icons.Default.Image, Modifier.padding(padding).fillMaxSize())
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val indexedImages = remember(images) { images.withIndex().toList() }
                items(indexedImages, key = { it.value.id }) { (index, img) ->
                    // Coil resolves the File async and handles missing files gracefully;
                    // no per-item exists() stat on the main thread.
                    AsyncImage(
                        model = File(vm.repo.root, img.relPath),
                        contentDescription = img.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenImage(index) },
                    )
                }
            }
        }
    }
}
