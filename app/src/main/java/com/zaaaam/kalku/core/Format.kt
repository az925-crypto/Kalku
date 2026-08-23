package com.zaaaam.kalku.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {

    fun bytes(n: Long): String {
        if (n < 0) return "?"
        if (n < 1024) return "$n B"
        val units = listOf("KB", "MB", "GB", "TB")
        var v = n.toDouble()
        for (u in units) {
            v /= 1024.0
            if (v < 1024) return String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, u)
        }
        return String.format(Locale.US, "%.1f PB", v / 1024.0)
    }

    fun date(ts: Long, pattern: String = "dd MMM yyyy HH:mm"): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ts))

    fun millis(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }

    /** Human number: strips float noise, caps decimals. */
    fun number(value: Double, precision: Int = 10): String {
        if (value.isNaN()) return "Error"
        if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
        val abs = Math.abs(value)
        if (abs != 0.0 && (abs >= 1e12 || abs < 1e-9)) return String.format(Locale.US, "%.6E", value)
        val p = precision.coerceIn(0, 12)
        var s = String.format(Locale.US, "%.${p}f", value)
        if (s.contains('.')) {
            s = s.trimEnd('0').trimEnd('.')
        }
        // normalize "-0"
        if (s == "-0") s = "0"
        return s
    }
}

/** File-name and path helpers shared by the vault layer. */
object Names {

    private val illegal = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')

    fun sanitizeFileName(raw: String): String {
        val cleaned = raw.trim().filter { c -> !illegal.contains(c) && !c.isISOControl() }
        return when {
            cleaned.isEmpty() || cleaned == "." || cleaned == ".." -> "untitled"
            else -> cleaned
        }
    }

    /** Returns a name that does not collide with [taken]; appends " (2)", " (3)"… before the extension. */
    fun uniqueName(desired: String, taken: Set<String>): String {
        if (!taken.contains(desired)) return desired
        val dot = desired.lastIndexOf('.')
        val base = if (dot <= 0) desired else desired.substring(0, dot)
        val ext = if (dot <= 0) "" else desired.substring(dot)
        var n = 2
        while (true) {
            val candidate = "$base ($n)$ext"
            if (!taken.contains(candidate)) return candidate
            n++
        }
    }
}
