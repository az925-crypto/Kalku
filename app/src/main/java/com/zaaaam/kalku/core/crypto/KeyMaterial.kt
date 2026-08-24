package com.zaaaam.kalku.core.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Key hierarchy for Secure Vault:
 *
 *   PIN --PBKDF2(salt)--> KEK --AES-GCM-wrap--> DEK (AES-256, encrypts files)
 *
 * The salt and wrapped DEK live inside the vault (.meta), so a reinstall only
 * needs the user's PIN to recover the DEK. Changing the PIN re-wraps the same
 * DEK — file contents are never re-encrypted.
 */
object KeyMaterial {

    /** Heavier than the login hash: derived rarely (unlock / PIN change). */
    const val KEK_ITERATIONS = 250_000
    private const val KEY_BITS = 256
    private val rng = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(32).also(rng::nextBytes)

    fun newNonce(): ByteArray = ByteArray(VaultFileFormat.NONCE_SIZE).also(rng::nextBytes)

    fun deriveKek(pin: CharArray, salt: ByteArray, iterations: Int = KEK_ITERATIONS): SecretKey {
        require(pin.isNotEmpty()) { "PIN must not be empty" }
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return try {
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun generateDek(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(KEY_BITS) }.generateKey()

    data class WrappedKey(val iv: ByteArray, val ciphertext: ByteArray)

    fun wrapDek(dek: SecretKey, kek: SecretKey): WrappedKey {
        val iv = newNonce()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(VaultFileFormat.TAG_BITS, iv))
        return WrappedKey(iv, cipher.doFinal(dek.encoded))
    }

    /** Unwraps [wrapped]; throws [VaultCryptoException] on any failure (fail-closed). */
    fun unwrapDek(wrapped: WrappedKey, kek: SecretKey): SecretKey = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(VaultFileFormat.TAG_BITS, wrapped.iv))
        SecretKeySpec(cipher.doFinal(wrapped.ciphertext), "AES")
    } catch (e: Exception) {
        throw VaultCryptoException("Wrong key or corrupted key material", e)
    }
}
