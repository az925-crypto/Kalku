package com.zaaaam.kalku.core.crypto

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chunked AES-256-GCM encryption for vault files.
 *
 * Why chunked: [javax.crypto.CipherInputStream] with GCM is notoriously unsafe
 * (tag verified only at EOF, exceptions swallowed). Here every record is sealed
 * and verified independently via explicit [Cipher.doFinal], truncation is
 * detected through the sealed end marker, and per-record nonces are derived by
 * counting — never reused under the same key.
 */
class ChunkedGcmCipher(
    private val dek: SecretKey,
    private val chunkSize: Int = DEFAULT_CHUNK,
    private val rng: SecureRandom = SecureRandom(),
) {

    companion object {
        const val DEFAULT_CHUNK: Int = 4 * 1024 * 1024
        private const val LEN_BYTES = 4
    }

    // ------------------------------------------------------------ write side

    /** Encrypts everything from [src] into [dst], writing header + records. */
    fun encrypt(src: InputStream, dst: OutputStream) {
        val baseNonce = ByteArray(VaultFileFormat.NONCE_SIZE).also(rng::nextBytes)
        dst.write(VaultFileFormat.build(baseNonce, chunkSize))
        val buf = ByteArray(chunkSize)
        var index = 0L
        while (true) {
            var off = 0
            while (off < chunkSize) {
                val r = src.read(buf, off, chunkSize - off)
                if (r == -1) break
                off += r
            }
            if (off > 0) {
                writeRecord(dst, baseNonce, index++, VaultFileFormat.RECORD_TYPE_DATA, buf.copyOf(off))
            }
            if (off < chunkSize) {
                writeRecord(dst, baseNonce, index, VaultFileFormat.RECORD_TYPE_END, VaultFileFormat.END_TOKEN)
                dst.flush()
                return
            }
        }
    }

    private fun writeRecord(dst: OutputStream, baseNonce: ByteArray, index: Long, type: Byte, plaintext: ByteArray) {
        val ct = seal(VaultFileFormat.recordNonce(baseNonce, index), plaintext)
        dst.write(intArrayOf(type.toInt()).toByteArray())
        dst.write(u32be(ct.size))
        dst.write(ct)
    }

    private fun seal(nonce: ByteArray, plaintext: ByteArray): ByteArray = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, dek, GCMParameterSpec(VaultFileFormat.TAG_BITS, nonce))
        cipher.doFinal(plaintext)
    } catch (e: Exception) {
        throw VaultCryptoException("Encryption failed", e)
    }

    // ------------------------------------------------------------- read side

    /**
     * Decrypts [src] into [dst]. Verifies every record's tag and requires the
     * end marker; wrong key, corruption or truncation all throw.
     */
    fun decrypt(src: InputStream, dst: OutputStream) {
        DecryptedInputStream(src, this).use { it.copyTo(dst) }
    }

    internal fun readHeader(src: InputStream): VaultFileFormat.Header {
        val head = ByteArray(VaultFileFormat.HEADER_SIZE)
        readFully(src, head)
        return VaultFileFormat.parse(head)
    }

    /** Reads and opens the next record; null at the verified end marker. */
    internal fun openRecord(src: InputStream, header: VaultFileFormat.Header, expectedIndex: Long): ByteArray? {
        val type = src.read()
        if (type == -1) throw VaultCryptoException("Truncated file (missing end marker)")
        val lenBytes = ByteArray(LEN_BYTES)
        readFully(src, lenBytes)
        val len = ((lenBytes[0].toInt() and 0xFF) shl 24) or
            ((lenBytes[1].toInt() and 0xFF) shl 16) or
            ((lenBytes[2].toInt() and 0xFF) shl 8) or
            (lenBytes[3].toInt() and 0xFF)
        val maxLen = when (type) {
            VaultFileFormat.RECORD_TYPE_END.toInt() -> VaultFileFormat.END_TOKEN.size + 16
            else -> header.chunkSize + 16
        }
        if (len <= 0 || len > maxLen) throw VaultCryptoException("Corrupt record length")
        val ct = ByteArray(len)
        readFully(src, ct)
        val pt = unseal(VaultFileFormat.recordNonce(header.baseNonce, expectedIndex), ct)
        return when (type) {
            VaultFileFormat.RECORD_TYPE_DATA.toInt() -> pt
            VaultFileFormat.RECORD_TYPE_END.toInt() -> {
                if (!pt.contentEquals(VaultFileFormat.END_TOKEN)) throw VaultCryptoException("Bad end marker")
                null
            }
            else -> throw VaultCryptoException("Unknown record type")
        }
    }

    private fun unseal(nonce: ByteArray, ciphertext: ByteArray): ByteArray = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, dek, GCMParameterSpec(VaultFileFormat.TAG_BITS, nonce))
        cipher.doFinal(ciphertext)
    } catch (e: Exception) {
        throw VaultCryptoException("Decryption failed — wrong key or corrupted file", e)
    }

    // -------------------------------------------------------- stream facade

    /** Lazily decrypted view of an encrypted vault file (for export/share/text). */
    fun decryptedStream(src: InputStream): InputStream = DecryptedInputStream(src, this)

    internal class DecryptedInputStream(
        private val raw: InputStream,
        private val owner: ChunkedGcmCipher,
    ) : InputStream() {

        private var header: VaultFileFormat.Header? = null
        private var plain: ByteArrayInputStream = ByteArrayInputStream(ByteArray(0))
        private var index = 0L
        private var done = false

        @Synchronized
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (done) return -1
            if (len == 0) return 0
            while (true) {
                val n = plain.read(b, off, len)
                if (n > 0) return n
                if (header == null) header = owner.readHeader(raw)
                val next = owner.openRecord(raw, header!!, index++) ?: run {
                    done = true
                    return -1
                }
                plain = ByteArrayInputStream(next)
            }
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == 1) one[0].toInt() and 0xFF else -1
        }

        @Synchronized
        override fun close() {
            done = true
            raw.close()
        }
    }

    // -------------------------------------------------------------- helpers

    private fun u32be(v: Int): ByteArray =
        byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    private fun readFully(src: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val r = src.read(buf, off, buf.size - off)
            if (r == -1) throw EOFException("Unexpected end of encrypted stream")
            off += r
        }
    }
}

private fun IntArray.toByteArray(): ByteArray = ByteArray(size) { this[it].toByte() }
