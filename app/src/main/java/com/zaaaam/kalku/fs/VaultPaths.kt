package com.zaaaam.kalku.fs

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Vault storage resolution.
 *
 * Primary location is a hidden folder on the public shared storage
 * (`/storage/emulated/0/.KalkuVault`) so files SURVIVE uninstall/reinstall.
 * It requires "All files access" (API 30+) or legacy write permission (API < 30).
 *
 * Fallback is the app-specific external dir (lost on uninstall) so the app stays
 * usable before the user grants access; a warning surfaces in Settings.
 */
object VaultPaths {

    const val DIR_NAME = ".KalkuVault"
    const val TRASH_DIR = ".Trash"

    /** Default folder structure created on first run. */
    val DEFAULT_FOLDERS = listOf("Photos", "Videos", "Audio", "Documents", "Code", "Archives", "Others")

    data class Storage(val root: File, val isFallback: Boolean)

    fun hasFullAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 30) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun resolve(context: Context): Storage {
        if (hasFullAccess(context)) {
            try {
                val base = Environment.getExternalStorageDirectory()
                if (base != null) {
                    val root = File(base, DIR_NAME)
                    if ((root.exists() && root.isDirectory) || root.mkdirs() || root.isDirectory) {
                        return Storage(root, isFallback = false)
                    }
                }
            } catch (_: Exception) {
                // fall through to private storage
            }
        }
        val priv = context.getExternalFilesDir(null) ?: context.filesDir
        return Storage(File(priv, DIR_NAME), isFallback = true)
    }

    fun trashDir(root: File): File = File(root, TRASH_DIR)

    fun metaDir(root: File): File = File(root, ".meta")
}
