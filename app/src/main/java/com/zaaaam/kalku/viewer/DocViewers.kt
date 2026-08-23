package com.zaaaam.kalku.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import com.zaaaam.kalku.fs.ZipUtils
import com.zaaaam.kalku.vault.VaultViewModel
import java.io.File

// ---------------------------------------------------------------- pdf viewer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(vm: VaultViewModel, id: Long, onBack: () -> Unit) {
    val entry = entryOrNull(vm, id)
    if (entry == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    RecordOpen(vm, entry.id)

    val file = remember(entry.id) { File(vm.repo.root, entry.relPath) }
    val pageCount = remember(entry.id) {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).pageCount
            }
        } catch (e: Exception) { -1 }
    }
    val pages = remember(pageCount) { if (pageCount > 0) (0 until pageCount).toList() else emptyList() }

    Column(Modifier.fillMaxSize()) {
        ViewerTopBar(
            title = entry.name,
            favorite = entry.favorite,
            onBack = onBack,
            onToggleFavorite = { vm.toggleFavorite(entry) },
            onShare = { vm.shareEntries(listOf(entry)) },
            onDelete = { vm.trash(listOf(entry.id)); onBack() },
            extraActions = {},
            onInfo = null,
        )
        if (pageCount < 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("PDF tidak dapat dibuka", color = MaterialTheme.colorScheme.error)
            }
        } else {
            var zoom by remember { mutableFloatStateOf(1f) }
            LazyColumn(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomDelta, _ ->
                        zoom = (zoom * zoomDelta).coerceIn(1f, 5f)
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(pages, key = { _, p -> p }) { index, page ->
                    PdfPage(file, page, zoom)
                    Text(
                        "${index + 1} / $pageCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

private val pageCache = mutableStateMapOf<String, Bitmap>()

@Composable
private fun PdfPage(file: File, pageIndex: Int, zoom: Float) {
    LaunchedEffect(file.path, pageIndex) {
        val key = "${file.path}#$pageIndex"
        if (!pageCache.contains(key)) {
            // Decode off the main thread; PdfRenderer is blocking I/O.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        PdfRenderer(fd).use { renderer ->
                            renderer.openPage(pageIndex).use { page ->
                                val w = (page.width * 2.2f).toInt().coerceAtMost(2200)
                                val h = (page.height * w.toFloat() / page.width).toInt()
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                if (pageCache.size > 12) pageCache.clear() // simple memory cap
                                pageCache[key] = bmp
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(file.path) {
        onDispose { pageCache.clear() }
    }
    val key = "${file.path}#$pageIndex"
    val bmp = pageCache[key]
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Page ${pageIndex + 1}",
            modifier = Modifier.fillMaxWidth(zoom.coerceAtMost(1f)).padding(horizontal = 4.dp),
        )
    } else {
        Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
            Text("Rendering…")
        }
    }
}

// ----------------------------------------------------------- archive viewer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveViewerScreen(vm: VaultViewModel, id: Long, onBack: () -> Unit) {
    val entry = entryOrNull(vm, id)
    if (entry == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val file = remember(entry.id) { File(vm.repo.root, entry.relPath) }
    val entries = remember(entry.id) {
        runCatching { ZipUtils.list(file) }.getOrDefault(emptyList())
    }
    RecordOpen(vm, entry.id)

    Column(Modifier.fillMaxSize()) {
        ViewerTopBar(
            title = entry.name,
            favorite = entry.favorite,
            onBack = onBack,
            onToggleFavorite = { vm.toggleFavorite(entry) },
            onShare = { vm.shareEntries(listOf(entry)) },
            onDelete = { vm.trash(listOf(entry.id)); onBack() },
            extraActions = {
                androidx.compose.material3.IconButton(onClick = { vm.extractArchive(entry.id); onBack() }) {
                    Icon(Icons.Default.Unarchive, "Extract all", tint = Color.White)
                }
            },
            onInfo = null,
        )
        Text(
            "${entries.count { !it.isDirectory }} files · ${com.zaaaam.kalku.core.Format.bytes(entries.sumOf { it.size })}",
            Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(entries.filterNot { it.isDirectory }, key = { _, e -> e.name }) { _, zipEntry ->
                ListItem(
                    headlineContent = {
                        Text(zipEntry.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text(com.zaaaam.kalku.core.Format.bytes(zipEntry.size)) },
                    trailingContent = {
                        androidx.compose.material3.TextButton(onClick = {
                            vm.runIo {
                                ZipUtils.extractEntry(file, zipEntry.name, file.parentFile ?: return@runIo)
                                vm.toast.value = "Extracted: ${zipEntry.name.substringAfterLast('/')}"
                            }
                        }) { Text("Extract") }
                    },
                )
            }
        }
    }
}
