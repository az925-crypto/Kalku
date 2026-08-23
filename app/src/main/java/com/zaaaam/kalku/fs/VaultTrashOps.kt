package com.zaaaam.kalku.fs

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.zaaaam.kalku.core.CategoryDetector
import com.zaaaam.kalku.core.Names
import com.zaaaam.kalku.data.FileEntity
import com.zaaaam.kalku.data.TrashEntity
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

/** Recycle-bin, share/export, text I/O and index-rebuilding operations. */

/** Virtual relPath prefix for binned items; keeps the original path reusable. */
internal const val TRASH_VIRTUAL_PREFIX = ".Trash/"

// ---------------------------------------------------------------- recycle bin

suspend fun VaultRepo.trash(ids: List<Long>) {
    val trashDir = VaultPaths.trashDir(root).also { it.mkdirs() }
    for (id in ids) {
        val entry = fileDao.byId(id) ?: continue
        if (entry.deleted || entry.relPath.isEmpty()) continue
        assertInside(fileOf(entry.relPath))

        var trashName = ""
        val src = fileOf(entry.relPath)
        if (src.exists()) {
            val ext = CategoryDetector.extensionOf(entry.name)
            trashName = UUID.randomUUID().toString() + if (ext.isEmpty()) "" else ".$ext"
            if (!src.renameTo(File(trashDir, trashName))) throw VaultException("Delete failed for ${entry.name}")
        }
        // Metadata is kept but repathed under the virtual bin prefix so the
        // original relPath becomes immediately reusable without conflicts.
        fileDao.markDeleted(entry.relPath, likeEscaped(entry.relPath))
        val affected = fileDao.allDeleted().filter {
            it.relPath == entry.relPath || it.relPath.startsWith("${entry.relPath}/")
        }
        val virtualTop = TRASH_VIRTUAL_PREFIX + trashName
        for (r in affected) {
            if (trashName.isNotEmpty()) {
                val sub = r.relPath.removePrefix(entry.relPath)
                val virtual = virtualTop + sub
                fileDao.repathEntry(r.id, virtual, virtual.substringBeforeLast('/', ""), virtual.substringAfterLast('/'))
            }
        }
        trashDao().insert(
            TrashEntity(
                trashName = trashName,
                name = entry.name,
                originalRelPath = entry.relPath,
                originalParent = entry.parent,
                isFolder = entry.isFolder,
                category = entry.category,
                size = entry.size,
                deletedAt = System.currentTimeMillis(),
            )
        )
    }
}

suspend fun VaultRepo.restore(trashId: Long) {
    val row = trashDao().byId(trashId) ?: return
    val destDir = fileOf(row.originalParent).also { it.mkdirs() }
    val taken = destDir.list()?.toSet() ?: emptySet()
    val finalName = Names.uniqueName(row.name, taken)
    val dest = File(destDir, finalName)

    val src = if (row.trashName.isNotEmpty()) File(VaultPaths.trashDir(root), row.trashName) else null
    if (src != null && src.exists()) {
        assertInside(dest)
        if (!src.renameTo(dest)) throw VaultException("Restore failed")
    }

    val newPath = join(row.originalParent, finalName)
    if (row.trashName.isNotEmpty()) {
        val curPrefix = TRASH_VIRTUAL_PREFIX + row.trashName
        for (r in fileDao.allDeleted()) {
            val mapped = when {
                r.relPath == curPrefix -> newPath
                r.relPath.startsWith("$curPrefix/") -> newPath + r.relPath.removePrefix(curPrefix)
                else -> null
            } ?: continue
            fileDao.repathEntry(r.id, mapped, mapped.substringBeforeLast('/', ""), mapped.substringAfterLast('/'))
        }
        fileDao.markAlive(newPath, likeEscaped(newPath))
    } else {
        // Legacy/meta-only row that never moved on disk.
        fileDao.markAlive(row.originalRelPath, likeEscaped(row.originalRelPath))
    }
    trashDao().delete(trashId)
}

suspend fun VaultRepo.permanentDelete(trashId: Long) {
    val row = trashDao().byId(trashId) ?: return
    if (row.trashName.isNotEmpty()) {
        val f = File(VaultPaths.trashDir(root), row.trashName)
        if (f.exists()) f.deleteRecursively()
        fileDao.deleteTree(
            TRASH_VIRTUAL_PREFIX + row.trashName,
            likeEscaped(TRASH_VIRTUAL_PREFIX + row.trashName),
        )
    } else {
        fileDao.deleteTree(row.originalRelPath, likeEscaped(row.originalRelPath))
    }
    trashDao().delete(trashId)
}

suspend fun VaultRepo.emptyTrash(): Int {
    val items = trashDao().observeAll().first()
    items.forEach { permanentDelete(it.id) }
    return items.size
}

/** Deletes trashed items older than [retentionDays] days (<= 0 disables auto-clean). */
suspend fun VaultRepo.purgeExpired(retentionDays: Int): Int {
    if (retentionDays <= 0) return 0
    val cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L
    val old = trashDao().observeAll().first().filter { it.deletedAt < cutoff }
    old.forEach { permanentDelete(it.id) }
    return old.size
}

// ---------------------------------------------------------------- share/export


/** Content URI for sharing; stages through cache when running on fallback storage. */
fun VaultRepo.shareUri(relPath: String): Uri {
    val f = fileOf(relPath)
    require(f.isFile && f.exists()) { "Not a readable file" }
    return if (storage.isFallback) {
        val stage = File(appContext.cacheDir, "share").also { it.mkdirs() }
        val staged = File(stage, f.name)
        f.copyTo(staged, overwrite = true)
        FileProvider.getUriForFile(appContext, appContext.packageName + ".fileprovider", staged)
    } else {
        FileProvider.getUriForFile(appContext, appContext.packageName + ".fileprovider", f)
    }
}

fun VaultRepo.shareIntent(uris: List<Uri>, mime: String): Intent {
    val i = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        type = if (uris.size == 1) mime else "*/*"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(i, null)
}

/** Streams one vault file out to a SAF document [destUri] (Export / Save copy). */
fun VaultRepo.exportTo(relPath: String, destUri: Uri) {
    val src = fileOf(relPath)
    require(src.isFile) { "Not a file" }
    appContext.contentResolver.openOutputStream(destUri)?.use { out ->
        src.inputStream().use { it.copyTo(out) }
    } ?: throw VaultException("Cannot open destination")
}

// ------------------------------------------------------------------- text I/O

fun VaultRepo.readText(relPath: String): String {
    val f = fileOf(relPath)
    require(f.isFile && f.exists()) { "Not a readable file" }
    return String(f.readBytes(), Charsets.UTF_8)
}

/** Writes text content back, refreshing size/mtime metadata. */
suspend fun VaultRepo.writeText(relPath: String, content: String) {
    val f = fileOf(relPath)
    assertInside(f)
    f.writeText(content)
    fileDao.updateStat(relPath, f.length(), System.currentTimeMillis())
}

// ------------------------------------------------------------- index rebuild

/**
 * Walks the physical vault tree and rebuilds the metadata index.
 * Trashed rows are kept; rows whose files vanished outside the bin are dropped.
 * Returns the number of indexed files.
 */
suspend fun VaultRepo.scan(): Int {
    root.mkdirs()
    VaultPaths.trashDir(root).mkdirs()
    val now = System.currentTimeMillis()
    val indexed = mutableListOf<FileEntity>()
    val skipNames = setOf(VaultPaths.TRASH_DIR, ".meta")

    fun walk(dir: File, dirRel: String) {
        dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.forEach { f ->
                if (dirRel.isEmpty() && f.name in skipNames) return@forEach
                val rel = join(dirRel, f.name)
                if (f.isDirectory) {
                    indexed += folderEntity(rel, dirRel)
                    walk(f, rel)
                } else {
                    val cat = CategoryDetector.detect(f.name, readHeader(f))
                    indexed += FileEntity(
                        relPath = rel,
                        name = f.name,
                        parent = dirRel,
                        isFolder = false,
                        category = cat.name,
                        mime = CategoryDetector.mimeOf(cat, f.name),
                        size = f.length(),
                        createdAt = now,
                        modifiedAt = now,
                    )
                }
            }
    }
    walk(root, "")

    // Preserve user metadata and stable ids across rescans: reuse the existing
    // row id so REPLACE updates in place instead of deleting + reinserting.
    val existing = fileDao.all().associateBy { it.relPath }
    val preserved = indexed.map { e ->
        existing[e.relPath]?.let { old ->
            e.copy(id = old.id, favorite = old.favorite, tags = old.tags, createdAt = old.createdAt)
        } ?: e
    }
    fileDao.upsertAll(preserved)
    val walkedPaths = indexed.map { it.relPath }.toSet()
    val stale = existing.values.filter { it.relPath !in walkedPaths }.map { it.relPath }
    if (stale.isNotEmpty()) fileDao.deleteByPaths(stale)
    recentDao().pruneOrphans()
    return indexed.count { !it.isFolder }
}
