package dev.babysim.spike.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Soft, calm palette (nursery teal and peach) for phones without dynamic color; the same on every phone so screenshots match what the tester sees. */
private val Light =
    lightColorScheme(
        primary = Color(0xFF2F6C67),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB9ECE5),
        onPrimaryContainer = Color(0xFF00201E),
        secondary = Color(0xFF8E4E3E),
        secondaryContainer = Color(0xFFFFDBD2),
        onSecondaryContainer = Color(0xFF3A0B03),
        tertiary = Color(0xFF4C6080),
        background = Color(0xFFF7FAF8),
        surface = Color(0xFFF7FAF8),
        surfaceContainer = Color(0xFFEBF0EE),
        surfaceContainerHigh = Color(0xFFE5EAE8),
    )

private val Dark =
    darkColorScheme(
        primary = Color(0xFF9DD0C9),
        onPrimary = Color(0xFF003733),
        primaryContainer = Color(0xFF14504B),
        onPrimaryContainer = Color(0xFFB9ECE5),
        secondary = Color(0xFFFFB4A2),
        secondaryContainer = Color(0xFF713729),
        onSecondaryContainer = Color(0xFFFFDBD2),
        tertiary = Color(0xFFB4C8EC),
        background = Color(0xFF0F1514),
        surface = Color(0xFF0F1514),
        surfaceContainer = Color(0xFF1B2120),
        surfaceContainerHigh = Color(0xFF252B2A),
    )

@Composable
fun SpikeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
