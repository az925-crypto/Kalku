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

    val toast = MutableStateFlow<String?>(null)

    private fun fail(e: Exception) { toast.value = e.message ?: "Operation failed" }

    fun dismissToast() { toast.value = null }

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
        try { block() } catch (e: Exception) { fail(e) }
    }

    /** Builds the share chooser off-thread, then fires it on the main thread. */
    fun shareEntries(entries: List<FileEntity>) = viewModelScope.launch {
        val intent = withContext(Dispatchers.IO) {
            try {
                if (entries.any { it.isFolder }) {
                    toast.value = "Folders cannot be shared directly — zip them first"
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
        toast.value = "${created.size} file(s) imported"
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

    fun emptyTrash() = runIo { repo.emptyTrash(); toast.value = "Trash emptied" }

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
        toast.value = "Exported"
    }

    fun readTextSafe(relPath: String): String? =
        try { repo.readText(relPath) } catch (e: Exception) { fail(e); null }

    fun writeTextSafe(relPath: String, content: String) = runIo { repo.writeText(relPath, content) }

    fun saveTextAs(parent: String, rawName: String, content: String, onSaved: (Long) -> Unit) = runIo {
        val entity = repo.createTextFile(parent, rawName, content)
        onSaved(entity.id)
    }

    /** Compresses selected entries into a new ZIP inside [destParent]. */
    fun zipEntries(entries: List<FileEntity>, destParent: String, rawZipName: String) = runIo {
        require(entries.isNotEmpty()) { "Nothing selected" }
        val destDir = repo.fileOf(destParent)
        val taken = destDir.list()?.toSet() ?: emptySet()
        val finalName = Names.uniqueName(rawZipName.ifBlank { "archive" }.removeSuffix(".zip") + ".zip", taken)
        val outFile = File(destDir, finalName)
        val sources = entries.map { repo.fileOf(it.relPath) }
        withContext(Dispatchers.IO) { ZipUtils.compress(sources, outFile) }
        repo.insertFileRow(outFile, destParent, ByteArray(0))
        toast.value = "ZIP created: $finalName"
    }

    /** Extracts an archive into a sibling folder named after it. */
    fun extractArchive(archiveId: Long) = runIo {
        val archive = repo.byId(archiveId) ?: error("Not found")
        val zipFile = repo.fileOf(archive.relPath)
        val targetRel = archive.relPath.removeSuffix(".zip").removeSuffix(".ZIP")
        val target = repo.fileOf(targetRel)
        val n = withContext(Dispatchers.IO) { ZipUtils.extractAll(zipFile, target) }
        val parent = targetRel.substringBeforeLast('/', "")
        repo.fileDao.upsert(
            FileEntity(
                relPath = targetRel,
                name = targetRel.substringAfterLast('/'),
                parent = parent,
                isFolder = true,
                category = Category.OTHER.name,
                mime = "",
                size = 0,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
            )
        )
        repo.indexTreeUnder(target, targetRel)
        toast.value = "$n file(s) extracted"
    }

    fun rebuildIndex() = runIo {
        val n = repo.scan()
        toast.value = "Index rebuilt: $n files indexed"
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
