package com.zaaaam.kalku

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zaaaam.kalku.core.Category
import com.zaaaam.kalku.data.ThemeMode
import com.zaaaam.kalku.fs.VaultPaths
import com.zaaaam.kalku.security.LockController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.zaaaam.kalku.fs.purgeExpired
import com.zaaaam.kalku.fs.scan
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as KalkuApp).container
    val settings = container.settings

    // The lock controller owns THE process-wide CryptoSession (same instance as
    // repo/decCache/migrator) so unlock loads and lock wipes the one true key.
    val lock = LockController(container.settings, container.crypto) {
        runCatching { container.repo.root }.getOrNull()
    }

    /** URIs received via share intents, waiting to be imported into the vault. */
    val pendingShareUris = MutableStateFlow<List<Uri>>(emptyList())

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    @Suppress("unused")
    val accent: StateFlow<String> = settings.accent
        .stateIn(viewModelScope, SharingStarted.Eagerly, "teal")

    val themePack: StateFlow<com.zaaaam.kalku.ui.theme.ThemePack> = settings.themePack
        .map { raw ->
            runCatching { com.zaaaam.kalku.ui.theme.ThemePack.valueOf(raw) }
                .getOrDefault(com.zaaaam.kalku.ui.theme.ThemePack.PRECISION)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.zaaaam.kalku.ui.theme.ThemePack.PRECISION)

    init {
        viewModelScope.launch {
            lock.load()
            runCatching { container.repo.ensureStructure() }
        }
        // Crash hygiene: a killed process can leave decrypted leftovers behind
        // (viewer cache, share staging, half-written migration temps). None of
        // them can be in active use at startup, so drop them all.
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.decCache.wipeAll() }
            runCatching { File(getApplication<KalkuApp>().cacheDir, "share").deleteRecursively() }
            runCatching {
                container.repo.root.walkTopDown()
                    .filter { it.isFile && (it.name.endsWith(".kmig") || it.name.endsWith(".part")) }
                    .forEach { it.delete() }
            }
        }
        // Session transitions: auto-clean trash on unlock; drop decrypted
        // viewer caches on lock so plaintext copies never outlive the session.
        viewModelScope.launch {
            androidx.compose.runtime.snapshotFlow { lock.unlocked }.collect { unlocked ->
                if (unlocked) {
                    val days = settings.trashRetentionDays.first()
                    withContext(Dispatchers.IO) {
                        runCatching { container.repo.purgeExpired(days) }
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        runCatching { container.decCache.wipeAll() }
                        // Also clear share-staging leftovers from previous sessions.
                        runCatching { File(getApplication<KalkuApp>().cacheDir, "share").deleteRecursively() }
                    }
                }
            }
        }
    }

    fun acceptShareIntent(uris: List<Uri>) {
        if (uris.isNotEmpty()) pendingShareUris.value = uris
    }

    private val drainMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Imports shared files once the vault is reachable.
     * Routes each item to the default folder matching its category.
     * Returns count imported (0 if storage not ready).
     *
     * Single-flight + consume-before-import: onStart and the nav-level collector
     * can both call this; without the mutex the same URI list could double-import,
     * and a share arriving mid-drain could be wiped by the trailing clear.
     */
    suspend fun drainPendingShares(): Int = drainMutex.withLock {
        val uris = pendingShareUris.value
        if (uris.isEmpty()) return 0
        pendingShareUris.value = emptyList() // consumed up-front, atomically
        val repo = container.repo
        if (!repo.root.isDirectory && !repo.root.mkdirs()) {
            // Give back without clobbering shares accepted during the drain.
            pendingShareUris.value = uris + pendingShareUris.value
            return 0
        }
        var count = 0
        withContext(Dispatchers.IO) {
            val cache = File(getApplication<KalkuApp>().cacheDir, "share").also { it.mkdirs() }
            for (uri in uris) {
                try {
                    // Provider-supplied names are attacker-controllable: sanitize
                    // before building any local path (traversal defense).
                    val safeName = com.zaaaam.kalku.core.Names.sanitizeFileName(repo.displayNameOf(uri))
                    val unique = com.zaaaam.kalku.core.Names.uniqueName(safeName, cache.list()?.toSet() ?: emptySet())
                    val tmp = File(cache, unique)
                    try {
                        getApplication<KalkuApp>().contentResolver.openInputStream(uri)?.use { input ->
                            tmp.outputStream().use { input.copyTo(it) }
                        } ?: continue
                        val category = com.zaaaam.kalku.core.CategoryDetector.detect(unique)
                        repo.importLocalFile(tmp, destFolderFor(category))?.let { count++ }
                    } finally {
                        tmp.delete()
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Skip unreadable provider entries but keep importing the rest.
                }
            }
            // No full re-scan here: importLocalFile already inserts index rows.
        }
        count
    }

    private fun destFolderFor(category: Category): String =
        VaultPaths.DEFAULT_FOLDERS.firstOrNull { it.equals(category.label, ignoreCase = true) } ?: "Others"

    companion object {
        fun extractSharedUris(intent: android.content.Intent?): List<Uri> {
            intent ?: return emptyList()
            return when (intent.action) {
                android.content.Intent.ACTION_SEND ->
                    listOfNotNull(intent.getParcelableExtra<Uri>(android.content.Intent.EXTRA_STREAM))
                android.content.Intent.ACTION_SEND_MULTIPLE ->
                    intent.getParcelableArrayListExtra<Uri>(android.content.Intent.EXTRA_STREAM).orEmpty()
                else -> emptyList()
            }.filterNotNull()
        }
    }
}
