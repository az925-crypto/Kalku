package com.zaaaam.kalku.fs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.zaaaam.kalku.core.Category
import com.zaaaam.kalku.core.CategoryDetector
import com.zaaaam.kalku.core.Names
import com.zaaaam.kalku.data.FileEntity
import com.zaaaam.kalku.data.KalkuDatabase
import com.zaaaam.kalku.data.RecentEntity
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Thrown when a vault operation fails; message is user-presentable. */
class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

fun join(parent: String, name: String): String = if (parent.isEmpty()) name else "$parent/$name"

/**
 * Single facade for vault queries and structural mutations.
 * Physical files live under [VaultPaths]; Room keeps only metadata,
 * fully rebuildable via scan() (see VaultTrashOps / VaultIndexOps).
 */
class VaultRepo(
    val appContext: Context,
    internal val db: KalkuDatabase,
) {
    val fileDao get() = db.fileDao()
    internal fun trashDao() = db.trashDao()
    internal fun recentDao() = db.recentDao()

    val storage: VaultPaths.Storage by lazy { VaultPaths.resolve(context = appContext) }
    val root: File get() = storage.root

    // ------------------------------------------------------------------ paths

    fun fileOf(relPath: String): File {
        val clean = relPath.trim('/')
        require(clean.split('/').none { it == ".." || it.isBlank() }) { "Invalid path" }
        return File(root, clean)
    }

    internal fun assertInside(f: File) {
        val rootPath = root.canonicalPath + File.separator
        val p = f.canonicalPath
        if (!p.startsWith(rootPath) && p != root.canonicalPath) throw VaultException("Path escapes vault")
    }

    fun exists(relPath: String): Boolean = fileOf(relPath).exists()

    /** Escapes LIKE wildcards so user file names can't match unintended rows. */
    internal fun likeEscaped(path: String): String =
        path.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /** Root-level system folders users may not occupy. */
    private fun requireNotReserved(parent: String, name: String) {
        if (parent.isEmpty() && name in RESERVED_ROOT_NAMES) throw VaultException("'$name' is a reserved name")
    }

    // -------------------------------------------------------------- lifecycle

    /** Creates root, trash, meta and the default folder layout + index rows for folders. */
    suspend fun ensureStructure() {
        root.mkdirs()
        VaultPaths.trashDir(root).mkdirs()
        VaultPaths.metaDir(root).mkdirs()
        for (name in VaultPaths.DEFAULT_FOLDERS) {
            val dir = File(root, name)
            dir.mkdirs()
            if (fileDao.byPath(name) == null && dir.isDirectory) {
                fileDao.upsert(folderEntity(name, ""))
            }
        }
    }

    internal fun folderEntity(relPath: String, parent: String): FileEntity {
        val now = System.currentTimeMillis()
        return FileEntity(
            relPath = relPath,
            name = relPath.substringAfterLast('/'),
            parent = parent,
            isFolder = true,
            category = Category.OTHER.name,
            mime = "",
            size = 0,
            createdAt = now,
            modifiedAt = now,
        )
    }

    // ------------------------------------------------------------------ query

    suspend fun children(parent: String): List<FileEntity> = fileDao.children(parent)
    suspend fun byId(id: Long): FileEntity? = fileDao.byId(id)
    suspend fun byPath(path: String): FileEntity? = fileDao.byPath(path)
    suspend fun all(): List<FileEntity> = fileDao.all()

    suspend fun recordOpen(fileId: Long) {
        val f = fileDao.byId(fileId) ?: return
        db.recentDao().upsert(RecentEntity(fileId, f.name, f.relPath, f.category, System.currentTimeMillis()))
    }

    // ----------------------------------------------------------------- import

    suspend fun displayNameOf(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: "imported"
    }

    /** Copies content of [uris] into [destParent]; returns names actually created. */
    suspend fun importUris(uris: List<Uri>, destParent: String): List<String> {
        val destDir = fileOf(destParent).also { it.mkdirs(); assertInside(it) }
        val existing = destDir.list()?.toMutableSet() ?: mutableSetOf()
        val created = mutableListOf<String>()
        for (uri in uris) {
            try {
                val rawName = Names.sanitizeFileName(displayNameOf(uri))
                val name = Names.uniqueName(rawName, existing)
                val outFile = File(destDir, name)
                val header = ByteArray(512)
                var headerLen = 0
                context.contentResolver.openInputStream(uri)?.use { input ->
                    headerLen = input.read(header)
                    FileOutputStream(outFile).use { out ->
                        if (headerLen > 0) out.write(header, 0, headerLen)
                        input.copyTo(out)
                    }
                } ?: continue
                requireNotReserved(destParent, name)
                insertFileRow(outFile, destParent, header)
                existing.add(name)
                created.add(name)
            } catch (_: Exception) {
                // Skip unreadable provider entries but keep importing the rest.
            }
        }
        return created
    }

    /** Imports a plain local file (e.g. staged from a share intent). */
    suspend fun importLocalFile(src: File, destParent: String): String? {
        if (!src.isFile) return null
        val destDir = fileOf(destParent).also { it.mkdirs() }
        val existing = destDir.list()?.toSet() ?: emptySet()
        val name = Names.uniqueName(Names.sanitizeFileName(src.name), existing)
        val outFile = File(destDir, name)
        src.copyTo(outFile, overwrite = false)
        insertFileRow(outFile, destParent, readHeader(outFile))
        return name
    }

    internal fun readHeader(f: File): ByteArray {
        val buf = ByteArray(512)
        try {
            BufferedInputStream(f.inputStream()).use { it.read(buf) }
        } catch (_: IOException) {
        }
        return buf
    }

    internal suspend fun insertFileRow(outFile: File, parentRel: String, header: ByteArray) {
        val cat = CategoryDetector.detect(outFile.name, header)
        val now = System.currentTimeMillis()
        fileDao.upsert(
            FileEntity(
                relPath = join(parentRel, outFile.name),
                name = outFile.name,
                parent = parentRel,
                isFolder = false,
                category = cat.name,
                mime = CategoryDetector.mimeOf(cat, outFile.name),
                size = outFile.length(),
                createdAt = now,
                modifiedAt = now,
            )
        )
    }

    // ------------------------------------------------------------ create ops

    suspend fun createFolder(parent: String, rawName: String): FileEntity {
        val name = Names.sanitizeFileName(rawName)
        val siblings = children(parent).map { it.name }.toSet()
        val finalName = Names.uniqueName(name, siblings)
        requireNotReserved(parent, finalName)
        val dir = File(fileOf(parent), finalName).also { assertInside(it); it.mkdirs() }
        if (!dir.isDirectory) throw VaultException("Cannot create folder")
        val entity = folderEntity(join(parent, finalName), parent)
        fileDao.upsert(entity)
        return entity
    }

    suspend fun createTextFile(parent: String, rawName: String, content: String): FileEntity {
        val name = Names.sanitizeFileName(rawName).ifEmpty { "untitled.txt" }
        val siblings = children(parent).map { it.name }.toSet()
        val finalName = Names.uniqueName(name, siblings)
        requireNotReserved(parent, finalName)
        val f = File(fileOf(parent), finalName).also { assertInside(it) }
        f.writeText(content)
        val cat = CategoryDetector.detect(finalName, null)
        val entity = FileEntity(
            relPath = join(parent, finalName),
            name = finalName,
            parent = parent,
            isFolder = false,
            category = cat.name,
            mime = CategoryDetector.mimeOf(cat, finalName),
            size = f.length(),
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
        )
        fileDao.upsert(entity)
        return entity
    }

    // ----------------------------------------------- rename / move / copy ops

    suspend fun rename(id: Long, newNameRaw: String) {
        val entry = fileDao.byId(id) ?: throw VaultException("Not found")
        val newName = Names.sanitizeFileName(newNameRaw)
        if (newName == entry.name || newName.isEmpty()) return
        val old = fileOf(entry.relPath)
        assertInside(old)
        if (!old.exists()) throw VaultException("Missing on disk")
        requireNotReserved(entry.parent, newName)
        val target = File(old.parentFile, newName).also { assertInside(it) }
        if (target.exists()) throw VaultException("Name already exists")
        if (!old.renameTo(target)) throw VaultException("Rename failed")

        val newPath = join(entry.parent, newName)
        fileDao.repathEntry(id, newPath, entry.parent, newName)
        if (entry.isFolder) repathDescendants(entry.relPath, newPath)
    }

    private suspend fun repathDescendants(oldPrefix: String, newPrefix: String) {
        all().filter { it.relPath.startsWith("$oldPrefix/") }.forEach { row ->
            fileDao.repath(row.relPath, newPrefix + row.relPath.removePrefix(oldPrefix))
        }
    }

    suspend fun move(ids: List<Long>, destParent: String) {
        val destDir = fileOf(destParent).also { assertInside(it); it.mkdirs() }
        val moving = ids.mapNotNull { fileDao.byId(it) }
        for (entry in moving) {
            if (destParent == entry.relPath || destParent.startsWith(entry.relPath + "/")) {
                throw VaultException("Cannot move folder into itself")
            }
        }
        var taken = destDir.list()?.toSet() ?: emptySet()
        for (entry in moving) {
            val src = fileOf(entry.relPath)
            if (!src.exists()) continue
            val finalName = Names.uniqueName(entry.name, taken)
            requireNotReserved(destParent, finalName)
            val target = File(destDir, finalName).also { assertInside(it) }
            if (!src.renameTo(target)) throw VaultException("Move failed for ${entry.name}")
            taken = taken + finalName
            val newPath = join(destParent, finalName)
            fileDao.repathEntry(entry.id, newPath, destParent, finalName)
            if (entry.isFolder) repathDescendants(entry.relPath, newPath)
        }
    }

    suspend fun copy(ids: List<Long>, destParent: String) {
        val destDir = fileOf(destParent).also { assertInside(it); it.mkdirs() }
        var taken = destDir.list()?.toSet() ?: emptySet()
        for (id in ids) {
            val entry = fileDao.byId(id) ?: continue
            val src = fileOf(entry.relPath)
            if (!src.exists()) continue
            val finalName = Names.uniqueName(entry.name, taken)
            taken = taken + finalName
            requireNotReserved(destParent, finalName)
            val target = File(destDir, finalName).also { assertInside(it) }
            if (entry.isFolder) {
                src.walkTopDown().forEach { f ->
                    val dst = File(target, f.relativeTo(src).path)
                    if (f.isDirectory) dst.mkdirs() else f.copyTo(dst, overwrite = true)
                }
                fileDao.upsert(folderEntity(join(destParent, finalName), destParent))
                indexTreeUnder(target, join(destParent, finalName))
            } else {
                src.copyTo(target, overwrite = true)
                insertFileRow(target, destParent, readHeader(target))
            }
        }
    }

    /** Indexes everything below an already-copied folder on disk. */
    internal suspend fun indexTreeUnder(dirOnDisk: File, dirRel: String) {
        dirOnDisk.listFiles()?.sortedBy { it.name }?.forEach { f ->
            val rel = join(dirRel, f.name)
            if (f.isDirectory) {
                fileDao.upsert(folderEntity(rel, dirRel))
                indexTreeUnder(f, rel)
            } else {
                insertFileRow(f, dirRel, readHeader(f))
            }
        }
    }

    companion object {
        private val RESERVED_ROOT_NAMES = setOf(VaultPaths.TRASH_DIR, ".meta")
    }
}
