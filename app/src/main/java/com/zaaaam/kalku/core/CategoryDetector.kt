package com.zaaaam.kalku.core

/** Vault file categories. Order matters for the dashboard layout. */
enum class Category(val label: String) {
    IMAGE("Photos"),
    VIDEO("Videos"),
    AUDIO("Audio"),
    DOCUMENT("Documents"),
    CODE("Code"),
    ARCHIVE("Archives"),
    OTHER("Others");

    companion object {
        fun from(name: String): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/**
 * File type detection: magic-byte signature first, extension second.
 * Lets files stay categorized even with wrong/unknown/missing extensions.
 */
object CategoryDetector {

    /** Result of signature sniffing. */
    private sealed class Sig {
        data object None : Sig()
        data class Of(val category: Category) : Sig()
        /** ZIP container: real type depends on extension (docx = document, zip = archive). */
        data object Zip : Sig()
        /** ISO-BMFF 'ftyp' box: brand decides image/video/audio. */
        data class IsoBrand(val brand: String) : Sig()
        /** RIFF container: sub-format in bytes 8..12. */
        data class Riff(val form: String) : Sig()
    }

    private val extToCategory: Map<String, Category> = buildMap {
        putAll(listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "svg", "ico", "tif", "tiff").associateWith { Category.IMAGE })
        putAll(listOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "wmv", "mpg", "mpeg", "m4v", "ts", "ogv").associateWith { Category.VIDEO })
        putAll(listOf("mp3", "wav", "flac", "m4a", "aac", "ogg", "oga", "opus", "wma", "amr", "mid", "midi", "aiff").associateWith { Category.AUDIO })
        putAll(listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "txt", "md", "rtf", "odt", "ods", "odp", "epub", "log").associateWith { Category.DOCUMENT })
        putAll(listOf(
            "js", "ts", "jsx", "tsx", "py", "html", "htm", "css", "scss", "json", "xml", "java", "kt", "kts",
            "c", "h", "cpp", "hpp", "cc", "cs", "sql", "sh", "bash", "zsh", "bat", "ps1", "yaml", "yml",
            "toml", "ini", "cfg", "conf", "gradle", "php", "rb", "go", "rs", "swift", "dart", "lua", "pl", "r"
        ).associateWith { Category.CODE })
        putAll(listOf("zip", "7z", "tar", "gz", "bz2", "xz", "rar", "tgz", "apk", "jar", "iso").associateWith { Category.ARCHIVE })
    }

    fun detect(fileName: String, header: ByteArray?): Category {
        val ext = extensionOf(fileName).lowercase()

        // Extension override first for zip-based office documents (their sig is a plain zip).
        if (ext in setOf("docx", "xlsx", "pptx", "odt", "ods", "odp", "epub")) return extToCategory[ext]!!

        when (val sig = sniff(header)) {
            is Sig.Of -> return sig.category
            is Sig.IsoBrand -> return isoCategory(sig.brand, ext)
            is Sig.Riff -> return riffCategory(sig.form, ext)
            Sig.Zip -> return Category.ARCHIVE // jar/apk/plain-zip are all archives
            Sig.None -> {}
        }
        val byExt = extToCategory[ext]
        if (byExt != null) return byExt

        // Text-ish sniff fallback: XML/HTML content without extension.
        if (header != null && looksLikeText(header)) {
            val head = header.toString(Charsets.ISO_8859_1).lowercase().trimStart('\ufeff', ' ', '\t', '\n', '\r')
            if (head.startsWith("<svg") || head.startsWith("<?xml") && head.contains("<svg")) return Category.IMAGE
            if (head.startsWith("<!doctype html") || head.startsWith("<html")) return Category.CODE
            return Category.DOCUMENT
        }
        return Category.OTHER
    }

    fun detect(fileName: String): Category = detect(fileName, null)

    fun mimeOf(category: Category, fileName: String): String {
        val ext = extensionOf(fileName).lowercase()
        val known = mapOf(
            "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif",
            "webp" to "image/webp", "svg" to "image/svg+xml", "heic" to "image/heic",
            "mp4" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm", "mov" to "video/quicktime",
            "mp3" to "audio/mpeg", "wav" to "audio/wav", "flac" to "audio/flac", "ogg" to "audio/ogg",
            "m4a" to "audio/mp4", "pdf" to "application/pdf", "txt" to "text/plain", "md" to "text/markdown",
            "csv" to "text/csv", "html" to "text/html", "css" to "text/css", "js" to "text/javascript",
            "json" to "application/json", "xml" to "application/xml", "zip" to "application/zip",
            "7z" to "application/x-7z-compressed", "rar" to "application/vnd.rar", "gz" to "application/gzip",
            "tar" to "application/x-tar", "apk" to "application/vnd.android.package-archive",
        )
        known[ext]?.let { return it }
        return when (category) {
            Category.IMAGE -> "image/*"
            Category.VIDEO -> "video/*"
            Category.AUDIO -> "audio/*"
            Category.DOCUMENT -> "application/octet-stream"
            Category.CODE -> "text/plain"
            Category.ARCHIVE -> "application/octet-stream"
            Category.OTHER -> "application/octet-stream"
        }
    }

    fun extensionOf(name: String): String {
        val idx = name.lastIndexOf('.')
        return if (idx <= 0 || idx == name.length - 1) "" else name.substring(idx + 1)
    }

    private fun sniff(h: ByteArray?): Sig {
        if (h == null || h.size < 12) return Sig.None
        fun s(off: Int, len: Int): String = h.drop(off).take(len).toByteArray().toString(Charsets.ISO_8859_1)
        fun m(off: Int, vararg b: Int): Boolean =
            b.indices.all { off + it < h.size && (h[off + it].toInt() and 0xFF) == b[it] }

        return when {
            m(0, 0x89, 0x50, 0x4E, 0x47) -> Sig.Of(Category.IMAGE)          // PNG
            m(0, 0xFF, 0xD8, 0xFF) -> Sig.Of(Category.IMAGE)                // JPEG
            s(0, 3) == "GIF" -> Sig.Of(Category.IMAGE)
            s(0, 4) == "RIFF" && h.size >= 12 -> Sig.Riff(s(8, 4))
            s(4, 4) == "ftyp" -> Sig.IsoBrand(s(8, 4))
            m(0, 0x25, 0x50, 0x44, 0x46) -> Sig.Of(Category.DOCUMENT)       // %PDF
            m(0, 0x50, 0x4B) -> Sig.Zip                                     // PK
            m(0, 0x49, 0x44, 0x33) -> Sig.Of(Category.AUDIO)                // ID3
            (h[0].toInt() and 0xFF) == 0xFF && ((h[1].toInt() and 0xE0) == 0xE0) -> Sig.Of(Category.AUDIO) // MP3 frame
            s(0, 4) == "OggS" -> Sig.Of(Category.AUDIO)
            s(0, 4) == "fLaC" -> Sig.Of(Category.AUDIO)
            m(0, 0x1A, 0x45, 0xDF, 0xA3) -> Sig.Of(Category.VIDEO)          // Matroska/WebM
            s(0, 2) == "7z" -> Sig.Of(Category.ARCHIVE)
            s(0, 4) == "Rar!" -> Sig.Of(Category.ARCHIVE)
            m(0, 0x1F, 0x8B) -> Sig.Of(Category.ARCHIVE)                    // gzip
            s(0, 3) == "BZh" -> Sig.Of(Category.ARCHIVE)                    // bzip2
            m(0, 0xFD, 0x37, 0x7A, 0x58, 0x5A) -> Sig.Of(Category.ARCHIVE)  // xz
            h.size > 262 && s(257, 5) == "ustar" -> Sig.Of(Category.ARCHIVE)
            else -> Sig.None
        }
    }

    private fun isoCategory(brand: String, ext: String): Category {
        val b = brand.lowercase()
        return when {
            b.startsWith("hei") || b.startsWith("mif") || b.startsWith("avif") -> Category.IMAGE
            b.startsWith("qt") && ext == "mov" -> Category.VIDEO
            b.startsWith("M4A".lowercase()) -> Category.AUDIO
            b.startsWith("m4b") || b.startsWith("m4p") -> Category.AUDIO
            else -> Category.VIDEO
        }
    }

    private fun riffCategory(form: String, ext: String): Category = when {
        form == "WAVE" -> Category.AUDIO
        form == "AVI " -> Category.VIDEO
        form == "WEBP" -> Category.IMAGE
        else -> extToCategory[ext.lowercase()] ?: Category.OTHER
    }

    private fun looksLikeText(h: ByteArray): Boolean {
        val sample = h.take(512).toByteArray()
        var suspicious = 0
        for (b in sample) {
            if (b.toInt() == 0) return false
            if ((b < 9) || (b in 14..31)) suspicious++
        }
        return suspicious * 20 < sample.size // tolerate <5% control chars
    }
}
