package com.zaaaam.kalku.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zaaaam.kalku.core.PinHasher
import com.zaaaam.kalku.core.crypto.VaultCryptoException
import com.zaaaam.kalku.data.SettingsRepo
import com.zaaaam.kalku.fs.VaultCryptoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Session lock state for the vault. Lives in memory only:
 * a fresh process always starts locked (fail-safe default).
 *
 * Owns THE [CryptoSession] injected from the app container — the same single
 * instance used by VaultRepo, DecryptedCacheManager and the encryption
 * migrator. Unlocking with the PIN loads the vault DEK into it; locking /
 * auto-lock wipes it.
 */
class LockController(
    private val settings: SettingsRepo,
    private val crypto: CryptoSession,
    private val vaultRoot: () -> File?,
) {

    var unlocked by mutableStateOf(false)
        private set

    var pinExists by mutableStateOf<Boolean?>(null) // null = still loading
        private set

    var lastBackgroundedAt by mutableStateOf(0L)
        private set

    suspend fun load() {
        val hash = settings.currentPinHash()
        pinExists = !hash.isNullOrBlank()
    }

    /** Verifies a candidate PIN against the stored hash (CPU-bound → Default). */
    suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.Default) {
        PinHasher.verify(pin, settings.currentPinHash())
    }

    sealed interface UnlockResult {
        data object Ok : UnlockResult
        data object WrongPin : UnlockResult
    }

    /**
     * Verifies [pin] and, on success, opens the session: unlocks and loads the
     * vault DEK when Secure Vault key material exists. Key loading failures are
     * non-fatal — the vault stays usable in mixed/plaintext mode.
     */
    suspend fun tryUnlock(pin: String): UnlockResult {
        val ok = verifyPin(pin)
        if (!ok) return UnlockResult.WrongPin
        unlocked = true
        val root = vaultRoot() ?: return UnlockResult.Ok
        withContext(Dispatchers.IO) {
            runCatching {
                if (VaultCryptoStore.hasKeys(root)) {
                    try {
                        crypto.load(VaultCryptoStore.unwrap(root, pin))
                    } catch (e: VaultCryptoException) {
                        // A crash between "DEK re-wrapped under the new PIN" and
                        // "login hash updated" leaves the old hash pointing at a
                        // DEK wrapped under the old PIN — recoverable because
                        // rewrap() kept a backup. Restore it and retry once.
                        if (VaultCryptoStore.restoreBackup(root)) {
                            crypto.load(VaultCryptoStore.unwrap(root, pin))
                        } else {
                            throw e
                        }
                    }
                }
            }
        }
        return UnlockResult.Ok
    }

    /** Plain unlock without PIN (fresh default-PIN path before setup). */
    fun unlock() { unlocked = true }

    suspend fun setPin(pin: String, currentPin: String? = null) {
        require(pin.length in 4..16) { "PIN length must be 4-16" }
        val root = vaultRoot()
        // Secure Vault key lifecycle — performed BEFORE touching the stored
        // hash, and any failure aborts the whole change. Otherwise a swallowed
        // error here could leave the DEK wrapped under the old PIN while the
        // login hash accepts the new one: files lost with a smiling UI.
        //  - DEK loaded → re-wrap under the new PIN (files stay readable).
        //  - Keys exist but not loaded → unwrap with [currentPin] first.
        //  - Fresh vault (no keys, no files yet) → create keys; encryption on.
        //  - Legacy plaintext vault → leave untouched until user opts in.
        var hasKeysNow = false
        if (root != null && VaultCryptoStore.hasKeys(root)) {
            hasKeysNow = true
            val dek = crypto.keyOrNull()
            if (dek == null && !currentPin.isNullOrBlank()) {
                val loaded = withContext(Dispatchers.IO) {
                    runCatching {
                        VaultCryptoStore.unwrap(root, currentPin).also(crypto::load)
                    }.isSuccess
                }
                if (!loaded) throw VaultCryptoException("PIN saat ini tidak bisa membuka kunci vault")
            } else if (dek == null) {
                // No session key and no current PIN to unwrap with: leave the
                // key material untouched instead of guessing.
                throw VaultCryptoException("Vault terkunci — buka PIN lama dulu sebelum mengganti")
            }
            // Capture the DEK once: an auto-lock firing mid-change must not be
            // able to wipe the key out from under the re-wrap below.
            val dekForRewrap = crypto.keyOrNull()
                ?: throw VaultCryptoException("Vault terkunci di tengah operasi — coba lagi")
            val rewrapped = withContext(Dispatchers.IO) {
                runCatching { VaultCryptoStore.rewrap(root, dekForRewrap, pin) }.isSuccess
            }
            if (!rewrapped) throw VaultCryptoException("Gagal menyimpan kunci ulang")
        } else if (root != null && VaultCryptoStore.shouldDefaultEncrypted(root)) {
            val created = withContext(Dispatchers.IO) {
                runCatching {
                    crypto.load(VaultCryptoStore.createIfMissing(root, pin))
                    settings.setEncryptionEnabled(true)
                }.isSuccess
            }
            if (!created) throw VaultCryptoException("Gagal membuat kunci enkripsi")
        }

        settings.setPinHash(withContext(Dispatchers.Default) { PinHasher.hash(pin) })
        if (root != null && hasKeysNow) {
            // New wrap + new hash are both committed — the transition is over.
            withContext(Dispatchers.IO) { VaultCryptoStore.cleanupBackup(root) }
        }
        pinExists = true
        // An auto-lock may have wiped the session while PBKDF2 was running;
        // never report unlocked with a keyless Secure-Vault session.
        if (hasKeysNow && !crypto.isUnlocked) {
            throw VaultCryptoException("Vault terkunci ulang saat menyimpan — buka lagi")
        }
        unlocked = true
    }

    fun lock() {
        unlocked = false
        crypto.wipe()
    }

    fun onBackground() { lastBackgroundedAt = System.currentTimeMillis() }

    /** Re-lock if auto-lock delay elapsed while backgrounded. 0 disables. */
    suspend fun relockIfExpired(): Boolean {
        val minutes = settings.autoLockMinutes.first()
        if (!unlocked || minutes <= 0) return false
        if (System.currentTimeMillis() - lastBackgroundedAt >= minutes * 60_000L) {
            lock()
            return true
        }
        return false
    }
}
