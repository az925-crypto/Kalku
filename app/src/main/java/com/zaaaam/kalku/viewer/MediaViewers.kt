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
import com.zaaaam.kalku.data.FileEntity
import com.zaaaam.kalku.vault.VaultViewModel
import java.io.File

private val SPEEDS = listOf(0.5f, 1f, 1.5f, 2f)

// ------------------------------------------------------------- video player

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(vm: VaultViewModel, id: Long, onBack: () -> Unit) {
    val entry = EntryGate(vm, id, onBack)
    val context = LocalContext.current

    if (entry == null) {
        return
    }

    // Encrypted vault files need a decrypted copy before ExoPlayer can play.
    var mediaFile by remember(entry.id) { mutableStateOf<File?>(null) }
    androidx.compose.runtime.LaunchedEffect(entry.id) {
        mediaFile = vm.plainDisplayFile(entry.relPath)
    }

    val player = mediaFile?.let { f ->
        remember(f) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(f.toURI().toString()))
                prepare()
                playWhenReady = true
            }
        }
    }
    DisposableEffect(player) { onDispose { player?.release() } }

    if (mediaFile == null) {
        Column(Modifier.fillMaxSize()) {
            ViewerTopBar(title = entry.name, favorite = entry.favorite, onBack = onBack,
                onToggleFavorite = { vm.toggleFavorite(entry) }, onShare = {}, onDelete = {})
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Membuka video…", color = Color.White.copy(alpha = 0.6f))
            }
        }
        return
    }

    var speedIdx by remember { mutableStateOf(1) }
    LaunchedEffect(speedIdx) { player!!.setPlaybackSpeed(SPEEDS[speedIdx]) }
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
    val listLoaded = allFiles.isNotEmpty()
    val audios = remember(allFiles) {
        allFiles.filter { !it.isFolder && !it.deleted && it.category == "AUDIO" }.sortedBy { it.relPath }
    }

    // Tracks are resolved ONE at a time: pre-decrypting the entire playlist
    // blocked the screen for seconds on encrypted libraries and thrashed the
    // decrypted cache.
    var currentIndex by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(-1) }
    LaunchedEffect(audios) {
        if (currentIndex == -1 && audios.isNotEmpty()) {
            val found = audios.indexOfFirst { it.id == id }
            if (found >= 0) currentIndex = found
        }
    }
    if (currentIndex == -1) {
        if (listLoaded && audios.none { it.id == id }) {
            // Index fully loaded (or no audio at all): genuinely missing.
            MissingEntryScreen(onBack)
        } else {
            EntryLoadingScreen(onBack)
        }
        return
    }
    val entry = audios[currentIndex]

    // Resolve plaintext only for the track that will actually play.
    var currentFile by remember(entry.id) { mutableStateOf<File?>(null) }
    LaunchedEffect(entry.id, entry.relPath) {
        currentFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            vm.plainDisplayFile(entry.relPath)
        }
    }

    val context = LocalContext.current
    val player = currentFile?.let { f ->
        remember(f) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(f.toURI().toString()))
                prepare()
                playWhenReady = false
            }
        }
    }
    DisposableEffect(player) { onDispose { player?.release() } }

    if (player == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var loopAll by remember { mutableStateOf(true) }
    var speedIdx by remember { mutableStateOf(1) }
    var shuffleOn by remember { mutableStateOf(player.shuffleModeEnabled) }
    // Local drag state so the thumb follows the finger even while paused
    // (the 500ms position poller only runs while playing).
    var isSeeking by remember { mutableStateOf(false) }
    var seekPos by remember { mutableLongStateOf(0L) }

    fun jumpToRandom() {
        if (audios.size <= 1) return
        var n = currentIndex
        while (n == currentIndex) n = kotlin.random.Random.nextInt(audios.size)
        currentIndex = n
    }
    fun stepBy(delta: Int) {
        if (audios.isEmpty()) return
        currentIndex = (currentIndex + delta).mod(audios.size)
    }
    fun advanceTrack() = if (shuffleOn) jumpToRandom() else stepBy(1)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onPlaybackStateChanged(state: Int) {
                duration = if (state == Player.STATE_READY) player.duration.coerceAtLeast(0) else 0
                // Playlist advance is manual (single-item media): on track end,
                // loop-all moves to the next/shuffled entry, otherwise stop.
                if (state == Player.STATE_ENDED && loopAll && audios.size > 1) advanceTrack()
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
    // Keep the displayed position fresh when paused (e.g. right after a seek);
    // bounded polling instead of an endless while(true).
    LaunchedEffect(Unit) {
        while (true) {
            if (!isPlaying && !isSeeking) position = player.currentPosition.coerceAtLeast(0)
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
                    "${currentIndex + 1} / ${audios.size}",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = (if (isSeeking) seekPos else position).toFloat(),
                    onValueChange = {
                        isSeeking = true
                        seekPos = it.toLong()
                    },
                    onValueChangeFinished = {
                        player.seekTo(seekPos)
                        position = seekPos
                        isSeeking = false
                    },
                    valueRange = 0f..(if (duration > 0) duration else 1f).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${Format.millis(if (isSeeking) seekPos else position)}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    Text("${Format.millis(duration)}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { loopAll = !loopAll }) {
                    Icon(if (loopAll) Icons.Default.Repeat else Icons.Default.RepeatOne, "Loop", tint = Color.White)
                }
                IconButton(onClick = { if (shuffleOn) jumpToRandom() else stepBy(-1) }) {
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
                IconButton(onClick = { advanceTrack() }) {
                    Icon(Icons.Default.SkipNext, "Next", tint = Color.White)
                }
                IconButton(onClick = {
                    shuffleOn = !shuffleOn
                    // ExoPlayer no longer owns the playlist; shuffle is manual.
                    player.shuffleModeEnabled = false
                }) {
                    Icon(
                        Icons.Default.Shuffle,
                        "Shuffle",
                        tint = if (shuffleOn) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
