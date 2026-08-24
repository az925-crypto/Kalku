package com.zaaaam.kalku.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.zaaaam.kalku.fs.ZipUtils
import com.zaaaam.kalku.vault.VaultViewModel
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File

// ---------------------------------------------------------------- pdf viewer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(vm: VaultViewModel, id: Long, onBack: () -> Unit) {
    val entry = EntryGate(vm, id, onBack)
    if (entry == null) {
        return
    }
    RecordOpen(vm, entry.id)

    // Encrypted vault files need a decrypted, seekable copy for PdfRenderer.
    var pdfFile by remember(entry.id) { mutableStateOf<File?>(null) }
    LaunchedEffect(entry.id) {
        pdfFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            vm.plainDisplayFile(entry.relPath)
        }
    }
    val file = pdfFile

    if (file == null) {
        Column(Modifier.fillMaxSize()) {
            ViewerTopBar(title = entry.name, favorite = entry.favorite, onBack = onBack,
                onToggleFavorite = { vm.toggleFavorite(entry) }, onShare = {}, onDelete = {})
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Membuka PDF…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    // One renderer owned by the screen: constructing a fresh PdfRenderer per
    // page re-parsed the entire document for every render.
    var rendererState by remember(file) { mutableStateOf<PdfRenderer?>(null) }
    var pdfLoadDone by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        rendererState = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    PdfRenderer(pfd)
                } catch (e: Exception) {
                    pfd.close()
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
        pdfLoadDone = true
    }
    // Serial dispatcher: PdfRenderer is not thread-safe, and Dispatchers.IO
    // would happily render two pages concurrently.
    val renderDispatcher = remember(file) {
        java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }
    androidx.compose.runtime.DisposableEffect(file) {
        onDispose {
            rendererState?.close()
            renderDispatcher.close()
        }
    }
    val renderer = rendererState

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
        if (renderer == null && !pdfLoadDone) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Membuka PDF…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (renderer == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("PDF tidak dapat dibuka", color = MaterialTheme.colorScheme.error)
            }
        } else {
            val pageCount = renderer.pageCount
            val pages = remember(pageCount) { (0 until pageCount).toList() }
            var zoom by remember { mutableFloatStateOf(1f) }
            // Cache is owned by the screen, not per-page: a per-page DisposableEffect
            // cleared the whole map whenever one page left composition, throwing away
            // still-visible pages that then stayed stuck on "Rendering…" forever.
            val pageCache = remember(entry.id) { mutableStateMapOf<String, Bitmap>() }
            val renderOrder = remember(entry.id) { mutableListOf<String>() }
            val inFlight = remember(entry.id) { mutableSetOf<String>() }
            androidx.compose.runtime.DisposableEffect(entry.id) {
                onDispose {
                    pageCache.clear()
                    renderOrder.clear()
                    inFlight.clear()
                }
            }
            LazyColumn(
                Modifier.fillMaxSize().twoFingerTransform { zoomDelta, _ ->
                    zoom = (zoom * zoomDelta).coerceIn(1f, 5f)
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(pages, key = { _, p -> p }) { index, page ->
                    PdfPage(renderer, renderDispatcher, page, zoom, pageCache, renderOrder, inFlight)
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

private const val PAGE_CACHE_LIMIT = 6

/** Renders one page off the main thread; returns null on any failure incl. OOM. */
private suspend fun renderPage(
    renderer: PdfRenderer,
    dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    pageIndex: Int,
): Bitmap? =
    try {
        kotlinx.coroutines.withContext(dispatcher) {
            renderer.openPage(pageIndex).use { page ->
                // Cap bitmap size: full-res pages at 2200px could OOM the
                // process on large scanned PDFs (× cache limit). RGB_565 halves
                // memory vs ARGB_8888 — fine for opaque rendered pages.
                val w = (page.width * 2.2f).toInt().coerceAtMost(1600)
                val h = (page.height * w.toFloat() / page.width).toInt()
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    } catch (t: Throwable) {
        // Includes OutOfMemoryError and races against renderer close on dispose —
        // never crash the viewer over a bad page.
        null
    }

@Composable
private fun PdfPage(
    renderer: PdfRenderer,
    renderDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    pageIndex: Int,
    zoom: Float,
    pageCache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Bitmap>,
    renderOrder: MutableList<String>,
    inFlight: MutableSet<String>,
) {
    val key = "${renderer.hashCode()}#$pageIndex"
    LaunchedEffect(key) {
        if (pageCache.containsKey(key) || !inFlight.add(key)) return@LaunchedEffect
        // Decode off the main thread; PdfRenderer is blocking I/O.
        val bmp = renderPage(renderer, renderDispatcher, pageIndex)
        inFlight.remove(key)
        if (bmp != null && !pageCache.containsKey(key)) {
            // Evict oldest entries instead of clearing everything.
            while (renderOrder.size >= PAGE_CACHE_LIMIT) {
                pageCache.remove(renderOrder.removeAt(0))
            }
            renderOrder.add(key)
            pageCache[key] = bmp
        }
    }
    val bmp = pageCache[key]
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Page ${pageIndex + 1}",
            // Real visual zoom: fillMaxWidth(zoom.coerceAtMost(1f)) could never exceed 1f.
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                },
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
    val entry = EntryGate(vm, id, onBack)
    if (entry == null) {
        return
    }
    RecordOpen(vm, entry.id)

    // ZIP listing needs a seekable plaintext file.
    var zipFile by remember(entry.id) { mutableStateOf<File?>(null) }
    LaunchedEffect(entry.id) {
        zipFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            vm.plainDisplayFile(entry.relPath)
        }
    }

    val file = zipFile

    // ZIP central-directory parsing is disk I/O — keep it off the main thread
    // and out of composition (remember{} used to run it synchronously).
    var entriesState by remember(file) { mutableStateOf<List<ZipUtils.Entry>?>(null) }
    LaunchedEffect(file) {
        entriesState = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            file?.let { runCatching { ZipUtils.list(it) }.getOrDefault(emptyList()) }
        }
    }
    val entries = entriesState ?: emptyList()

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
            when {
                file == null || entriesState == null -> "Membuka archive…"
                else -> "${entries.count { !it.isDirectory }} files · ${com.zaaaam.kalku.core.Format.bytes(entries.sumOf { it.size })}"
            },
            Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
            // Composite key: legal ZIPs may contain duplicate entry names.
            itemsIndexed(entries.filterNot { it.isDirectory }, key = { idx, e -> "$idx:${e.name}" }) { _, zipEntry ->
                ListItem(
                    headlineContent = {
                        Text(zipEntry.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text(com.zaaaam.kalku.core.Format.bytes(zipEntry.size)) },
                    trailingContent = {
                        androidx.compose.material3.TextButton(onClick = {
                            val f = file ?: return@TextButton
                            vm.runIo {
                                // Extract through the vault pipeline so the entry
                                // lands in the archive's folder (encrypted when
                                // the session is), not in an invisible cache dir.
                                // Unique per run: concurrent extracts must not
                                // share one staging dir.
                                val stage = java.io.File(vm.repo.appContext.cacheDir,
                                    "entry_extract_${System.currentTimeMillis()}")
                                    .also { it.mkdirs() }
                                try {
                                    val out = ZipUtils.extractEntry(f, zipEntry.name, stage)
                                    if (out != null) {
                                        val parentRel = entry.relPath.substringBeforeLast('/', "")
                                        val imported = vm.repo.importLocalFile(out, parentRel)
                                        vm.showToast(imported?.let { "Extracted: $it" }
                                            ?: "Gagal extract ${zipEntry.name.substringAfterLast('/')}")
                                    } else {
                                        vm.showToast("Gagal extract ${zipEntry.name}")
                                    }
                                } finally {
                                    stage.deleteRecursively()
                                }
                            }
                        }) { Text("Extract") }
                    },
                )
            }
        }
    }
}
