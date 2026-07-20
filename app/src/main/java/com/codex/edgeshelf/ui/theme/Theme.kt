package com.codex.edgeshelf.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Canvas = Color(0xFFF4F3EE)
val Ink = Color(0xFF17211E)
val InkMuted = Color(0xFF5E6965)
val Jade = Color(0xFF276B59)
val JadeSoft = Color(0xFFD8E8E1)
val SurfaceRaised = Color(0xFFFCFBF7)
val Hairline = Color(0xFFD8DCD8)
val WarningSoft = Color(0xFFF4E6C9)

private val EdgeShelfColors = lightColorScheme(
    primary = Jade,
    onPrimary = Color.White,
    primaryContainer = JadeSoft,
    onPrimaryContainer = Ink,
    secondary = InkMuted,
    onSecondary = Color.White,
    background = Canvas,
    onBackground = Ink,
    surface = SurfaceRaised,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8EBE7),
    onSurfaceVariant = InkMuted,
    outline = Color(0xFF7B8581),
    outlineVariant = Hairline,
    error = Color(0xFF9B3F3A),
)

@Composable
fun EdgeShelfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EdgeShelfColors,
        typography = Typography(),
        content = content,
    )
}
