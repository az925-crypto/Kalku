package com.zaaaam.kalku.fs

import com.zaaaam.kalku.core.crypto.KeyMaterial
import com.zaaaam.kalku.core.crypto.VaultCryptoException
import java.io.File
import javax.crypto.SecretKey

/**
 * Persists Secure Vault key material inside the vault's `.meta` directory so it
 * survives uninstall/reinstall:
 *
 *   .meta/kdf.params → "v1:<iterations>:<saltBase64>"
 *   .meta/dek.bin    → "<ivBase64>:<wrappedDekBase64>" (DEK wrapped by PIN-KEK)
 */
object VaultCryptoStore {

    private const val PARAMS_VERSION = "v1"
    private const val FILE_PARAMS = "kdf.params"
    private const val FILE_DEK = "dek.bin"
    private const val FILE_DEK_BACKUP = "dek.bak"

    /** Sanity bounds for the stored PBKDF2 iteration count (DoS / corruption guard). */
    private const val MIN_ITERATIONS = 10_000
    private const val MAX_ITERATIONS = 2_000_000

    private fun metaDir(root: File) = File(root, ".meta")
    private fun paramsFile(root: File) = File(metaDir(root), FILE_PARAMS)
    private fun dekFile(root: File) = File(metaDir(root), FILE_DEK)

    fun hasKeys(root: File): Boolean = paramsFile(root).isFile && dekFile(root).isFile

    /** True when a fresh vault should start with encryption enabled by default. */
    fun shouldDefaultEncrypted(root: File): Boolean =
        !hasKeys(root) && !hasAnyUserFiles(root)

    private fun hasAnyUserFiles(root: File): Boolean {
        if (!root.isDirectory) return false
        val skip = setOf(VaultPaths.TRASH_DIR, ".meta")
        return root.listFiles()?.any { f ->
            if (f.name in skip) return@any false
            if (f.isFile) return@any true
            f.walkTopDown().drop(1).any { it.isFile }
        } ?: false
    }

    data class Params(val salt: ByteArray, val iterations: Int)

    fun readParams(root: File): Params {
        val text = paramsFile(root).readText().trim()
        val parts = text.split(":")
        if (parts.size != 3 || parts[0] != PARAMS_VERSION) throw VaultCryptoException("Bad kdf.params")
        val iterations = parts[1].toIntOrNull()
            ?.coerceIn(MIN_ITERATIONS, MAX_ITERATIONS)
            ?: throw VaultCryptoException("Bad kdf.params")
        return Params(b64d(parts[2]), iterations)
    }

    /**
     * Creates salt + DEK wrapped under the KEK of [pin]; refuses to overwrite an
     * existing key file (use [rewrap] for PIN changes).
     */
    fun createIfMissing(root: File, pin: String): SecretKey {
        metaDir(root).mkdirs()
        if (hasKeys(root)) throw VaultCryptoException("Key material already exists")
        val salt = KeyMaterial.newSalt()
        paramsFile(root).writeText(listOf(PARAMS_VERSION, KeyMaterial.KEK_ITERATIONS.toString(), b64e(salt)).joinToString(":"))
        val kek = KeyMaterial.deriveKek(pin.toCharArray(), salt)
        val dek = KeyMaterial.generateDek()
        val wrapped = KeyMaterial.wrapDek(dek, kek)
        writeWrapped(root, wrapped)
        return dek
    }

    /** Unwraps and returns the DEK using [pin]. Throws on wrong PIN. */
    fun unwrap(root: File, pin: String): SecretKey {
        val p = readParams(root)
        val kek = KeyMaterial.deriveKek(pin.toCharArray(), p.salt, p.iterations)
        return KeyMaterial.unwrapDek(readWrapped(root), kek)
    }

    /**
     * Re-wraps [dek] under the KEK of [newPin]. The DEK itself is unchanged, so
     * already-encrypted files stay readable — no re-encryption needed.
     *
     * Crash safety: the previous wrapped key is backed up first and only removed
     * by [cleanupBackup] after the caller has committed the matching PIN hash.
     * A process death between re-wrap and hash update leaves a recoverable
     * state — [restoreBackup] rolls the key back so the OLD PIN still works.
     */
    fun rewrap(root: File, dek: SecretKey, newPin: String) {
        if (!hasKeys(root)) throw VaultCryptoException("Key material missing")
        val p = readParams(root)
        val newKek = KeyMaterial.deriveKek(newPin.toCharArray(), p.salt, p.iterations)
        // Atomic-ish: write to temp then move over, keeping a rollback copy of
        // the current wrap until the caller finishes the whole PIN change.
        val tmp = File(dekFile(root).parentFile, "${FILE_DEK}.tmp")
        val backup = backupFile(root)
        val current = dekFile(root)
        val wrapped = KeyMaterial.wrapDek(dek, newKek)
        tmp.writeText(listOf(b64e(wrapped.iv), b64e(wrapped.ciphertext)).joinToString(":"))
        current.copyTo(backup, overwrite = true)
        if (!tmp.renameTo(current)) {
            tmp.delete()
            backup.delete()
            throw VaultCryptoException("Failed to store re-wrapped key")
        }
    }

    /**
     * Rolls the wrapped DEK back to its pre-change value (no-op when no backup
     * exists). Returns true when a restore actually happened.
     */
    fun restoreBackup(root: File): Boolean {
        val backup = backupFile(root)
        if (!backup.isFile) return false
        val restored = backup.renameTo(dekFile(root))
        if (!restored) backup.delete()
        return restored
    }

    /** Removes the rollback copy after a fully committed PIN change. */
    fun cleanupBackup(root: File) {
        backupFile(root).delete()
    }

    private fun backupFile(root: File) = File(metaDir(root), FILE_DEK_BACKUP)

    internal fun readWrapped(root: File): KeyMaterial.WrappedKey {
        val parts = dekFile(root).readText().trim().split(":")
        if (parts.size != 2) throw VaultCryptoException("Bad key file")
        return KeyMaterial.WrappedKey(b64d(parts[0]), b64d(parts[1]))
    }

    private fun writeWrapped(root: File, wrapped: KeyMaterial.WrappedKey) {
        dekFile(root).writeText(listOf(b64e(wrapped.iv), b64e(wrapped.ciphertext)).joinToString(":"))
    }

    private fun b64e(b: ByteArray): String = java.util.Base64.getEncoder().encodeToString(b)
    private fun b64d(s: String): ByteArray = java.util.Base64.getDecoder().decode(s)
}
