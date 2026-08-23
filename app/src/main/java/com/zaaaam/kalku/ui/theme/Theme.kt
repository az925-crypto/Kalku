package com.zaaaam.kalku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.zaaaam.kalku.data.ThemeMode

data class Accent(val key: String, val label: String, val primary: Color)

val ACCENTS = listOf(
    Accent("teal", "Teal", Color(0xFF1B6E5A)),
    Accent("blue", "Blue", Color(0xFF1565C0)),
    Accent("indigo", "Indigo", Color(0xFF3949AB)),
    Accent("green", "Green", Color(0xFF2E7D32)),
    Accent("amber", "Amber", Color(0xFFB26A00)),
    Accent("rose", "Rose", Color(0xFFAD1457)),
)

fun kalkuScheme(accentKey: String, dark: Boolean): ColorScheme {
    val accent = ACCENTS.firstOrNull { it.key == accentKey } ?: ACCENTS.first()
    return if (dark) {
        darkColorScheme(
            primary = accent.primary,
            background = Color(0xFF0F1412),
            surface = Color(0xFF151B19),
            surfaceVariant = Color(0xFF202825),
            onPrimary = Color.White,
            onBackground = Color(0xFFE2E7E4),
            onSurface = Color(0xFFE2E7E4),
        )
    } else {
        lightColorScheme(
            primary = accent.primary,
            background = Color(0xFFF6F8F7),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFEDF1EF),
            onPrimary = Color.White,
            onBackground = Color(0xFF17201C),
            onSurface = Color(0xFF17201C),
        )
    }
}

@Composable
fun KalkuTheme(themeMode: ThemeMode, accentKey: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = kalkuScheme(accentKey, dark), content = content)
}
