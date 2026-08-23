package com.zaaaam.kalku.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zaaaam.kalku.core.Category
import com.zaaaam.kalku.core.Format
import com.zaaaam.kalku.data.FileEntity
import com.zaaaam.kalku.ui.iconFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: VaultViewModel,
    onOpenFolder: (String) -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onTrash: () -> Unit,
    onGallery: () -> Unit,
    onSettings: () -> Unit,
    onLock: () -> Unit,
    onOpenFile: (FileEntity) -> Unit,
) {
    val stats by vm.stats.collectAsState()
    val total by vm.totalSize.collectAsState()
    val recents by vm.recents.collectAsState()
    val favorites by vm.favorites.collectAsState()
    var query by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Vault", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onGallery) { Icon(Icons.Default.MoreHoriz, "Gallery", tint = MaterialTheme.colorScheme.onPrimary) }
                        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onPrimary) }
                        IconButton(onClick = onLock) { Icon(Icons.Default.Lock, "Lock", tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            if (it.length >= 2) onSearch()
                        },
                        placeholder = { Text("Cari file…", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onPrimary) },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSearch() }
                            .background(androidx.compose.ui.graphics.Color.Transparent),
                        enabled = false,
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Storage", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Text(Format.bytes(total), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.size(6.dp))
                val maxCat = stats.maxOfOrNull { it.size }?.coerceAtLeast(1L) ?: 1L
                stats.forEach { s ->
                    if (s.count > 0 || s.size > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = false) {}) {
                            Text(s.category.label, Modifier.size(width = 90.dp, height = 20.dp), style = MaterialTheme.typography.bodySmall)
                            Box(Modifier.weight(1f)) {
                                LinearProgressIndicator(
                                    progress = { (s.size.toFloat() / maxCat).coerceIn(0.02f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Text(Format.bytes(s.size), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            SectionHeader("Categories")
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(((stats.size + 2) / 3 * 84).dp),
                userScrollEnabled = false,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(stats.filter { it.category != Category.OTHER }) { s ->
                    CategoryCard(s.category, s.count, onOpenFolder)
                }
            }
        }
        item {
            SectionHeader("Recent")
            if (recents.isEmpty()) {
                Text("Belum ada file dibuka", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
            } else {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    recents.take(5).forEach { r ->
                        RecentRow(r.name, r.category) {
                            vm.byPathThenOpen(r.relPath)?.let(onOpenFile)
                        }
                    }
                }
            }
        }
        item {
            SectionHeaderRow("Favorites", onFavorites)
            if (favorites.isEmpty()) {
                Text("Belum ada favorit", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
            } else {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    favorites.take(5).forEach { f ->
                        RecentRow(f.name, f.category) { onOpenFile(f) }
                    }
                }
            }
        }
        item {
            SectionRow(Icons.Default.DeleteOutline, "Recycle Bin", "${vm.trashItems.collectAsState().value.size} item", onTrash)
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun SectionHeaderRow(title: String, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onMore() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.FavoriteBorder, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
internal fun CategoryCard(category: Category, count: Int, onOpenFolder: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        onClick = { onOpenFolder(category.label) },
    ) {
        Column(Modifier.padding(12.dp)) {
            Icon(iconFor(false, category.name), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(6.dp))
            Text(category.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text("$count file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun RecentRow(name: String, category: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(iconFor(false, category), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(12.dp))
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.weight(1f))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp).padding(start = 4.dp), tint = MaterialTheme.colorScheme.outline)
    }
}
