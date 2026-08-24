package com.zaaaam.kalku.fs

import com.zaaaam.kalku.core.crypto.ChunkedGcmCipher
import com.zaaaam.kalku.core.crypto.VaultFileFormat
import com.zaaaam.kalku.security.CryptoSession
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Converts the whole vault between plaintext and encrypted-at-rest.
 *
 * The work list is derived from disk state (header presence), so an interrupted
 * migration simply resumes where it left off — no extra bookkeeping file.
 * Each file is converted atomically: write sibling temp → verify → rename over.
 */
class VaultEncryptionMigrator(
    private val repo: VaultRepo,
    private val session: CryptoSession,
) {

    enum class Dir { ENCRYPT, DECRYPT }

    sealed interface State {
        data object Idle : State
        data class Running(val direction: Dir, val done: Int, val total: Int, val currentName: String) : State
        data class Paused(val direction: Dir?, val done: Int, val total: Int) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val mutex = Mutex()

    @Volatile
    private var progress = 0 to 0

    val isRunning: Boolean get() = _state.value is State.Running

    /** Thrown when the session key disappears mid-run (auto-lock fired). */
    private class SessionLost : Exception()

    /**
     * Runs the migration to completion. Returns true only when fully done;
     * false when paused or failed (state reflects which). Cancellation is
     * rethrown after the state is recorded — pausing via cancel must not
     * swallow structured-concurrency semantics.
     */
    suspend fun runSuspend(direction: Dir): Boolean = mutex.withLock {
        if (_state.value is State.Running) return@withLock false
        try {
            run(direction)
            true
        } catch (e: SessionLost) {
            _state.value = State.Paused(direction, progress.first, progress.second)
            false
        } catch (e: CancellationException) {
            _state.value = State.Paused(direction, progress.first, progress.second)
            throw e
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "Migrasi gagal")
            false
        }
    }

    private suspend fun run(direction: Dir) {
        val targets = collectTargets(direction)
        progress = 0 to targets.size
        _state.value = State.Running(direction, 0, targets.size, "")
        var done = 0
        for ((rel, file) in targets) {
            coroutineContext.ensureActive()
            // Re-check per file: an auto-lock mid-migration must not keep
            // converting with a stale cipher reference while the UI shows
            // locked. Losing the key pauses the run instead.
            val cipher = session.cipherOrNull() ?: throw SessionLost()
            _state.value = State.Running(direction, done, targets.size, file.name)
            convertInPlace(file, cipher, encrypt = direction == Dir.ENCRYPT)
            // Keep the original Modified date; only the stored size changes.
            val previousModified = repo.fileDao.byPath(rel)?.modifiedAt
            repo.fileDao.updateStat(rel, file.length(), previousModified ?: System.currentTimeMillis())
            done++
            progress = done to targets.size
        }
        _state.value = State.Idle
    }

    /**
     * Files still needing conversion, derived purely from disk headers.
     * Includes recycle-bin contents (.Trash — same sensitive data) but skips
     * the .meta key directory.
     */
    internal fun collectTargets(direction: Dir): List<Pair<String, File>> {
        val out = mutableListOf<Pair<String, File>>()
        val wantEncrypted = direction == Dir.DECRYPT
        val root = repo.root

        fun walk(dir: File, rel: String) {
            dir.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { f ->
                if (rel.isEmpty() && f.name == ".meta") return@forEach
                // Temp leftovers from an interrupted conversion are never targets.
                if (f.name.endsWith(".kmig") || f.name.endsWith(".part")) return@forEach
                val r = join(rel, f.name)
                if (f.isDirectory) {
                    walk(f, r)
                } else if (VaultFileFormat.isEncrypted(f) == wantEncrypted) {
                    out += r to f
                }
            }
        }
        walk(root, "")
        return out
    }

    companion object {

        private object NullSink : OutputStream() {
            override fun write(b: Int) {}
            override fun write(b: ByteArray, off: Int, len: Int) {}
        }

        /**
         * Converts [f] in place between plaintext and encrypted.
         * Order guarantees the original survives any failure before the final
         * atomic rename.
         */
        fun convertInPlace(f: File, cipher: ChunkedGcmCipher, encrypt: Boolean) {
            val tmp = File(f.parentFile, "${f.name}.kmig")
            try {
                FileInputStream(f).use { input ->
                    tmp.outputStream().use { output ->
                        if (encrypt) cipher.encrypt(input, output) else cipher.decrypt(input, output)
                    }
                }
                if (encrypt) {
                    // Verify sealed end marker + all tags BEFORE destroying original.
                    FileInputStream(tmp).use { input ->
                        cipher.decrypt(input, NullSink)
                    }
                }
                if (!tmp.renameTo(f)) throw VaultException("Gagal mengonversi ${f.name}")
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        }
    }
}
