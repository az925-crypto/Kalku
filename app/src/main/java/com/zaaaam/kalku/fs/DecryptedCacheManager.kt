package com.zaaaam.kalku.fs

import com.zaaaam.kalku.core.crypto.VaultFileFormat
import com.zaaaam.kalku.security.CryptoSession
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * LRU cache of decrypted vault files under cacheDir/vault_dec, used by viewers
 * that need seekable plaintext (PDF/ZIP via file descriptor) or media players.
 *
 * - Plaintext files are returned as-is (no copy).
 * - Encrypted files are decrypted once into the cache and reused until evicted.
 * - [wipeAll] runs on lock so decrypted copies never outlive the session.
 * - Entries are invalidated when the repo mutates a path (delete → re-import
 *   with the same name must never serve the old content).
 * - Concurrent requests for the same path share one fill (per-path locks) and
 *   each writes a unique temp file, so parallel decryptions can't corrupt.
 * - Coil disk caching is bypassed for these files; memory-only image caching.
 */
class DecryptedCacheManager(
    private val repo: VaultRepo,
    private val session: CryptoSession,
    private val cacheDir: File,
    private val capacityBytes: Long = DEFAULT_CAPACITY,
) {

    companion object {
        const val DEFAULT_CAPACITY: Long = 512L * 1024 * 1024
    }

    private val lock = Any()
    private val entries = LinkedHashMap<String, File>() // relPath -> cached plain file (access order)
    private var totalBytes = 0L
    private val pathLocks = ConcurrentHashMap<String, Any>()

    init {
        repo.addMutationListener(::invalidateInternal)
    }

    fun dir(): File = cacheDir

    /**
     * Returns a readable plaintext [File] for [relPath], or null when the file
     * is missing or cannot be decrypted (wrong key / corruption).
     */
    fun plainFile(relPath: String): File? {
        val src = repo.fileOf(relPath)
        if (!src.isFile) return null
        if (!VaultFileFormat.isEncrypted(src)) return src

        synchronized(lock) {
            entries[relPath]?.let { cached ->
                if (cached.isFile) {
                    // LRU touch
                    entries.remove(relPath)
                    entries[relPath] = cached
                    return cached
                }
                entries.remove(relPath)
                totalBytes -= cached.length()
            }
        }

        var result: File? = null
        val pathLock = pathLocks.computeIfAbsent(relPath) { Any() }
        synchronized(pathLock) {
            // Double-check: another thread may have filled it while we waited.
            val cached = synchronized(lock) {
                val hit = entries[relPath]
                when {
                    hit == null -> null
                    hit.isFile -> {
                        entries.remove(relPath)
                        entries[relPath] = hit
                        hit
                    }
                    else -> {
                        entries.remove(relPath)
                        totalBytes -= hit.length()
                        null
                    }
                }
            }
            if (cached != null) {
                result = cached
            } else {
                val cipher = session.cipherOrNull() ?: return@synchronized
                val dst = File(cacheDir, sha1(relPath) + "." + src.extension)
                // Unique temp per attempt: two writers sharing one ".part" name
                // would truncate each other mid-decrypt.
                val tmp = File(cacheDir, "${dst.name}.${UUID.randomUUID()}.part")
                try {
                    cacheDir.mkdirs()
                    FileInputStream(src).use { input ->
                        tmp.outputStream().use { output -> cipher.decrypt(input, output) }
                    }
                    synchronized(lock) {
                        dst.delete() // replace any orphaned copy from an earlier crash
                        if (tmp.renameTo(dst)) {
                            totalBytes += dst.length()
                            entries[relPath] = dst
                            evictIfNeededLocked(protect = relPath)
                            result = dst
                        } else {
                            tmp.delete()
                        }
                    }
                } catch (_: Exception) {
                    tmp.delete()
                }
            }
        }
        pathLocks.remove(relPath, pathLock)
        return result
    }

    /** Deletes every decrypted copy (called on lock / wipe / startup). */
    fun wipeAll() {
        synchronized(lock) {
            entries.clear()
            totalBytes = 0
        }
        cacheDir.deleteRecursively()
    }

    /** Drops cached copies for [relPath] and everything below it (folders). */
    private fun invalidateInternal(relPath: String) {
        val prefix = "$relPath/"
        synchronized(lock) {
            val stale = entries.keys.filter { it == relPath || it.startsWith(prefix) }
            for (key in stale) {
                entries.remove(key)?.let { f ->
                    totalBytes -= f.length()
                    f.delete()
                }
            }
        }
    }

    private fun evictIfNeededLocked(protect: String?) {
        while (totalBytes > capacityBytes && entries.isNotEmpty()) {
            val oldestKey = entries.keys.firstOrNull() ?: break
            // Keep the just-added entry reachable for the caller even when it
            // alone exceeds capacity — refusing to cache it would break the
            // viewer entirely; overflow until wipeAll is the lesser cost.
            if (oldestKey == protect && entries.size == 1) break
            val f = entries.remove(oldestKey) ?: break
            totalBytes -= f.length()
            f.delete()
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
