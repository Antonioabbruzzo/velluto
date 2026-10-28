package com.velluto.tv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.velluto.tv.platform.VellutoBodyFont
import com.velluto.tv.platform.VellutoDisplayFont
import kotlin.math.max

/** Colori della "sala": nero, velluto rosso, ottone per i dettagli, avorio per il testo. */
object Palette {
    /** Nero sala, appena caldo. */
    val Background = Color(0xFF060405)
    val Surface = Color(0xFF161012)
    val SurfaceHigh = Color(0xFF241A1C)
    /** Rosso velluto: pulsanti principali, sezione attiva. */
    val Accent = Color(0xFFA3101E)
    /** Rosso più luminoso per bagliori, tacche e barre di avanzamento. */
    val AccentBright = Color(0xFFE0263A)
    /** Rosso scuro "velluto", per le luci di sfondo. */
    val AccentDeep = Color(0xFF4A0810)
    /** Ottone: solo piccoli dettagli (voto, "Vedi tutti", numeri dei canali). */
    val Brass = Color(0xFFC8A266)
    val BrassLight = Color(0xFFE3C48E)
    /** Pannelli in vetro: avorio quasi trasparente sopra lo sfondo. */
    val Glass = Color(0x0FF3EDE6)
    /** Bordo sottile dei pannelli in vetro. */
    val Stroke = Color(0x1FF3EDE6)
    /** Avorio: testo e card/pulsanti selezionati. */
    val Text = Color(0xFFF3EDE6)
    val TextDim = Color(0xFFA39A92)
    val Good = Color(0xFF46D369)
}

/** Durata delle animazioni di selezione: abbastanza rapida da non rallentare il telecomando. */
internal const val FOCUS_ANIM_MS = 160

/** Rosso dei pulsanti principali (Riproduci, ecc.). */
internal val AccentGradient = Brush.horizontalGradient(listOf(Color(0xFFB8142A), Palette.Accent))

/** Titoli grandi da insegna: Big Shoulders, righe strette (il testo va passato già in maiuscolo). */
internal fun displayStyle(size: TextUnit, weight: FontWeight = FontWeight.Black): TextStyle =
    TextStyle(fontFamily = VellutoDisplayFont, fontWeight = weight, fontSize = size, lineHeight = size * 0.92f)

/**
 * Sfondo dell'app: nero sala con una luce di velluto rosso in alto a sinistra,
 * così le pagine non sono un piatto nero uniforme.
 */
fun Modifier.vellutoBackdrop(): Modifier = drawBehind {
    drawRect(Palette.Background)
    val big = max(size.width, size.height)
    drawRect(
        Brush.radialGradient(
            listOf(Palette.AccentDeep.copy(alpha = 0.5f), Color.Transparent),
            center = Offset(size.width * 0.08f, -size.height * 0.05f),
            radius = big * 0.6f,
        )
    )
}

/**
 * Anello di selezione animato: bordo avorio che compare in dissolvenza e un alone rosso sotto
 * l'elemento. Nessun ingrandimento, così niente si sposta. Va messo prima di `clip`.
 */
fun Modifier.focusRing(active: Boolean, shape: Shape, glow: Color = Palette.AccentBright): Modifier = composed {
    val border by animateColorAsState(
        if (active) Palette.Text else Color.Transparent, tween(FOCUS_ANIM_MS), label = "ringBorder",
    )
    val elevation by animateDpAsState(if (active) 20.dp else 0.dp, tween(FOCUS_ANIM_MS), label = "ringGlow")
    this
        .shadow(elevation, shape, clip = false, ambientColor = glow, spotColor = glow)
        .border(3.dp, border, shape)
}

/**
 * Caricamento di Velluto: una linea sottile percorsa da una luce rossa e ottone, al posto della
 * rotellina. Con [fraction] (0–1) mostra anche quanto è stato fatto.
 * L'animazione gira solo finché il caricamento è sullo schermo.
 */
@Composable
fun VellutoLoader(modifier: Modifier = Modifier, thickness: Dp = 3.dp, fraction: Float? = null) {
    val sweep by rememberInfiniteTransition(label = "loader").animateFloat(
        initialValue = -0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "sweep",
    )
    Box(
        modifier
            .height(thickness)
            .clipToBounds()
            .drawBehind {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(Palette.Text.copy(alpha = 0.1f), cornerRadius = r)
                if (fraction != null) {
                    drawRoundRect(Palette.Accent, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = r)
                }
                val w = size.width * 0.4f
                val x = size.width * sweep
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Transparent, 0.45f to Palette.AccentBright, 0.6f to Palette.BrassLight, 1f to Color.Transparent,
                        startX = x, endX = x + w,
                    ),
                    topLeft = Offset(x, 0f),
                    size = Size(w, size.height),
                )
            },
    )
}

/** Tutti gli stili di testo di Material con il carattere di Velluto. */
private fun Typography.withFont(): Typography {
    fun TextStyle.f() = copy(fontFamily = VellutoBodyFont)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
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
        typography = Typography().withFont(),
        content = content,
    )
}
