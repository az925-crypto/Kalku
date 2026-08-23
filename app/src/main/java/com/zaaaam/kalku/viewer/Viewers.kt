package com.zaaaam.kalku.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zaaaam.kalku.core.Format
import com.zaaaam.kalku.data.FileEntity
import com.zaaaam.kalku.vault.VaultViewModel
import java.io.File

@Composable
internal fun entryOrNull(vm: VaultViewModel, id: Long): FileEntity? =
    vm.allFiles.collectAsState().value.firstOrNull { it.id == id }

@Composable
internal fun ViewerTopBar(
    title: String,
    favorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    extraActions: @Composable () -> Unit = {},
    onInfo: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
        }
        Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (favorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                "Favorite",
                tint = if (favorite) MaterialTheme.colorScheme.primary else Color.White,
            )
        }
        extraActions()
        IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
        if (onInfo != null) IconButton(onClick = onInfo) { Icon(Icons.Default.Info, "Info", tint = Color.White) }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
    }
}

@Composable
fun InfoDialog(entry: FileEntity, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoRow("Type", entry.mime.ifEmpty { entry.category })
                InfoRow("Size", Format.bytes(entry.size))
                InfoRow("Path", "/${entry.relPath}")
                InfoRow("Modified", Format.date(entry.modifiedAt))
                if (entry.tags.isNotBlank()) InfoRow("Tags", entry.tags)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 24.dp))
    }
}

// ------------------------------------------------------------- image viewer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(vm: VaultViewModel, id: Long, onBack: () -> Unit) {
    val images by vm.images.collectAsState()
    val startIndex = images.indexOfFirst { it.id == id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { images.size })
    var showInfo by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            ViewerTopBar(
                title = images.getOrNull(pagerState.currentPage)?.name ?: "",
                favorite = images.getOrNull(pagerState.currentPage)?.favorite == true,
                onBack = onBack,
                onToggleFavorite = { images.getOrNull(pagerState.currentPage)?.let { vm.toggleFavorite(it) } },
                onShare = {
                    images.getOrNull(pagerState.currentPage)?.let { e ->
                        vm.shareEntries(listOf(e))
                    }
                },
                onDelete = {
                    images.getOrNull(pagerState.currentPage)?.let { vm.trash(listOf(it.id)) }
                    if (images.size <= 1) onBack()
                },
                onInfo = { showInfo = true },
            )
            if (images.isEmpty()) {
                Box(Modifier.fillMaxSize())
            } else {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val entry = images[page]
                    ZoomableImage(File(vm.repo.root, entry.relPath))
                }
            }
        }
    }

    val current = pagerState.currentPage
    val infoTarget = images.getOrNull(current)
    if (showInfo && infoTarget != null) {
        InfoDialog(infoTarget) { showInfo = false }
    }
}

@Composable
private fun ZoomableImage(file: File) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = file,
            contentDescription = file.name,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
