package com.velluto.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Icone del player, disegnate a mano: identiche su Fire TV e Windows (niente simboli dei font). */
enum class PlayerIcon { PLAY, PAUSE, PREV, NEXT, BACK10, FWD10, VOLUME, MUTED, MINUS, PLUS, FULLSCREEN, BACK }

@Composable
fun PlayerIconView(icon: PlayerIcon, color: Color, size: Dp = 24.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) { drawPlayerIcon(icon, color) }
        if (icon == PlayerIcon.BACK10 || icon == PlayerIcon.FWD10) {
            Text("10", color = color, fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun DrawScope.drawPlayerIcon(icon: PlayerIcon, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.09f
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2), strokeWidth = stroke, cap = StrokeCap.Round)

    fun triangle(points: List<Pair<Float, Float>>) {
        val p = Path().apply {
            moveTo(w * points[0].first, h * points[0].second)
            points.drop(1).forEach { (x, y) -> lineTo(w * x, h * y) }
            close()
        }
        drawPath(p, color)
    }

    when (icon) {
        PlayerIcon.PLAY -> triangle(listOf(0.28f to 0.16f, 0.84f to 0.5f, 0.28f to 0.84f))
        PlayerIcon.PAUSE -> {
            val r = CornerRadius(w * 0.04f)
            drawRoundRect(color, Offset(w * 0.24f, h * 0.18f), Size(w * 0.17f, h * 0.64f), r)
            drawRoundRect(color, Offset(w * 0.59f, h * 0.18f), Size(w * 0.17f, h * 0.64f), r)
        }
        PlayerIcon.PREV -> {
            drawRoundRect(color, Offset(w * 0.18f, h * 0.2f), Size(w * 0.1f, h * 0.6f), CornerRadius(w * 0.03f))
            triangle(listOf(0.82f to 0.2f, 0.32f to 0.5f, 0.82f to 0.8f))
        }
        PlayerIcon.NEXT -> {
            drawRoundRect(color, Offset(w * 0.72f, h * 0.2f), Size(w * 0.1f, h * 0.6f), CornerRadius(w * 0.03f))
            triangle(listOf(0.18f to 0.2f, 0.68f to 0.5f, 0.18f to 0.8f))
        }
        PlayerIcon.BACK10, PlayerIcon.FWD10 -> {
            // Cerchio quasi chiuso con la punta della freccia; il "10" è scritto sopra.
            // "Avanti" ha la punta in alto a destra, "indietro" in alto a sinistra.
            val fwd = icon == PlayerIcon.FWD10
            val start = if (fwd) -60f else -120f
            drawArc(
                color, startAngle = start, sweepAngle = if (fwd) -300f else 300f, useCenter = false,
                topLeft = Offset(w * 0.1f, h * 0.1f), size = Size(w * 0.8f, h * 0.8f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (fwd) triangle(listOf(0.66f to 0.02f, 0.82f to 0.2f, 0.58f to 0.26f))
            else triangle(listOf(0.34f to 0.02f, 0.18f to 0.2f, 0.42f to 0.26f))
        }
        PlayerIcon.VOLUME, PlayerIcon.MUTED -> {
            // Altoparlante
            triangle(listOf(0.1f to 0.38f, 0.28f to 0.38f, 0.5f to 0.16f, 0.5f to 0.84f, 0.28f to 0.62f, 0.1f to 0.62f))
            if (icon == PlayerIcon.VOLUME) {
                drawArc(
                    color, -45f, 90f, false, Offset(w * 0.38f, h * 0.3f), Size(w * 0.3f, h * 0.4f),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color, -50f, 100f, false, Offset(w * 0.36f, h * 0.14f), Size(w * 0.52f, h * 0.72f),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            } else {
                line(0.62f, 0.36f, 0.88f, 0.64f)
                line(0.88f, 0.36f, 0.62f, 0.64f)
            }
        }
        PlayerIcon.MINUS -> line(0.24f, 0.5f, 0.76f, 0.5f)
        PlayerIcon.PLUS -> {
            line(0.24f, 0.5f, 0.76f, 0.5f)
            line(0.5f, 0.24f, 0.5f, 0.76f)
        }
        PlayerIcon.FULLSCREEN -> {
            // Quattro angoli
            line(0.16f, 0.36f, 0.16f, 0.16f); line(0.16f, 0.16f, 0.36f, 0.16f)
            line(0.64f, 0.16f, 0.84f, 0.16f); line(0.84f, 0.16f, 0.84f, 0.36f)
            line(0.84f, 0.64f, 0.84f, 0.84f); line(0.84f, 0.84f, 0.64f, 0.84f)
            line(0.36f, 0.84f, 0.16f, 0.84f); line(0.16f, 0.84f, 0.16f, 0.64f)
        }
        PlayerIcon.BACK -> {
            line(0.2f, 0.5f, 0.82f, 0.5f)
            line(0.2f, 0.5f, 0.44f, 0.26f)
            line(0.2f, 0.5f, 0.44f, 0.74f)
        }
    }
}
