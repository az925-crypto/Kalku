package com.zaaaam.kalku.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.zaaaam.kalku.core.Format
import com.zaaaam.kalku.vault.VaultViewModel
import java.io.File

private val SPEEDS = listOf(0.5f, 1f, 1.5f, 2f)

// ------------------------------------------------------------- video player

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(vm: VaultViewModel, id: Long, onBack: () -> Unit) {
    val entry = entryOrNull(vm, id)
    val context = LocalContext.current

    if (entry == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val player = remember(entry.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(File(vm.repo.root, entry.relPath).toURI().toString()))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    var speedIdx by remember { mutableStateOf(1) }
    LaunchedEffect(speedIdx) { player.setPlaybackSpeed(SPEEDS[speedIdx]) }
    RecordOpen(vm, entry.id)

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        ViewerTopBar(
            title = entry.name,
            favorite = entry.favorite,
            onBack = onBack,
            onToggleFavorite = { vm.toggleFavorite(entry) },
            onShare = { vm.shareEntries(listOf(entry)) },
            onDelete = { vm.trash(listOf(entry.id)); onBack() },
            extraActions = {
                IconButton(onClick = { speedIdx = (speedIdx + 1) % SPEEDS.size }) {
                    Text("${SPEEDS[speedIdx]}x", color = Color.White)
                }
            },
        )
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
            modifier = Modifier.fillMaxWidth(),
            update = { it.player = player },
        )
    }
}

@Composable
internal fun RecordOpen(vm: VaultViewModel, id: Long) {
    androidx.compose.runtime.LaunchedEffect(id) { vm.recordOpen(id) }
}

// ------------------------------------------------------------- audio player

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(vm: VaultViewModel, id: Long, onBack: () -> Unit) {
    val allFiles by vm.allFiles.collectAsState()
    val audios = remember(allFiles) {
        allFiles.filter { !it.isFolder && !it.deleted && it.category == "AUDIO" }.sortedBy { it.relPath }
    }
    val startIndex = audios.indexOfFirst { it.id == id }
    val entry = audios.getOrNull(startIndex)

    if (entry == null || audios.isEmpty()) {
        Box(Modifier.fillMaxSize())
        return
    }

    val context = LocalContext.current
    val player = remember(audios.map { it.id }) {
        ExoPlayer.Builder(context).build().apply {
            addMediaItems(audios.map { MediaItem.fromUri(File(vm.repo.root, it.relPath).toURI().toString()) })
            seekTo(startIndex, 0)
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var loopAll by remember { mutableStateOf(true) }
    var speedIdx by remember { mutableStateOf(1) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onPlaybackStateChanged(state: Int) {
                duration = if (state == Player.STATE_READY) player.duration.coerceAtLeast(0) else 0
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = player.currentPosition.coerceAtLeast(0)
            kotlinx.coroutines.delay(500)
        }
    }
    LaunchedEffect(speedIdx) { player.setPlaybackSpeed(SPEEDS[speedIdx]) }
    RecordOpen(vm, entry.id)

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ViewerTopBar(
                title = entry.name,
                favorite = entry.favorite,
                onBack = onBack,
                onToggleFavorite = { vm.toggleFavorite(entry) },
                onShare = { vm.shareEntries(listOf(entry)) },
                onDelete = { vm.trash(listOf(entry.id)); onBack() },
                extraActions = {
                    IconButton(onClick = { speedIdx = (speedIdx + 1) % SPEEDS.size }) {
                        Text("${SPEEDS[speedIdx]}x", color = Color.White)
                    }
                },
                onInfo = null,
            )

            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            Spacer(Modifier.height(24.dp))

            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    entry.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    "${startIndex + 1} / ${audios.size}",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = position.toFloat(),
                    onValueChange = { player.seekTo(it.toLong()) },
                    valueRange = 0f..(if (duration > 0) duration else 1f).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${Format.millis(position)}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    Text("${Format.millis(duration)}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { loopAll = !loopAll; player.repeatMode = if (loopAll) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF }) {
                    Icon(if (loopAll) Icons.Default.Repeat else Icons.Default.RepeatOne, "Loop", tint = Color.White)
                }
                IconButton(onClick = { player.seekToPreviousMediaItem() }) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White)
                }
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Play/Pause",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                IconButton(onClick = { player.seekToNextMediaItem() }) {
                    Icon(Icons.Default.SkipNext, "Next", tint = Color.White)
                }
                IconButton(onClick = { /* shuffle handled by playlist order */ }) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }
}
