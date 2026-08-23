package com.zaaaam.kalku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zaaaam.kalku.R
import com.zaaaam.kalku.data.ThemeMode

/** Two curated theme packs; each has a light and a dark scheme. */
enum class ThemePack(val label: String, val description: String) {
    /** Light = Atelier Paper (warm paper, vermillion accent, teal actions). Dark = Precision Archive (graphite + copper). */
    PRECISION("Precision", "Kertas hangat · vermillion / grafit · copper"),

    /** Light = Paper Terra Expressive (gobi paper + ember). Dark = Dark Terra (obsidian night + ember nova). */
    TERRA("Terra", "Material You hangat · ember"),
}

// ------------------------------------------------------------------- fonts

val InstrumentSerif = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.instrument_serif, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(
        R.font.instrument_serif_italic,
        FontWeight.Normal,
        androidx.compose.ui.text.font.FontStyle.Italic,
    ),
)

val DMSans = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.dmsans_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.dmsans_medium, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(R.font.dmsans_bold, FontWeight.Bold),
)

val InterFont = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.inter_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.inter_medium, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(R.font.inter_semibold, FontWeight.SemiBold),
)

val SoraFont = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.sora_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(R.font.sora_bold, FontWeight.Bold),
)

/** Tabular numerals for the calculator, editor gutter and code. Shared by both packs. */
val MonoNumbers = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.jbmono_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.jbmono_medium, FontWeight.Medium),
)

// ------------------------------------------------------------- category hues

/** Stable per-category hues used by storage bars, tiles and glyphs (both packs). */
fun categoryColor(category: String): Color = when (category) {
    "IMAGE" -> Color(0xFF2A6B5A)
    "VIDEO" -> Color(0xFF4A6FA5)
    "AUDIO" -> Color(0xFF8A6A2A)
    "DOCUMENT" -> Color(0xFF6B4A7A)
    "CODE" -> Color(0xFF8E6BB0)
    "ARCHIVE" -> Color(0xFFB3542E)
    else -> Color(0xFF7A756B)
}

// --------------------------------------------------------------- typography

private fun precisionTypography() = Typography(
    displayLarge = TextStyle(fontSize = 44.sp, fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal),
    displayMedium = TextStyle(fontSize = 36.sp, fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal),
    displaySmall = TextStyle(fontSize = 30.sp, fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal),
    headlineLarge = TextStyle(fontSize = 32.sp, fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal),
    headlineMedium = TextStyle(fontSize = 27.sp, fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontSize = 23.sp, fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 19.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, fontFamily = DMSans, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, fontFamily = DMSans, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 11.5.sp, fontFamily = DMSans, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 11.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium),
)

private fun terraTypography() = Typography(
    displayLarge = TextStyle(fontSize = 42.sp, fontFamily = SoraFont, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 34.sp, fontFamily = SoraFont, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 28.sp, fontFamily = SoraFont, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 30.sp, fontFamily = SoraFont, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 25.sp, fontFamily = SoraFont, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 21.sp, fontFamily = SoraFont, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontFamily = InterFont, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.5.sp, fontFamily = InterFont, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, fontFamily = InterFont, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, fontFamily = InterFont, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, fontFamily = InterFont, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 11.5.sp, fontFamily = InterFont, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, fontFamily = InterFont, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 11.sp, fontFamily = InterFont, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, fontFamily = InterFont, fontWeight = FontWeight.Medium),
)

// ------------------------------------------------------------------ schemes

private fun precisionLight() = lightColorScheme(
    primary = Color(0xFF2A6B5A),            // teal actions (save, links)
    onPrimary = Color(0xFFFFFBF0),
    primaryContainer = Color(0xFFE6F0EB),
    onPrimaryContainer = Color(0xFF1E4E3F),
    secondary = Color(0xFF6B4A7A),
    secondaryContainer = Color(0xFFEDE2F0),
    onSecondaryContainer = Color(0xFF4A3555),
    tertiary = Color(0xFFD6402A),           // vermillion accent: '=', FAB, dirty dot
    onTertiary = Color(0xFFFFFBF0),
    tertiaryContainer = Color(0xFFFFF1EF),
    onTertiaryContainer = Color(0xFF8A3A2E),
    background = Color(0xFFFFFBF0),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFF3EDE0),     // paper2: util keys, chips, gutters
    onSurfaceVariant = Color(0xFF6E685C),
    outline = Color(0xFFB9AE99),
    outlineVariant = Color(0xFFE4DAC5),
    error = Color(0xFFD6402A),
    errorContainer = Color(0xFFFFF1EF),
    onErrorContainer = Color(0xFF8A3A2E),
)

private fun precisionDark() = darkColorScheme(
    primary = Color(0xFFEC9C4A),            // copper — brighter, less muddy
    onPrimary = Color(0xFF241708),
    primaryContainer = Color(0xFF3A2E1F),   // operator keys — warmer
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = Color(0xFFB8D0B8),          // vault sage — brighter
    secondaryContainer = Color(0xFF1E2E24),
    onSecondaryContainer = Color(0xFFC8E8C8),
    tertiary = Color(0xFFEC9C4A),
    onTertiary = Color(0xFF241708),
    tertiaryContainer = Color(0xFF4A3420),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF1C1917),         // lifted from #12100E
    onBackground = Color(0xFFF0E6D0),
    surface = Color(0xFF23201E),            // lifted
    onSurface = Color(0xFFF0E6D0),
    surfaceVariant = Color(0xFF2F2B25),     // digit keys — more contrast
    onSurfaceVariant = Color(0xFFC2B8A8),
    outline = Color(0xFF8A7F6E),
    outlineVariant = Color(0xFF3A3530),
    error = Color(0xFFFFB4A5),
    errorContainer = Color(0xFF3D1F1A),
    onErrorContainer = Color(0xFFFFDAD1),
)

private fun terraLight() = lightColorScheme(
    primary = Color(0xFFE64626),            // ember: '=', FAB, selection
    onPrimary = Color(0xFFFFFBF0),
    primaryContainer = Color(0xFFFFE5DE),
    onPrimaryContainer = Color(0xFF7A2415),
    secondary = Color(0xFF1E4E3F),
    secondaryContainer = Color(0xFFD7E0D2), // sage mist: operator keys
    onSecondaryContainer = Color(0xFF1E4E3F),
    tertiary = Color(0xFF4A6FA5),
    tertiaryContainer = Color(0xFFE3ECF6),
    onTertiaryContainer = Color(0xFF2E4A70),
    background = Color(0xFFF8F3EB),         // gobi paper
    onBackground = Color(0xFF121412),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121412),
    surfaceVariant = Color(0xFFEDE8DF),     // stone
    onSurfaceVariant = Color(0xFF6E6A60),
    outline = Color(0xFFA8A296),
    outlineVariant = Color(0xFFDDD6C8),
    error = Color(0xFFD6402A),
    errorContainer = Color(0xFFFFF1EF),
    onErrorContainer = Color(0xFF8A3A2E),
)

private fun terraDark() = darkColorScheme(
    primary = Color(0xFFFF6B3D),            // ember nova — brighter
    onPrimary = Color(0xFF1C0D08),
    primaryContainer = Color(0xFF4A2A1D),
    onPrimaryContainer = Color(0xFFFFD8CC),
    secondary = Color(0xFFD0E0C8),          // sage — brighter
    secondaryContainer = Color(0xFF2E3F36), // sage nocturne — lifted
    onSecondaryContainer = Color(0xFFD0E0C8),
    tertiary = Color(0xFF9EC3E8),
    tertiaryContainer = Color(0xFF2B3A4A),
    onTertiaryContainer = Color(0xFFC2D8EF),
    background = Color(0xFF1A1E1C),         // lifted from #141815
    onBackground = Color(0xFFF5EDE0),       // paper moon — brighter
    surface = Color(0xFF242926),            // surface char — lifted
    onSurface = Color(0xFFF5EDE0),
    surfaceVariant = Color(0xFF2F3530),
    onSurfaceVariant = Color(0xFFB5BDB8),
    outline = Color(0xFF8A948E),
    outlineVariant = Color(0xFF3A4040),     // soot line — more visible
    error = Color(0xFFFF8A6B),
    errorContainer = Color(0xFF3A2520),
    onErrorContainer = Color(0xFFFFD8CC),
)

// --------------------------------------------------------------- resolution

data class ResolvedTheme(val scheme: ColorScheme, val typography: Typography)

fun resolveTheme(pack: ThemePack, dark: Boolean): ResolvedTheme = when (pack) {
    ThemePack.PRECISION ->
        if (dark) ResolvedTheme(precisionDark(), precisionTypography())
        else ResolvedTheme(precisionLight(), precisionTypography())
    ThemePack.TERRA ->
        if (dark) ResolvedTheme(terraDark(), terraTypography())
        else ResolvedTheme(terraLight(), terraTypography())
}

@Composable
fun KalkuTheme(
    themeMode: ThemeMode,
    pack: ThemePack,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val resolved = resolveTheme(pack, dark)
    MaterialTheme(
        colorScheme = resolved.scheme,
        typography = resolved.typography,
        content = content,
    )
}
