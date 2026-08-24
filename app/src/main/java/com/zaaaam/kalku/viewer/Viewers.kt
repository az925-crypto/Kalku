package com.zaaaam.kalku.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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

/**
 * Pinch-zoom/pan that only engages with two pointers. Single-finger gestures
 * stay unconsumed so parent LazyColumns and HorizontalPager keep working.
 */
internal fun Modifier.twoFingerTransform(onTransform: (zoom: Float, pan: Offset) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var engaged = false
            while (true) {
                val event = awaitPointerEvent()
                val pressedCount = event.changes.count { it.pressed }
                when {
                    pressedCount >= 2 -> {
                        engaged = true
                        onTransform(event.calculateZoom(), event.calculatePan())
                        event.changes.forEach { it.consume() }
                    }
                    engaged -> {
                        if (event.changes.none { it.pressed }) break
                        // Swallow remaining lifts so the parent doesn't fling.
                        event.changes.forEach { it.consume() }
                    }
                    else -> break
                }
            }
        }
    }

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

/** Shown when a routed file id no longer resolves; auto-returns to safety. */
@Composable
internal fun MissingEntryScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        ViewerTopBar(
            title = "Tidak ditemukan",
            favorite = false,
            onBack = onBack,
            onToggleFavorite = {},
            onShare = {},
            onDelete = {},
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("File tidak ditemukan", color = Color.White.copy(alpha = 0.6f))
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1200)
        onBack()
    }
}

/**
 * Shown when the routed id isn't resolved YET because the index flow hasn't
 * emitted its first frame (cold start on a big vault). Without this, viewers
 * flashed "not found" and kicked back for perfectly valid files.
 */
@Composable
internal fun EntryLoadingScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        ViewerTopBar(
            title = "Memuat…",
            favorite = false,
            onBack = onBack,
            onToggleFavorite = {},
            onShare = {},
            onDelete = {},
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Membuka vault…", color = Color.White.copy(alpha = 0.6f))
        }
    }
}

/**
 * Resolves a routed id, distinguishing "index still loading" from "really
 * missing". Returns null while either holds; renders the right placeholder
 * screen and returns true when [onBack]-style bail-out UI took over.
 */
@Composable
internal fun EntryGate(vm: VaultViewModel, id: Long, onBack: () -> Unit): FileEntity? {
    val files by vm.allFiles.collectAsState()
    val entry = files.firstOrNull { it.id == id }
    if (entry != null) return entry
    if (files.isEmpty()) EntryLoadingScreen(onBack) else MissingEntryScreen(onBack)
    return null
}

// ------------------------------------------------------------- image viewer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(vm: VaultViewModel, id: Long, onBack: () -> Unit) {
    val images by vm.images.collectAsState()
    val startIndex = images.indexOfFirst { it.id == id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { images.size })
    var showInfo by remember { mutableStateOf(false) }
    // Track the page actually being viewed so swipes also land in Recents.
    RecordOpen(vm, images.getOrNull(pagerState.currentPage)?.id ?: id)

    // First frame may still be empty: once the list arrives, jump to the
    // tapped image instead of silently landing on item 0.
    var jumped by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(images) {
        if (!jumped && images.isNotEmpty()) {
            jumped = true
            val target = images.indexOfFirst { it.id == id }
            if (target >= 0 && target != pagerState.currentPage) {
                pagerState.scrollToPage(target)
            }
        }
    }

    // Deletions shrink the list; keep the pager inside bounds to avoid
    // IndexOutOfBounds on the frame where the list changed.
    androidx.compose.runtime.LaunchedEffect(images.size) {
        if (images.isNotEmpty() && pagerState.currentPage >= images.size) {
            pagerState.scrollToPage((images.size - 1).coerceAtLeast(0))
        }
    }

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
                    val entry = images.getOrNull(page) ?: return@HorizontalPager
                    ResolvedImage(vm = vm, relPath = entry.relPath, name = entry.name)
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

/** Resolves a (possibly encrypted) vault file to plaintext, then renders zoomable. */
@Composable
private fun ResolvedImage(vm: VaultViewModel, relPath: String, name: String) {
    var file by remember(relPath) { mutableStateOf<File?>(null) }
    androidx.compose.runtime.LaunchedEffect(relPath) {
        file = vm.plainDisplayFile(relPath)
    }
    val resolved = file
    if (resolved != null) {
        ZoomableImage(resolved)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Tidak bisa membuka gambar",
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ZoomableImage(file: File) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .twoFingerTransform { zoom, pan ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                offset = if (scale > 1f) offset + pan else Offset.Zero
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
