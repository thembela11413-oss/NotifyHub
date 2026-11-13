package com.notifyhub.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NotifyDark = darkColorScheme(
    primary = Color(0xFF7CC4FF),
    onPrimary = Color(0xFF00263F),
    primaryContainer = Color(0xFF0F3A5C),
    onPrimaryContainer = Color(0xFFCFE8FF),
    secondary = Color(0xFF8FD9B6),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1A212B),
    onSurfaceVariant = Color(0xFFA3AEBD),
    surfaceContainer = Color(0xFF151A21),
    error = Color(0xFFFF8A80),
    outline = Color(0xFF3A4553),
)

/** Phase 1 is dark-only (dark-mode-first). */
@Composable
fun NotifyHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NotifyDark, content = content)
}
