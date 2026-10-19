package com.velluto.tv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Background = Color(0xFF0E0E10)
    val Surface = Color(0xFF1C1C20)
    val SurfaceHigh = Color(0xFF2A2A30)
    val Accent = Color(0xFFC1121F)
    val Text = Color(0xFFF5F5F5)
    val TextDim = Color(0xFFA8A8B0)
    val Good = Color(0xFF46D369)
}

@Composable
fun VellutoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Color.White,
            background = Palette.Background,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.SurfaceHigh,
            onSurfaceVariant = Palette.TextDim,
        ),
        content = content,
    )
}
