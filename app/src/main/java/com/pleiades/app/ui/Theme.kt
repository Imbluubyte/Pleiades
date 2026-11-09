package com.pleiades.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object P {
    val bg = Color(0xFF16161E)
    val panel = Color(0xFF1E1E2A)
    val panel2 = Color(0xFF272736)
    val line = Color(0xFF34344A)
    val accent = Color(0xFF8AB4FF)
    val text = Color(0xFFE6E6F0)
    val dim = Color(0xFF9A9AB0)
    val danger = Color(0xFFFF7A7A)
}

@Composable
fun PleiadesTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = P.accent, onPrimary = Color(0xFF0B1630), background = P.bg, surface = P.panel,
        onSurface = P.text, onBackground = P.text, surfaceVariant = P.panel2, secondary = P.accent,
    ),
    content = content,
)
