package com.zaaaam.kalku.security

import com.zaaaam.kalku.core.crypto.ChunkedGcmCipher
import javax.crypto.SecretKey

/**
 * Holds the vault DEK in memory while the session is unlocked.
 * Wiped on lock / auto-lock; a fresh process always starts empty (fail-safe).
 */
class CryptoSession {

    @Volatile
    private var dek: SecretKey? = null

    val isUnlocked: Boolean get() = dek != null

    /** Installs the DEK for this session. */
    fun load(key: SecretKey) {
        dek = key
    }

    /** Drops the DEK; encrypted files become unreadable until re-unlock. */
    fun wipe() {
        dek = null
    }

    /** Key access for PIN re-wrap; null when no DEK is loaded. */
    fun keyOrNull(): SecretKey? = dek

    /**
     * Returns a cipher bound to the session DEK, or null when Secure Vault has
     * no loaded key for this session (plaintext / mixed mode).
     */
    fun cipherOrNull(): ChunkedGcmCipher? = dek?.let { ChunkedGcmCipher(it) }
}
