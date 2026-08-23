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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.zaaaam.kalku.fs.scan
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as KalkuApp).container
    val settings = container.settings
    val lock = LockController(container.settings)

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
    }

    fun acceptShareIntent(uris: List<Uri>) {
        if (uris.isNotEmpty()) pendingShareUris.value = uris
    }

    /**
     * Imports shared files once the vault is reachable.
     * Routes each item to the default folder matching its category.
     * Returns count imported (0 if storage not ready).
     */
    suspend fun drainPendingShares(): Int {
        val uris = pendingShareUris.value
        if (uris.isEmpty()) return 0
        val repo = container.repo
        if (!repo.root.isDirectory && !repo.root.mkdirs()) return 0
        var count = 0
        withContext(Dispatchers.IO) {
            val cache = File(getApplication<KalkuApp>().cacheDir, "share").also { it.mkdirs() }
            for (uri in uris) {
                runCatching {
                    // Provider-supplied names are attacker-controllable: sanitize
                    // before building any local path (traversal defense).
                    val safeName = com.zaaaam.kalku.core.Names.sanitizeFileName(repo.displayNameOf(uri))
                    val unique = com.zaaaam.kalku.core.Names.uniqueName(safeName, cache.list()?.toSet() ?: emptySet())
                    val tmp = File(cache, unique)
                    getApplication<KalkuApp>().contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    } ?: return@runCatching
                    val category = com.zaaaam.kalku.core.CategoryDetector.detect(unique)
                    repo.importLocalFile(tmp, destFolderFor(category))?.let { count++ }
                    tmp.delete()
                }
            }
            // Re-index so imported items show up even if rows were inserted while locked.
            runCatching { repo.scan() }
        }
        pendingShareUris.value = emptyList()
        return count
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
