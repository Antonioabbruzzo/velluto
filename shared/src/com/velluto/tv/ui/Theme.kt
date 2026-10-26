package com.velluto.tv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.math.max

object Palette {
    val Background = Color(0xFF0B0B0F)
    val Surface = Color(0xFF18181F)
    val SurfaceHigh = Color(0xFF26262F)
    val Accent = Color(0xFFC1121F)
    /** Rosso più luminoso per sfumature e bagliori. */
    val AccentBright = Color(0xFFE5383B)
    /** Rosso scuro "velluto", per le luci di sfondo. */
    val AccentDeep = Color(0xFF5C0A12)
    /** Pannelli in vetro: bianco quasi trasparente sopra lo sfondo. */
    val Glass = Color(0x0FFFFFFF)
    /** Bordo sottile dei pannelli in vetro. */
    val Stroke = Color(0x1AFFFFFF)
    val Text = Color(0xFFF5F5F5)
    val TextDim = Color(0xFFA8A8B0)
    val Good = Color(0xFF46D369)
}

/** Durata delle animazioni di selezione: abbastanza rapida da non rallentare il telecomando. */
internal const val FOCUS_ANIM_MS = 160

/** Sfumatura rossa dei pulsanti principali (Riproduci, ecc.). */
internal val AccentGradient = Brush.horizontalGradient(listOf(Palette.AccentBright, Palette.Accent))

/**
 * Sfondo dell'app: nero profondo con una luce rossa soffusa in alto a sinistra e una più fredda
 * in basso a destra, così le pagine non sono un piatto nero uniforme.
 */
fun Modifier.vellutoBackdrop(): Modifier = drawBehind {
    drawRect(Palette.Background)
    val big = max(size.width, size.height)
    drawRect(
        Brush.radialGradient(
            listOf(Palette.AccentDeep.copy(alpha = 0.55f), Color.Transparent),
            center = Offset(size.width * 0.08f, -size.height * 0.05f),
            radius = big * 0.65f,
        )
    )
    drawRect(
        Brush.radialGradient(
            listOf(Color(0xFF1B1A3A).copy(alpha = 0.55f), Color.Transparent),
            center = Offset(size.width * 1.02f, size.height * 1.05f),
            radius = big * 0.6f,
        )
    )
}

/**
 * Anello di selezione animato: bordo bianco che compare in dissolvenza e un bagliore morbido
 * attorno all'elemento. Nessun ingrandimento, così niente si sposta. Va messo prima di `clip`.
 */
fun Modifier.focusRing(active: Boolean, shape: Shape, glow: Color = Color.White): Modifier = composed {
    val border by animateColorAsState(
        if (active) Color.White else Color.Transparent, tween(FOCUS_ANIM_MS), label = "ringBorder",
    )
    val elevation by animateDpAsState(if (active) 18.dp else 0.dp, tween(FOCUS_ANIM_MS), label = "ringGlow")
    this
        .shadow(elevation, shape, clip = false, ambientColor = glow, spotColor = glow)
        .border(3.dp, border, shape)
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
