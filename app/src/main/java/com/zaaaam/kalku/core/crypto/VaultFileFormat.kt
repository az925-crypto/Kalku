package com.zaaaam.kalku.core.crypto

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * On-disk format for encrypted vault files.
 *
 * Header (32 bytes, big-endian):
 * ```
 *  0..7   MAGIC  "KALKUENC" (ASCII)
 *  8      VERSION (currently 1)
 *  9      FLAGS (0)
 * 10..21  BASE_NONCE (12 bytes; per-record nonce = base + record index)
 * 22..25  CHUNK_SIZE in bytes (4 bytes)
 * 26..31  RESERVED (zeros)
 * ```
 *
 * After the header comes a sequence of length-prefixed AES-256-GCM records:
 * ```
 *  TYPE   (1 byte: 0 = data chunk, 1 = end marker)
 *  LEN    (4 bytes: ciphertext length including the 16-byte GCM tag)
 *  DATA   (LEN bytes)
 * ```
 * The end marker seals the 8-byte token "KALKUEND"; its presence proves the
 * file was written completely and was not truncated.
 */
object VaultFileFormat {

    val MAGIC: ByteArray = "KALKUENC".toByteArray(Charsets.US_ASCII)
    val END_TOKEN: ByteArray = "KALKUEND".toByteArray(Charsets.US_ASCII)

    const val VERSION: Int = 1
    const val HEADER_SIZE: Int = 32
    const val RECORD_TYPE_DATA: Byte = 0
    const val RECORD_TYPE_END: Byte = 1
    const val TAG_BITS: Int = 128
    const val NONCE_SIZE: Int = 12

    data class Header(val version: Int, val baseNonce: ByteArray, val chunkSize: Int)

    /** True when [header] starts with the magic bytes and a known version. */
    fun isEncrypted(header: ByteArray): Boolean =
        header.size >= MAGIC.size + 2 &&
            header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) &&
            header[MAGIC.size].toInt() and 0xFF == VERSION

    /** Convenience check that reads only the first bytes of [file]. */
    fun isEncrypted(file: File): Boolean {
        if (!file.isFile || file.length() < HEADER_SIZE) return false
        return try {
            val head = ByteArray(MAGIC.size + 2)
            RandomAccessFile(file, "r").use { raf ->
                if (raf.read(head) < head.size) return false
            }
            isEncrypted(head)
        } catch (_: IOException) {
            false
        }
    }

    fun parse(header: ByteArray): Header {
        if (!isEncrypted(header)) throw VaultCryptoException("Not a KALKU encrypted file")
        if (header.size < HEADER_SIZE) throw VaultCryptoException("Truncated header")
        val nonce = header.copyOfRange(10, 10 + NONCE_SIZE)
        val chunkSize =
            ((header[22].toInt() and 0xFF) shl 24) or
                ((header[23].toInt() and 0xFF) shl 16) or
                ((header[24].toInt() and 0xFF) shl 8) or
                (header[25].toInt() and 0xFF)
        if (chunkSize <= 0) throw VaultCryptoException("Invalid chunk size")
        return Header(VERSION, nonce, chunkSize)
    }

    fun build(baseNonce: ByteArray, chunkSize: Int): ByteArray {
        require(baseNonce.size == NONCE_SIZE) { "nonce must be $NONCE_SIZE bytes" }
        val out = ByteArray(HEADER_SIZE)
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.size)
        out[8] = VERSION.toByte()
        out[9] = 0 // flags
        System.arraycopy(baseNonce, 0, out, 10, NONCE_SIZE)
        out[22] = (chunkSize ushr 24).toByte()
        out[23] = (chunkSize ushr 16).toByte()
        out[24] = (chunkSize ushr 8).toByte()
        out[25] = chunkSize.toByte()
        // 26..31 remain zero (reserved)
        return out
    }

    /**
     * Per-record nonce: BASE + index as a 96-bit big-endian counter. Distinct for
     * every record of a file, preventing (key, nonce) reuse under one DEK.
     */
    fun recordNonce(base: ByteArray, index: Long): ByteArray {
        val n = base.copyOf()
        var carry = index
        for (i in NONCE_SIZE - 1 downTo 0) {
            if (carry == 0L) break
            val sum = (n[i].toInt() and 0xFF) + (carry and 0xFF).toInt()
            n[i] = (sum and 0xFF).toByte()
            carry = (carry ushr 8) + (sum ushr 8)
        }
        return n
    }
}
