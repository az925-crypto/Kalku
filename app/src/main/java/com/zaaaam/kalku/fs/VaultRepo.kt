package com.zaaaam.kalku.fs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.zaaaam.kalku.core.Category
import com.zaaaam.kalku.core.CategoryDetector
import com.zaaaam.kalku.core.Names
import com.zaaaam.kalku.core.crypto.VaultFileFormat
import com.zaaaam.kalku.data.FileEntity
import com.zaaaam.kalku.data.KalkuDatabase
import com.zaaaam.kalku.data.RecentEntity
import com.zaaaam.kalku.security.CryptoSession
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream

/** Thrown when a vault operation fails; message is user-presentable. */
class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

fun join(parent: String, name: String): String = if (parent.isEmpty()) name else "$parent/$name"

/**
 * Single facade for vault queries and structural mutations.
 * Physical files live under [VaultPaths]; Room keeps only metadata,
 * fully rebuildable via scan() (see VaultTrashOps / VaultIndexOps).
 *
 * When the session holds a Secure Vault DEK ([CryptoSession]), all writes are
 * AES-GCM encrypted at rest and reads decrypt transparently.
 */
class VaultRepo(
    val appContext: Context,
    internal val db: KalkuDatabase,
    private val crypto: CryptoSession,
) {
    val fileDao get() = db.fileDao()
    internal fun trashDao() = db.trashDao()
    internal fun recentDao() = db.recentDao()

    val storage: VaultPaths.Storage by lazy { VaultPaths.resolve(context = appContext) }
    val root: File get() = storage.root

    // --------------------------------------------------------------- crypto

    /** True when this session can encrypt/decrypt (DEK loaded). */
    fun isEncrypting(): Boolean = crypto.cipherOrNull() != null

    /** Plaintext view of [f]; decrypts transparently, throws if locked+encrypted. */
    fun plainStream(f: File): InputStream {
        require(f.isFile) { "Not a readable file" }
        if (!VaultFileFormat.isEncrypted(f)) return f.inputStream()
        val cipher = crypto.cipherOrNull() ?: throw VaultException("File terenkripsi — buka vault untuk membuka")
        return cipher.decryptedStream(f.inputStream().buffered())
    }

    /** Copies [source] into [outFile], encrypting when the session allows.
     *  Refuses to silently downgrade an encrypted file to plaintext when the
     *  session lost its key (e.g. auto-lock fired mid-edit). */
    internal fun writeThrough(source: InputStream, outFile: File) {
        val cipher = crypto.cipherOrNull()
        if (cipher == null) {
            if (outFile.exists() && VaultFileFormat.isEncrypted(outFile)) {
                throw VaultException("Vault terkunci — buka PIN lagi sebelum menyimpan")
            }
            FileOutputStream(outFile).use { source.copyTo(it) }
        } else {
            FileOutputStream(outFile).use { cipher.encrypt(source.buffered(), it) }
        }
    }

    private fun writeBytesEncrypted(f: File, data: ByteArray) {
        writeThrough(ByteArrayInputStream(data), f)
    }

    /** First plaintext bytes of a vault file (decrypting when needed). */
    internal fun plainHead(f: File): ByteArray = try {
        plainStream(f).use { s ->
            val buf = ByteArray(512)
            val n = s.read(buf)
            if (n > 0) buf.copyOf(n) else ByteArray(0)
        }
    } catch (_: Exception) {
        ByteArray(0)
    }

    /**
     * Copies [src] to [dst] through the encrypt pipeline while capturing the
     * first plaintext bytes in the SAME pass — avoids decrypting the file twice
     * (once for the category head, once for the body).
     */
    internal fun copyPlainCapturingHead(src: File, dst: File): ByteArray {
        val head = ByteArray(512)
        var headLen = 0
        plainStream(src).use { input ->
            while (headLen < head.size) {
                val r = input.read(head, headLen, head.size - headLen)
                if (r < 0) break
                headLen += r
            }
            writeThrough(
                SequenceInputStream(ByteArrayInputStream(head, 0, headLen), input),
                dst,
            )
        }
        return head.copyOf(headLen)
    }

    // ------------------------------------------------------------ cache sync

    /** Notified with a vault relPath whenever its on-disk content changes identity. */
    private val mutationListeners = mutableListOf<(String) -> Unit>()

    fun addMutationListener(listener: (String) -> Unit) {
        mutationListeners += listener
    }

    internal fun notifyMutated(relPath: String) {
        mutationListeners.forEach { runCatching { it(relPath) } }
    }

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
    suspend fun ensureStructure() = withContext(kotlinx.coroutines.Dispatchers.IO) {
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
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
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
            var createdName: String? = null
            try {
                val rawName = Names.sanitizeFileName(displayNameOf(uri))
                val name = Names.uniqueName(rawName, existing)
                requireNotReserved(destParent, name)
                val target = File(destDir, name)
                createdName = name
                val header = ByteArray(512)
                var headerLen = 0
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    headerLen = input.read(header)
                    if (headerLen < 0) headerLen = 0
                    // Detection uses the plaintext head; the write pipeline may
                    // then encrypt everything (header included).
                    val body = SequenceInputStream(ByteArrayInputStream(header, 0, headerLen), input)
                    writeThrough(body, target)
                } ?: continue
                insertFileRow(target, destParent, header.copyOf(headerLen))
                existing.add(name)
                created.add(name)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancellation must abort the whole import — swallowing it here
                // would keep doing blocking I/O per URI and delete fresh work.
                createdName?.let { File(destDir, it).delete() }
                throw e
            } catch (_: Exception) {
                // Skip unreadable provider entries but keep importing the rest;
                // never leave half-written orphans behind for scan() to index.
                createdName?.let { File(destDir, it).delete() }
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
        // Detect from the plaintext source BEFORE it gets encrypted on disk.
        val srcHeader = readHeader(src)
        writeThrough(src.inputStream().buffered(), outFile)
        insertFileRow(outFile, destParent, srcHeader)
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
        writeBytesEncrypted(f, content.toByteArray(Charsets.UTF_8))
        return textFileEntity(f, parent, finalName)
    }

    /**
     * Like [createTextFile] but fails instead of uniquifying when the name is
     * already taken — used by Save As so users never get surprise "(2)" copies.
     */
    suspend fun createTextFileExact(parent: String, rawName: String, content: String): FileEntity {
        val finalName = Names.sanitizeFileName(rawName).ifEmpty { "untitled.txt" }
        requireNotReserved(parent, finalName)
        val f = File(fileOf(parent), finalName).also { assertInside(it) }
        if (f.exists()) throw VaultException("Nama sudah dipakai")
        writeBytesEncrypted(f, content.toByteArray(Charsets.UTF_8))
        return textFileEntity(f, parent, finalName)
    }

    private suspend fun textFileEntity(f: File, parent: String, finalName: String): FileEntity {
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
        notifyMutated(entry.relPath)
    }

    private suspend fun repathDescendants(oldPrefix: String, newPrefix: String) {
        all().filter { it.relPath.startsWith("$oldPrefix/") }.forEach { row ->
            val newRel = newPrefix + row.relPath.removePrefix(oldPrefix)
            // Update relPath, parent AND name together — repathing only relPath
            // leaves stale parent values and emptied folders behind.
            fileDao.repathEntry(
                row.id,
                newRel,
                newRel.substringBeforeLast('/', ""),
                newRel.substringAfterLast('/'),
            )
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
            notifyMutated(entry.relPath)
        }
    }

    suspend fun copy(ids: List<Long>, destParent: String) {
        val destDir = fileOf(destParent).also { assertInside(it); it.mkdirs() }
        // Same guard as move(): copying a folder into itself would make the
        // walk consume its own output and duplicate recursively until the
        // disk fills up.
        for (id in ids) {
            val entry = fileDao.byId(id) ?: continue
            if (destParent == entry.relPath || destParent.startsWith(entry.relPath + "/")) {
                throw VaultException("Cannot copy folder into itself")
            }
        }
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
                // Heads captured during the copy pass — no second decryption.
                val heads = mutableMapOf<String, ByteArray>()
                src.walkTopDown().forEach { f ->
                    val rel = f.relativeTo(src).path
                    val dst = File(target, rel)
                    // plainStream decrypts encrypted sources so the copy is a
                    // fresh plaintext → (re)encrypt pass, never blob-of-blob.
                    if (f.isDirectory) dst.mkdirs() else heads[rel] = copyPlainCapturingHead(f, dst)
                }
                fileDao.upsert(folderEntity(join(destParent, finalName), destParent))
                indexTreeUnder(target, join(destParent, finalName), heads)
            } else {
                val head = copyPlainCapturingHead(src, target)
                insertFileRow(target, destParent, head)
            }
        }
    }

    /** Indexes everything below an already-copied folder on disk. */
    internal suspend fun indexTreeUnder(
        dirOnDisk: File,
        dirRel: String,
        capturedHeads: Map<String, ByteArray> = emptyMap(),
    ) {
        dirOnDisk.listFiles()?.sortedBy { it.name }?.forEach { f ->
            val rel = join(dirRel, f.name)
            if (f.isDirectory) {
                fileDao.upsert(folderEntity(rel, dirRel))
                indexTreeUnder(f, rel, capturedHeads)
            } else {
                // Prefer a head captured during the copy pass; fall back to a
                // fresh decrypt for trees that were not just copied.
                val head = capturedHeads[f.relativeToOrNull(dirOnDisk)?.path]
                    ?: plainHead(f)
                insertFileRow(f, dirRel, head)
            }
        }
    }

    /**
     * Imports a plaintext [srcDir] tree into [destRel] (creating folder rows),
     * routing every file through the normal import pipeline so encryption and
     * sizes are handled consistently.
     */
    suspend fun importTree(srcDir: File, destRel: String) {
        fileOf(destRel).also { it.mkdirs(); assertInside(it) }
        if (fileDao.byPath(destRel) == null) {
            fileDao.upsert(folderEntity(destRel, destRel.substringBeforeLast('/', "")))
        }
        srcDir.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { f ->
            if (f.isDirectory) {
                val childRel = join(destRel, f.name)
                fileDao.upsert(folderEntity(childRel, destRel))
                importTree(f, childRel)
            } else {
                importLocalFile(f, destRel)
            }
        }
    }

    companion object {
        private val RESERVED_ROOT_NAMES = setOf(VaultPaths.TRASH_DIR, ".meta")
    }
}
