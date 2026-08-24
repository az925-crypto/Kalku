package com.zaaaam.kalku

import com.zaaaam.kalku.core.crypto.ChunkedGcmCipher
import com.zaaaam.kalku.core.crypto.KeyMaterial
import com.zaaaam.kalku.core.crypto.VaultCryptoException
import com.zaaaam.kalku.core.crypto.VaultFileFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.crypto.SecretKey

class CryptoLayerTest {

    private fun dek(): SecretKey = KeyMaterial.generateDek()

    // ------------------------------------------------------- VaultFileFormat

    @org.junit.Test
    fun `header build and parse roundtrip`() {
        val nonce = KeyMaterial.newNonce()
        val header = VaultFileFormat.build(nonce, ChunkedGcmCipher.DEFAULT_CHUNK)
        assertEquals(VaultFileFormat.HEADER_SIZE, header.size)
        val parsed = VaultFileFormat.parse(header)
        assertArrayEquals(nonce, parsed.baseNonce)
        assertEquals(ChunkedGcmCipher.DEFAULT_CHUNK, parsed.chunkSize)
        assertEquals(VaultFileFormat.VERSION, parsed.version)
    }

    @org.junit.Test
    fun `isEncrypted detects magic and rejects garbage`() {
        assertTrue(VaultFileFormat.isEncrypted(VaultFileFormat.build(KeyMaterial.newNonce(), 1024)))
        assertFalse(VaultFileFormat.isEncrypted(ByteArray(64)))
        assertFalse(VaultFileFormat.isEncrypted(ByteArray(0)))
        val tampered = VaultFileFormat.build(KeyMaterial.newNonce(), 1024).also { it[8] = 9 }
        assertFalse(VaultFileFormat.isEncrypted(tampered))
    }

    @org.junit.Test
    fun `record nonces never collide for sequential indices`() {
        val base = KeyMaterial.newNonce()
        val seen = HashSet<String>()
        for (i in 0 until 10_000L) seen.add(VaultFileFormat.recordNonce(base, i).contentToString())
        assertEquals(10_000, seen.size)
        // Distinct bases give distinct nonces at the same index.
        assertNotEquals(
            VaultFileFormat.recordNonce(base, 1).contentToString(),
            VaultFileFormat.recordNonce(KeyMaterial.newNonce(), 1).contentToString(),
        )
    }

    // ------------------------------------------------------------ KeyMaterial

    @org.junit.Test
    fun `wrap and unwrap roundtrip`() {
        val kek = KeyMaterial.deriveKek("123456".toCharArray(), KeyMaterial.newSalt())
        val dek = dek()
        val wrapped = KeyMaterial.wrapDek(dek, kek)
        val restored = KeyMaterial.unwrapDek(wrapped, kek)
        assertArrayEquals(dek.encoded, restored.encoded)
    }

    @org.junit.Test(expected = VaultCryptoException::class)
    fun `unwrap with wrong key fails closed`() {
        val kekA = KeyMaterial.deriveKek("111111".toCharArray(), KeyMaterial.newSalt())
        val kekB = KeyMaterial.deriveKek("222222".toCharArray(), KeyMaterial.newSalt())
        val wrapped = KeyMaterial.wrapDek(dek(), kekA)
        KeyMaterial.unwrapDek(wrapped, kekB)
    }

    @org.junit.Test
    fun `salts are unique per call`() {
        val a = KeyMaterial.newSalt()
        val b = KeyMaterial.newSalt()
        assertFalse(a.contentEquals(b))
    }

    // -------------------------------------------------------- ChunkedGcmCipher

    private fun roundtrip(data: ByteArray, chunkSize: Int): ByteArray {
        val key = dek() // one shared key across the whole roundtrip
        val cipher = ChunkedGcmCipher(key, chunkSize)
        val enc = ByteArrayOutputStream()
        cipher.encrypt(ByteArrayInputStream(data), enc)
        assertTrue("output should carry the header", enc.size() > VaultFileFormat.HEADER_SIZE)
        val dec = ChunkedGcmCipher(key, chunkSize)
        val out = ByteArrayOutputStream()
        dec.decrypt(ByteArrayInputStream(enc.toByteArray()), out)
        return out.toByteArray()
    }

    @org.junit.Test
    fun `roundtrip sizes around chunk boundary`() {
        val small = byteArrayOf(1, 2, 3)
        assertArrayEquals(small, roundtrip(small, 16))

        val empty = ByteArray(0)
        assertArrayEquals(empty, roundtrip(empty, 16))

        val exact = ByteArray(32) { it.toByte() }
        assertArrayEquals(exact, roundtrip(exact, 16))

        val over = ByteArray(33) { (it * 7).toByte() }
        assertArrayEquals(over, roundtrip(over, 16))

        val big = ByteArray(1000) { (it * 13 % 256).toByte() }
        assertArrayEquals(big, roundtrip(big, 128))
    }

    @org.junit.Test
    fun `default chunk size handles multi megabyte stream`() {
        val data = ByteArray(ChunkedGcmCipher.DEFAULT_CHUNK * 2 + 12345).also {
            java.util.Random(42).nextBytes(it)
        }
        assertArrayEquals(data, roundtrip(data, ChunkedGcmCipher.DEFAULT_CHUNK))
    }

    private fun encryptWith(data: ByteArray, key: SecretKey, chunkSize: Int = 64): ByteArray {
        val cipher = ChunkedGcmCipher(key, chunkSize)
        val enc = ByteArrayOutputStream()
        cipher.encrypt(ByteArrayInputStream(data), enc)
        return enc.toByteArray()
    }

    @org.junit.Test(expected = VaultCryptoException::class)
    fun `decrypt with wrong key throws`() {
        val blob = encryptWith("secret".toByteArray(), dek())
        ChunkedGcmCipher(dek(), 64).decrypt(ByteArrayInputStream(blob), ByteArrayOutputStream())
    }

    @org.junit.Test(expected = VaultCryptoException::class)
    fun `truncated ciphertext fails end-marker check`() {
        val blob = encryptWith(ByteArray(500) { it.toByte() }, dek())
        val truncated = blob.copyOfRange(0, blob.size - 5)
        ChunkedGcmCipher(dek(), 64).decrypt(ByteArrayInputStream(truncated), ByteArrayOutputStream())
    }

    @org.junit.Test(expected = VaultCryptoException::class)
    fun `flipped ciphertext bit fails tag verification`() {
        val blob = encryptWith("tamper detection".toByteArray(), dek())
        blob[blob.size / 2] = (blob[blob.size / 2].toInt() xor 0x41).toByte()
        ChunkedGcmCipher(dek(), 64).decrypt(ByteArrayInputStream(blob), ByteArrayOutputStream())
    }

    @org.junit.Test
    fun `decrypted stream yields plaintext lazily`() {
        val data = ByteArray(300) { (it * 3 % 251).toByte() }
        val key = dek()
        val blob = encryptWith(data, key, 64)
        val plainStream = ChunkedGcmCipher(key, 64).decryptedStream(ByteArrayInputStream(blob))
        plainStream.use { readAll ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(37) // awkward size to exercise buffering
            while (true) {
                val n = readAll.read(buf)
                if (n == -1) break
                out.write(buf, 0, n)
            }
            assertArrayEquals(data, out.toByteArray())
        }
    }

    @org.junit.Test
    fun `same plaintext twice produces different ciphertext`() {
        val data = "nonce uniqueness".toByteArray()
        val a = encryptWith(data, dek())
        val b = encryptWith(data, dek())
        assertFalse(a.contentEquals(b))
    }

    // -------------------------------------------------- file-level integration

    @org.junit.Test
    fun `file header detection matches format`() {
        val tmp = File.createTempFile("kalku_enc", ".bin")
        try {
            val f = File(tmp.parentFile, "enc_${System.nanoTime()}.bin")
            val cipher = ChunkedGcmCipher(dek(), 64)
            cipher.encrypt(ByteArrayInputStream("hello".toByteArray()), f.outputStream())
            assertTrue(VaultFileFormat.isEncrypted(f))
            assertFalse(VaultFileFormat.isEncrypted(File(tmp.path))) // empty/garbage file
            f.delete()
        } finally {
            tmp.delete()
        }
    }
}
