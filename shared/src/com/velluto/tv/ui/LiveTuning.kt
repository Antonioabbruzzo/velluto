package com.velluto.tv.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velluto.tv.data.EpgEntry
import com.velluto.tv.data.LiveChannel
import com.velluto.tv.data.cleanCategoryName
import com.velluto.tv.platform.RemoteImage

/**
 * Mentre un canale live parte: logo, numero e nome del canale con la linea di caricamento,
 * il programma in onda appena arriva la guida, e il canale prima e dopo (▲ ▼ per cambiare).
 * Così cambiando canale sai subito dove stai andando, anche se il provider ci mette qualche secondo.
 */
@Composable
internal fun LiveTuning(vm: AppViewModel, live: PlayRequest.Live, epg: List<EpgEntry>, modifier: Modifier = Modifier) {
    val ch = live.channels.getOrNull(live.index) ?: return
    val n = live.channels.size
    val category = remember(ch.categoryId) {
        vm.catalog?.liveCategories?.firstOrNull { it.id == ch.categoryId }?.name?.let(::cleanCategoryName).orEmpty()
    }
    val now = System.currentTimeMillis()
    val onAir = epg.firstOrNull { now in it.start until it.end }?.title
    // A ogni cambio di canale il blocco compare salendo appena.
    val enter = remember(ch.id) { Animatable(0f) }
    LaunchedEffect(ch.id) { enter.animateTo(1f, tween(260)) }

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF12090B), Color.Black))),
    ) {
        Row(
            Modifier
                .align(Alignment.Center)
                .width(600.dp)
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * 10.dp.toPx()
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val logoShape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .size(width = 150.dp, height = 96.dp)
                    .clip(logoShape)
                    .background(Palette.Glass)
                    .border(1.dp, Palette.Stroke, logoShape)
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (ch.icon.isNotBlank()) {
                    RemoteImage(url = ch.icon, contentDescription = ch.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else {
                    Text(ch.name.take(3).uppercase(), color = Palette.Text, style = displayStyle(32.sp))
                }
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    if (ch.num > 0) {
                        Text("${ch.num}", color = Palette.Brass, style = displayStyle(32.sp, FontWeight.ExtraBold))
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        ch.name.uppercase(),
                        color = Palette.Text,
                        style = displayStyle(42.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (category.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(category, color = Palette.TextDim, fontSize = 15.sp, maxLines = 1)
                }
                Spacer(Modifier.height(18.dp))
                VellutoLoader(Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text(
                    if (onAir != null) "In onda: $onAir" else "Sintonizzo il canale",
                    color = Color(0xFFD8D0C8),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (n > 1) {
            Column(
                Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChannelHint("▲", live.channels[(live.index - 1 + n) % n])
                ChannelHint("▼", live.channels[(live.index + 1) % n])
            }
        }
    }
}

@Composable
private fun ChannelHint(arrow: String, ch: LiveChannel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(arrow, color = Palette.TextDim, fontSize = 12.sp)
        if (ch.num > 0) Text("${ch.num}", color = Palette.Brass, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(ch.name, color = Color(0xFF8C837C), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
