package com.zaaaam.kalku

import com.zaaaam.kalku.core.Category
import com.zaaaam.kalku.core.CategoryDetector
import com.zaaaam.kalku.core.Format
import com.zaaaam.kalku.core.Names
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryDetectorTest {

    private fun header(vararg bytes: Int): ByteArray = bytes.map { it.toByte() }.toByteArray()

    @Test fun magicBytesWin() {
        assertEquals(Category.IMAGE, CategoryDetector.detect("renamed.bin", header(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)))
        assertEquals(Category.IMAGE, CategoryDetector.detect("photo.dat", header(0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0, 0, 0, 0, 0)))
        assertEquals(Category.DOCUMENT, CategoryDetector.detect("doc.dat", "%PDF-1.7".toByteArray() + ByteArray(6)))
        assertEquals(Category.ARCHIVE, CategoryDetector.detect("pack.dat", header(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0)))
        assertEquals(Category.AUDIO, CategoryDetector.detect("song.dat", "ID3\u0004".toByteArray() + ByteArray(8)))
        assertEquals(Category.VIDEO, CategoryDetector.detect("movie.dat", header(0x1A, 0x45, 0xDF, 0xA3, 0, 0, 0, 0, 0, 0, 0, 0))) // mkv/webm
    }

    @Test fun isoBrandDetection() {
        val ftyp = { brand: String -> byteArrayOf(0, 0, 0, 24) + "ftyp".toByteArray() + brand.toByteArray() }
        assertEquals(Category.IMAGE, CategoryDetector.detect("a.dat", ftyp("heic")))
        assertEquals(Category.IMAGE, CategoryDetector.detect("a.dat", ftyp("avif")))
        assertEquals(Category.VIDEO, CategoryDetector.detect("a.dat", ftyp("isom")))
        assertEquals(Category.AUDIO, CategoryDetector.detect("a.m4a", ftyp("M4A ")))
    }

    @Test fun riffContainers() {
        val wav = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WAVE".toByteArray()
        val avi = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "AVI ".toByteArray()
        val webp = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBP".toByteArray()
        assertEquals(Category.AUDIO, CategoryDetector.detect("s.wav", wav))
        assertEquals(Category.VIDEO, CategoryDetector.detect("v.avi", avi))
        assertEquals(Category.IMAGE, CategoryDetector.detect("i.webp", webp))
    }

    @Test fun officeDocsOverrideZipSignature() {
        assertEquals(
            Category.DOCUMENT,
            CategoryDetector.detect("report.docx", header(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0)),
        )
        assertEquals(
            Category.DOCUMENT,
            CategoryDetector.detect("sheet.xlsx", header(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0)),
        )
    }

    @Test fun extensionFallbackWhenNoHeader() {
        assertEquals(Category.CODE, CategoryDetector.detect("main.kt"))
        assertEquals(Category.VIDEO, CategoryDetector.detect("clip.mkv"))
        assertEquals(Category.ARCHIVE, CategoryDetector.detect("backup.tar.gz"))
        assertEquals(Category.OTHER, CategoryDetector.detect("unknown.xyz"))
        assertEquals(Category.OTHER, CategoryDetector.detect("noextension"))
    }

    @Test fun mimeMapping() {
        assertEquals("image/png", CategoryDetector.mimeOf(Category.IMAGE, "x.png"))
        assertEquals("application/pdf", CategoryDetector.mimeOf(Category.DOCUMENT, "x.pdf"))
        assertEquals("application/octet-stream", CategoryDetector.mimeOf(Category.OTHER, "x.bin"))
    }
}

class NamesFormatTest {

    @Test fun sanitizeStripsIllegal() {
        assertEquals("my file.txt", Names.sanitizeFileName(" my file.txt "))
        assertEquals("abc", Names.sanitizeFileName("a/b\\c"))
        assertEquals("untitled", Names.sanitizeFileName(".."))
        assertEquals("untitled", Names.sanitizeFileName(""))
    }

    @Test fun uniqueNameAvoidsCollisions() {
        val taken = setOf("photo.jpg")
        assertEquals("photo (2).jpg", Names.uniqueName("photo.jpg", taken))
        assertEquals("photo (3).jpg", Names.uniqueName("photo.jpg", taken + "photo (2).jpg"))
        assertEquals("new.png", Names.uniqueName("new.png", setOf("other.png")))
        assertEquals("archive (2)", Names.uniqueName("archive", taken))
    }

    @Test fun byteFormatting() {
        assertEquals("512 B", Format.bytes(512))
        assertEquals("1.0 KB", Format.bytes(1024))
        assertEquals("1.5 MB", Format.bytes((1024 * 1024 * 1.5).toLong()))
        assertEquals("2.0 GB", Format.bytes((1024L * 1024 * 1024 * 2).toLong()))
    }

    @Test fun numberFormatting() {
        assertEquals("42", Format.number(42.0))
        assertEquals("0.5", Format.number(0.5))
        assertEquals("∞", Format.number(Double.POSITIVE_INFINITY))
        assertEquals("-∞", Format.number(Double.NEGATIVE_INFINITY))
        assertEquals("Error", Format.number(Double.NaN))
        assertEquals("0", Format.number(-0.0))
    }
}
