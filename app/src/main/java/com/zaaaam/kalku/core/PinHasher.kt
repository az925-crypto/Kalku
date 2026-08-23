package com.zaaaam.kalku.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory

/**
 * PIN hashing using PBKDF2WithHmacSHA256.
 * Storage format: "v1:<iterations>:<saltBase64>:<hashBase64>" — plain PIN never persisted.
 */
object PinHasher {

    private const val VERSION = "v1"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private val rng = SecureRandom()

    fun hash(pin: String): String {
        require(pin.isNotEmpty()) { "PIN must not be empty" }
        val salt = ByteArray(16).also(rng::nextBytes)
        val hash = pbkdf2(pin, salt, ITERATIONS)
        return listOf(VERSION, ITERATIONS.toString(), b64(salt), b64(hash)).joinToString(":")
    }

    fun verify(pin: String, stored: String?): Boolean {
        if (stored.isNullOrBlank()) return false
        val parts = stored.split(":")
        if (parts.size != 4 || parts[0] != VERSION) return false
        return try {
            val iterations = parts[1].toInt()
            val salt = unb64(parts[2])
            val expected = unb64(parts[3])
            val actual = pbkdf2(pin, salt, iterations)
            MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) {
            false
        }
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS))
            .encoded

    private fun b64(b: ByteArray): String = java.util.Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String): ByteArray = java.util.Base64.getDecoder().decode(s)
}
