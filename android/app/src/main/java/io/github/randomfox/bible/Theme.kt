package io.github.randomfox.bible

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class BibleColors(
    val bar: Color, val onBar: Color, val verseNumber: Color, val tile: Color, val onTile: Color,
    val selected: Color, val muted: Color, val strip: Color, val indicator: Color, val severity: List<Color>,
    /** Marks contested passages: readings still debated, with a confidence level and other possible meanings. */
    val contested: Color,
)

private val Light = BibleColors(
    bar = Color(0xFF1F4F8F), onBar = Color.White, verseNumber = Color(0xFF1F4F8F),
    tile = Color(0xFFE8EFF9), onTile = Color(0xFF173A6B), selected = Color(0xFFFFF4C2),
    muted = Color(0xFF6B7380), strip = Color(0xFFDDE1E7), indicator = Color(0xFF3C4A5C),
    severity = listOf(Color(0xFF9DB3D3), Color(0xFFE0A100), Color(0xFFC62828)),
    contested = Color(0xFF7B3FA8),
)

private val Dark = BibleColors(
    bar = Color(0xFF16304F), onBar = Color(0xFFE8EEF7), verseNumber = Color(0xFF8FB4E8),
    tile = Color(0xFF1E2A3A), onTile = Color(0xFFCFE0F7), selected = Color(0xFF3A3420),
    muted = Color(0xFF9AA3AF), strip = Color(0xFF232933), indicator = Color(0xFF8FB4E8),
    severity = listOf(Color(0xFF5C7397), Color(0xFFE0A100), Color(0xFFEF5350)),
    contested = Color(0xFFC6A0F0),
)

val LocalBibleColors = staticCompositionLocalOf { Light }

@Composable
fun BibleTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF8FB4E8), onPrimary = Color(0xFF0F1E33), background = Color(0xFF121418),
        surface = Color(0xFF121418), onBackground = Color(0xFFE6E6E6), onSurface = Color(0xFFE6E6E6),
        surfaceContainerLow = Color(0xFF1A1D23), surfaceVariant = Color(0xFF1E2A3A),
    ) else lightColorScheme(
        primary = Color(0xFF1F4F8F), onPrimary = Color.White, background = Color.White, surface = Color.White,
        onBackground = Color(0xFF1B1B1F), onSurface = Color(0xFF1B1B1F), surfaceContainerLow = Color(0xFFF6F8FC),
        surfaceVariant = Color(0xFFE8EFF9),
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalBibleColors provides if (dark) Dark else Light) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

val SEVERITY_LABEL = listOf("Nuance", "Notable", "Major")
val CATEGORY_LABEL = mapOf(
    "TEXT" to "Manuscript", "MEANING" to "Meaning", "INTERP" to "Interpretation",
    "LEXICAL" to "Word sense", "ARCHAIC" to "Archaic English", "NAME" to "Name",
)
