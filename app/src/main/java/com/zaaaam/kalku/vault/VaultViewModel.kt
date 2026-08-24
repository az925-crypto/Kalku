package com.zaaaam.kalku.vault

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zaaaam.kalku.KalkuApp
import com.zaaaam.kalku.core.Category
import com.zaaaam.kalku.core.Names
import com.zaaaam.kalku.data.FileEntity
import com.zaaaam.kalku.data.RecentEntity
import com.zaaaam.kalku.data.TrashEntity
import com.zaaaam.kalku.fs.VaultEncryptionMigrator
import com.zaaaam.kalku.fs.VaultException
import com.zaaaam.kalku.fs.VaultPaths
import com.zaaaam.kalku.fs.ZipUtils
import com.zaaaam.kalku.fs.emptyTrash
import com.zaaaam.kalku.fs.exportTo
import com.zaaaam.kalku.fs.join
import com.zaaaam.kalku.fs.permanentDelete
import com.zaaaam.kalku.fs.purgeExpired
import com.zaaaam.kalku.fs.readText
import com.zaaaam.kalku.fs.restore
import com.zaaaam.kalku.fs.scan
import com.zaaaam.kalku.fs.shareIntent
import com.zaaaam.kalku.fs.shareUri
import com.zaaaam.kalku.fs.trash
import com.zaaaam.kalku.fs.writeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SortBy(val label: String) { NAME("Name"), SIZE("Size"), MODIFIED("Modified"), TYPE("Type") }
data class SortSpec(val by: SortBy = SortBy.NAME, val ascending: Boolean = true)

/**
 * Shared brain for every vault screen. Room flows are the single source of truth;
 * operations mutate disk + DB and UI updates stream back automatically.
 */
class VaultViewModel(app: Application) : AndroidViewModel(app) {

    private val c = (app as KalkuApp).container
    val repo = c.repo
    val settings = c.settings
    private val decCache = c.decCache

    private val migrator = com.zaaaam.kalku.fs.VaultEncryptionMigrator(repo, c.crypto)

    /** Secure Vault migration progress for Settings UI. */
    val migration: StateFlow<com.zaaaam.kalku.fs.VaultEncryptionMigrator.State> = migrator.state

    /** True when this session can decrypt (DEK loaded). */
    fun cryptoActive(): Boolean = repo.isEncrypting()

    /** Plaintext file for display; null when missing or undecryptable. */
    suspend fun plainDisplayFile(relPath: String): File? =
        withContext(Dispatchers.IO) { decCache.plainFile(relPath) }

    /**
     * Toggles Secure Vault. Enabling flips the flag immediately (new writes get
     * encrypted) then encrypts existing files. Disabling decrypts everything
     * first — the flag flips only after a fully successful run.
     *
     * [pin] is required only when enabling on a legacy plaintext vault that has
     * no key material yet (the DEK must be wrapped under a PIN-derived KEK).
     */
    private var migrationJob: kotlinx.coroutines.Job? = null

    fun toggleEncryption(enable: Boolean, pin: String? = null) {
        if (migrator.isRunning) return
        migrationJob = viewModelScope.launch(Dispatchers.IO) {
            if (enable) {
                val root = repo.root
                if (!com.zaaaam.kalku.fs.VaultCryptoStore.hasKeys(root)) {
                    if (pin.isNullOrEmpty()) {
                        showToast("Masukkan PIN untuk membuat kunci enkripsi")
                        return@launch
                    }
                    val created = runCatching {
                        c.crypto.load(com.zaaaam.kalku.fs.VaultCryptoStore.createIfMissing(root, pin))
                        true
                    }.getOrElse { showToast(it.message ?: "Gagal membuat kunci"); false }
                    if (!created) return@launch
                }
                settings.setEncryptionEnabled(true)
                migrator.runSuspend(VaultEncryptionMigrator.Dir.ENCRYPT)
            } else {
                val done = migrator.runSuspend(VaultEncryptionMigrator.Dir.DECRYPT)
                if (done) settings.setEncryptionEnabled(false)
            }
        }
    }

    fun pauseMigration() {
        migrationJob?.cancel()
        migrationJob = null
    }

    fun resumeMigration() {
        if (migrator.isRunning) return
        val paused = migration.value as? VaultEncryptionMigrator.State.Paused
        migrationJob = viewModelScope.launch(Dispatchers.IO) {
            val dir = paused?.direction ?: run {
                // Unknown direction (fresh process): infer from the mode flag.
                if (settings.currentEncryptionEnabled()) VaultEncryptionMigrator.Dir.ENCRYPT else VaultEncryptionMigrator.Dir.DECRYPT
            }
            migrator.runSuspend(dir)
        }
    }

    /** Files still plaintext while the mode is ON (-1 = not computed yet). */
    private val pendingPlainCount = MutableStateFlow(-1)
    val unencryptedPending: StateFlow<Int> = pendingPlainCount

    fun refreshUnencryptedPending() = viewModelScope.launch(Dispatchers.IO) {
        pendingPlainCount.value =
            migrator.collectTargets(com.zaaaam.kalku.fs.VaultEncryptionMigrator.Dir.ENCRYPT).size
    }

    /** True when full storage access granted and root usable. */
    fun storageFullAccess(): Boolean = VaultPaths.hasFullAccess(getApplication())

    private val filesFlow = repo.fileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allFiles: StateFlow<List<FileEntity>> = filesFlow

    // -------------------------------------------------------------- browsing

    private val folderRel = MutableStateFlow("")

    fun openFolder(rel: String) { folderRel.value = rel }

    /** Finds a live file row by relative path (used by Recent list). */
    fun byPathThenOpen(relPath: String): FileEntity? =
        allFiles.value.firstOrNull { it.relPath == relPath && !it.deleted }

    val children: StateFlow<List<FileEntity>> =
        combine(filesFlow, folderRel) { list, f -> list.filter { it.parent == f } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --------------------------------------------------------------- library

    val favorites: StateFlow<List<FileEntity>> = filesFlow
        .map { list -> list.filter { it.favorite && !it.deleted } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val images: StateFlow<List<FileEntity>> = filesFlow
        .map { list ->
            list.filter { !it.isFolder && !it.deleted && it.category == Category.IMAGE.name }
                .sortedByDescending { it.modifiedAt }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recents: StateFlow<List<RecentEntity>> = repo.recentDao().observeRecent(20)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val trashItems: StateFlow<List<TrashEntity>> = repo.trashDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    data class CategoryStat(val category: Category, val count: Int, val size: Long)

    val stats: StateFlow<List<CategoryStat>> = filesFlow
        .map { list ->
            Category.entries.map { cat ->
                val items = list.filter { it.category == cat.name && !it.deleted && !it.isFolder }
                CategoryStat(cat, items.size, items.sumOf { it.size })
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totalSize: StateFlow<Long> = filesFlow
        .map { list -> list.filter { !it.deleted }.sumOf { it.size } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // ---------------------------------------------------------------- search

    data class SearchFilter(
        val query: String = "",
        val category: Category? = null,
        val favoriteOnly: Boolean = false,
    )

    fun search(filter: SearchFilter): List<FileEntity> {
        val q = filter.query.trim().lowercase()
        return allFiles.value.asSequence()
            .filter { !it.deleted }
            .filter { filter.category == null || it.category == filter.category.name }
            .filter { !filter.favoriteOnly || it.favorite }
            .filter { f ->
                q.isEmpty() ||
                    f.name.lowercase().contains(q) ||
                    f.relPath.lowercase().contains(q) ||
                    f.tags.lowercase().split(',').any { it.trim().startsWith(q) && it.isNotBlank() }
            }
            .toList()
    }

    // ------------------------------------------------------------------- ops

    /** Identified payload: identical messages in a row still re-trigger the snackbar. */
    data class ToastEvent(val id: Long, val msg: String)

    private val _toast = MutableStateFlow<ToastEvent?>(null)
    val toast: StateFlow<ToastEvent?> = _toast

    fun showToast(msg: String) { _toast.value = ToastEvent(System.nanoTime(), msg) }

    private fun fail(e: Exception) { showToast(e.message ?: "Operation failed") }

    fun dismissToast() { _toast.value = null }

    fun launchIntent(intent: Intent) = viewModelScope.launch {
        withContext(Dispatchers.Main) {
            runCatching {
                getApplication<Application>().startActivity(
                    Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { fail(Exception("Tidak ada aplikasi untuk aksi ini")) }
        }
    }

    fun runIo(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e)
        }
    }

    /** Builds the share chooser off-thread, then fires it on the main thread. */
    fun shareEntries(entries: List<FileEntity>) = viewModelScope.launch {
        val intent = withContext(Dispatchers.IO) {
            try {
                if (entries.any { it.isFolder }) {
                    showToast("Folders cannot be shared directly — zip them first")
                    null
                } else {
                    val uris = entries.map { repo.shareUri(it.relPath) }
                    val mime = entries.singleOrNull()?.mime?.takeIf { it.isNotEmpty() } ?: "*/*"
                    repo.shareIntent(uris, mime)
                }
            } catch (e: Exception) {
                fail(e); null
            }
        }
        intent?.let { launchIntent(it) }
    }

    fun import(uris: List<Uri>, destParent: String) = runIo {
        val created = repo.importUris(uris, destParent)
        showToast("${created.size} file(s) imported")
    }

    fun createFolder(parent: String, name: String) = runIo { repo.createFolder(parent, name) }

    fun createTextFile(parent: String, name: String, content: String = "") = runIo {
        repo.createTextFile(parent, name, content)
    }

    fun rename(id: Long, newName: String) = runIo { repo.rename(id, newName) }

    fun move(ids: List<Long>, destParent: String) = runIo { repo.move(ids, destParent) }

    fun copy(ids: List<Long>, destParent: String) = runIo { repo.copy(ids, destParent) }

    fun trash(ids: List<Long>) = runIo { repo.trash(ids) }

    fun restore(trashId: Long) = runIo { repo.restore(trashId) }

    fun permanentDelete(trashId: Long) = runIo { repo.permanentDelete(trashId) }

    fun emptyTrash() = runIo { repo.emptyTrash(); showToast("Trash emptied") }

    fun purgeExpired(days: Int) = runIo { repo.purgeExpired(days) }

    fun toggleFavorite(entry: FileEntity) = runIo {
        repo.fileDao.setFavorite(entry.id, !entry.favorite)
    }

    fun setTags(entry: FileEntity, tags: List<String>) = runIo {
        repo.fileDao.setTags(entry.id, tags.joinToString(",") { it.trim() })
    }

    fun recordOpen(id: Long) = runIo { repo.recordOpen(id) }

    fun export(entry: FileEntity, destUri: Uri) = runIo {
        require(!entry.isFolder) { "Export folders as ZIP instead" }
        repo.exportTo(entry.relPath, destUri)
        showToast("Exported")
    }

    fun readTextSafe(relPath: String): String? =
        try { repo.readText(relPath) } catch (e: Exception) { fail(e); null }

    /** Writes text back; returns false (after toasting) so callers can keep their dirty flag. */
    suspend fun writeTextSafe(relPath: String, content: String): Boolean =
        try {
            repo.writeText(relPath, content)
            true
        } catch (e: Exception) {
            fail(e); false
        }

    /** True when the file on disk carries the encrypted-vault header. */
    fun isPathEncrypted(relPath: String): Boolean =
        com.zaaaam.kalku.core.crypto.VaultFileFormat.isEncrypted(repo.fileOf(relPath))

    fun saveTextAs(parent: String, rawName: String, content: String, onSaved: (FileEntity) -> Unit) = runIo {
        // Exact name: silently uniquifying here produced surprise duplicates
        // ("note (2).txt") on every subsequent save.
        val entity = repo.createTextFileExact(parent, rawName, content)
        onSaved(entity)
    }

    /** Compresses selected entries into a new ZIP inside [destParent].
     *  Sources are staged as plaintext first (via the decrypted cache) so
     *  ciphertext is never zipped; the result goes through the normal import
     *  pipeline (encrypt + index). The cache file is zipped directly — no
     *  second plaintext copy is made. */
    fun zipEntries(entries: List<FileEntity>, destParent: String, rawZipName: String) = runIo {
        require(entries.isNotEmpty()) { "Nothing selected" }
        withContext(Dispatchers.IO) {
            val stage = File(getApplication<KalkuApp>().cacheDir, "zip_stage").also { it.mkdirs() }
            // Honor the requested name; uniquify per run so concurrent zips and
            // importLocalFile collisions never overwrite each other.
            val base = Names.sanitizeFileName(rawZipName.ifBlank { "archive" }.removeSuffix(".zip")).ifBlank { "archive" }
            val tmpZip = File(stage, "$base-${System.currentTimeMillis()}.zip")
            try {
                val staged = mutableListOf<File>()
                for (entry in entries) {
                    val src = repo.fileOf(entry.relPath)
                    if (!src.exists()) continue
                    when {
                        entry.isFolder -> {
                            val destFolder = File(stage, "${entry.id}_${entry.name}").also { it.mkdirs() }
                            stageTreeDecrypted(src, entry.relPath, destFolder)
                            staged += destFolder
                        }
                        else -> staged += decCache.plainFile(entry.relPath)
                            ?: throw VaultException("Tidak bisa dekripsi ${entry.name}")
                    }
                }
                ZipUtils.compress(staged, tmpZip)
                val finalName = repo.importLocalFile(tmpZip, destParent)
                showToast(finalName?.let { "ZIP created: $it" } ?: "Gagal membuat ZIP")
            } finally {
                stage.deleteRecursively()
            }
        }
    }

    /** Extracts an archive into a sibling folder named after it. */
    fun extractArchive(archiveId: Long) = runIo {
        val archive = repo.byId(archiveId) ?: error("Not found")
        val targetRel = archive.relPath.removeSuffix(".zip").removeSuffix(".ZIP")
        withContext(Dispatchers.IO) {
            val plainZip = decCache.plainFile(archive.relPath)
            if (plainZip == null || !plainZip.exists()) {
                throw VaultException("Tidak bisa dekripsi ${archive.name}")
            }
            // Extract outside the vault, then import through the normal pipeline:
            // folder rows, transparent re-encryption and correct sizes.
            val tmpExtract = File(getApplication<KalkuApp>().cacheDir, "extract_${System.currentTimeMillis()}")
            try {
                val n = ZipUtils.extractAll(plainZip, tmpExtract)
                repo.importTree(tmpExtract, targetRel)
                showToast("$n file(s) extracted")
            } finally {
                tmpExtract.deleteRecursively()
            }
        }
    }

    /** Stages [srcDirOnDisk] (vault folder [srcVaultRel]) as plaintext under [destDir]. */
    private fun stageTreeDecrypted(srcDirOnDisk: File, srcVaultRel: String, destDir: File) {
        srcDirOnDisk.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { f ->
            val dst = File(destDir, f.name)
            if (f.isDirectory) {
                dst.mkdirs()
                stageTreeDecrypted(f, com.zaaaam.kalku.fs.join(srcVaultRel, f.name), dst)
            } else {
                val plain = decCache.plainFile(com.zaaaam.kalku.fs.join(srcVaultRel, f.name))
                    ?: throw VaultException("Tidak bisa dekripsi ${f.name}")
                plain.inputStream().use { input -> dst.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    fun rebuildIndex() = runIo {
        val n = repo.scan()
        showToast("Index rebuilt: $n files indexed")
    }

    companion object {
        fun sortEntries(entries: List<FileEntity>, spec: SortSpec): List<FileEntity> {
            val cmp: Comparator<FileEntity> = when (spec.by) {
                SortBy.NAME -> compareBy { it.name.lowercase() }
                SortBy.SIZE -> compareBy { it.size }
                SortBy.MODIFIED -> compareBy { it.modifiedAt }
                SortBy.TYPE -> compareBy({ it.category }, { it.name.lowercase() })
            }
            return entries.sortedWith(
                if (spec.ascending) cmp else cmp.reversed()
            ).sortedByDescending { it.isFolder }
        }
    }
}

suspend fun VaultViewModel.byIds(ids: List<Long>): List<FileEntity> =
    ids.mapNotNull { repo.byId(it) }
