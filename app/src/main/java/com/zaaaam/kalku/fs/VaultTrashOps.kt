package com.zaaaam.kalku.fs

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.zaaaam.kalku.core.CategoryDetector
import com.zaaaam.kalku.core.Names
import com.zaaaam.kalku.data.FileEntity
import com.zaaaam.kalku.data.TrashEntity
import kotlinx.coroutines.flow.first
import java.io.ByteArrayInputStream
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
        // One transaction: a crash mid-way must not leave rows half-deleted
        // with no TrashEntity to restore from.
        db.withTransaction {
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
        notifyMutated(entry.relPath)
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
        db.withTransaction {
            for (r in fileDao.allDeleted()) {
                val mapped = when {
                    r.relPath == curPrefix -> newPath
                    r.relPath.startsWith("$curPrefix/") -> newPath + r.relPath.removePrefix(curPrefix)
                    else -> null
                } ?: continue
                fileDao.repathEntry(r.id, mapped, mapped.substringBeforeLast('/', ""), mapped.substringAfterLast('/'))
            }
            fileDao.markAlive(newPath, likeEscaped(newPath))
        }
        notifyMutated(curPrefix)
    } else {
        // Legacy/meta-only row that never moved on disk.
        db.withTransaction {
            fileDao.markAlive(row.originalRelPath, likeEscaped(row.originalRelPath))
        }
        notifyMutated(row.originalRelPath)
    }
    trashDao().delete(trashId)
}

suspend fun VaultRepo.permanentDelete(trashId: Long) {
    val row = trashDao().byId(trashId) ?: return
    if (row.trashName.isNotEmpty()) {
        val f = File(VaultPaths.trashDir(root), row.trashName)
        if (f.exists()) f.deleteRecursively()
        db.withTransaction {
            fileDao.deleteTree(
                TRASH_VIRTUAL_PREFIX + row.trashName,
                likeEscaped(TRASH_VIRTUAL_PREFIX + row.trashName),
            )
            trashDao().delete(trashId)
        }
        notifyMutated(TRASH_VIRTUAL_PREFIX + row.trashName)
    } else {
        // Legacy/meta-only row that never moved on disk. Only delete rows still
        // marked deleted — a live file may have reused this path in the meantime.
        db.withTransaction {
            fileDao.deleteTreeIfDeleted(row.originalRelPath, likeEscaped(row.originalRelPath))
            trashDao().delete(trashId)
        }
        notifyMutated(row.originalRelPath)
    }
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


/** Content URI for sharing; always stages decrypted copies through cache when
 *  the file is encrypted or running on fallback storage. */
fun VaultRepo.shareUri(relPath: String): Uri {
    val f = fileOf(relPath)
    require(f.isFile && f.exists()) { "Not a readable file" }
    val needsStage = storage.isFallback ||
        com.zaaaam.kalku.core.crypto.VaultFileFormat.isEncrypted(f)
    return if (needsStage) {
        val stage = File(appContext.cacheDir, "share").also { it.mkdirs() }
        // Multi-select can include same-named files from different folders:
        // staging with the raw name would silently overwrite an earlier copy
        // and share the wrong content.
        val uniqueName = Names.uniqueName(f.name, stage.list()?.toSet() ?: emptySet())
        val staged = File(stage, uniqueName)
        plainStream(f).use { input ->
            staged.outputStream().use { input.copyTo(it) }
        }
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
        plainStream(src).use { it.copyTo(out) }
    } ?: throw VaultException("Cannot open destination")
}

// ------------------------------------------------------------------- text I/O

fun VaultRepo.readText(relPath: String): String {
    val f = fileOf(relPath)
    require(f.isFile && f.exists()) { "Not a readable file" }
    return plainStream(f).use { String(it.readBytes(), Charsets.UTF_8) }
}

/** Writes text content back, refreshing size/mtime metadata.
 *  Atomic: writes a sibling temp then renames over the target, so a process
 *  death mid-write can never truncate (and thus corrupt) the original. */
suspend fun VaultRepo.writeText(relPath: String, content: String) {
    val f = fileOf(relPath)
    assertInside(f)
    // writeThrough's downgrade refusal checks the DESTINATION; a fresh temp
    // would bypass it, so guard explicitly before touching anything.
    if (f.exists() && com.zaaaam.kalku.core.crypto.VaultFileFormat.isEncrypted(f) && !isEncrypting()) {
        throw VaultException("Vault terkunci — buka PIN lagi sebelum menyimpan")
    }
    // ".part" is skipped by scan() and migration target collection, so a
    // leftover temp can never be indexed or converted.
    val tmp = File(f.parentFile, "${f.name}.part")
    try {
        writeThrough(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)), tmp)
        if (!tmp.renameTo(f)) throw VaultException("Gagal menyimpan ${f.name}")
    } finally {
        tmp.delete()
    }
    fileDao.updateStat(relPath, f.length(), System.currentTimeMillis())
    notifyMutated(relPath)
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
                // Never index temp leftovers from interrupted conversions.
                if (f.name.endsWith(".kmig") || f.name.endsWith(".part")) return@forEach
                val rel = join(dirRel, f.name)
                if (f.isDirectory) {
                    indexed += folderEntity(rel, dirRel)
                    walk(f, rel)
                } else {
                    val cat = CategoryDetector.detect(f.name, readHeader(f))
                    // Prefer the real disk mtime so externally-modified files keep
                    // meaningful Modified dates even before any DB row exists.
                    val mtime = f.lastModified().takeIf { it > 0 } ?: now
                    indexed += FileEntity(
                        relPath = rel,
                        name = f.name,
                        parent = dirRel,
                        isFolder = false,
                        category = cat.name,
                        mime = CategoryDetector.mimeOf(cat, f.name),
                        size = f.length(),
                        createdAt = mtime,
                        modifiedAt = mtime,
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
            // Keep stable ids and user metadata. modifiedAt survives rescans, yet
            // still advances when a file was edited externally (disk mtime newer).
            e.copy(
                id = old.id,
                favorite = old.favorite,
                tags = old.tags,
                createdAt = old.createdAt,
                modifiedAt = maxOf(old.modifiedAt, e.modifiedAt),
            )
        } ?: e
    }
    fileDao.upsertAll(preserved)
    val walkedPaths = indexed.map { it.relPath }.toSet()
    val stale = existing.values.filter { it.relPath !in walkedPaths }.map { it.relPath }
    if (stale.isNotEmpty()) fileDao.deleteByPaths(stale)
    recentDao().pruneOrphans()
    return indexed.count { !it.isFolder }
}
