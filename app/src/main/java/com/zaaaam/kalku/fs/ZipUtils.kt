package com.zaaaam.kalku.fs

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Minimal ZIP tooling: list, extract (Zip-Slip safe), create from files/folders. */
object ZipUtils {

    data class Entry(val name: String, val isDirectory: Boolean, val size: Long)

    fun list(zipFile: File): List<Entry> = ZipFile(zipFile).use { zf ->
        zf.entries().asSequence()
            .filterNot { it.name.contains("..") }
            .map { Entry(it.name, it.isDirectory, it.size) }
            .toList()
    }

    /**
     * Extracts every entry into [destDir].
     * Rejects entries whose resolved path escapes [destDir] (Zip Slip).
     */
    fun extractAll(zipFile: File, destDir: File): Int {
        destDir.mkdirs()
        val canonicalDest = destDir.canonicalPath + File.separator
        var count = 0
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.name.contains("..")) continue
                val out = File(destDir, entry.name)
                if (!out.canonicalPath.startsWith(canonicalDest)) continue // traversal attempt
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zis.copyTo(it) }
                    count++
                }
                zis.closeEntry()
            }
        }
        return count
    }

    /** Extracts a single [entryName] into [destDir]. Returns the written file or null. */
    fun extractEntry(zipFile: File, entryName: String, destDir: File): File? {
        destDir.mkdirs()
        val canonicalDest = destDir.canonicalPath + File.separator
        ZipFile(zipFile).use { zf ->
            val entry: ZipEntry = zf.getEntry(entryName) ?: return null
            val out = File(destDir, entry.name.substringAfterLast('/'))
            if (!out.canonicalPath.startsWith(canonicalDest)) return null
            zf.getInputStream(entry).use { input -> FileOutputStream(out).use { input.copyTo(it) } }
            return out
        }
    }

    /** Zips [sources] (files and/or folders, recursively) into [outZip]. */
    fun compress(sources: List<File>, outZip: File) {
        outZip.parentFile?.mkdirs()
        ZipOutputStream(outZip.outputStream().buffered()).use { zos ->
            for (src in sources) {
                val baseName = src.name
                if (src.isDirectory) {
                    src.walkTopDown().forEach { f ->
                        val rel = if (f == src) baseName else "$baseName/${f.relativeTo(src).path}"
                        put(zos, f, rel)
                    }
                } else {
                    put(zos, src, baseName)
                }
            }
        }
    }

    private fun put(zos: ZipOutputStream, f: File, rel: String) {
        val entry = ZipEntry(if (f.isDirectory && !rel.endsWith("/")) "$rel/" else rel)
        entry.time = f.lastModified()
        zos.putNextEntry(entry)
        if (f.isFile) f.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
    }
}
