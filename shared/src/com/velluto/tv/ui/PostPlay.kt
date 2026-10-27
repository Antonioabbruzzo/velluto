package com.velluto.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velluto.tv.data.Series
import com.velluto.tv.data.Title
import com.velluto.tv.platform.RemoteImage

/** Quante locandine si vedono insieme nel pannello di fine film. */
internal const val POST_PLAY_VISIBLE = 6

/**
 * Fine film, come Netflix: sopra i titoli di coda compaiono i titoli consigliati in base al film
 * appena visto. Col telecomando ◀ ▶ scelgono, OK apre, ▲ nasconde il pannello (continui a vedere
 * i titoli di coda); col mouse basta un clic.
 */
@Composable
internal fun PostPlayPanel(
    source: String,
    titles: List<Title>,
    selected: Int,
    ended: Boolean,
    onOpen: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.35f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Black.copy(alpha = 0.95f),
                )
            )
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 48.dp, end = 48.dp, bottom = 36.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(AccentGradient))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (ended) "Ti potrebbe piacere anche" else "Sta per finire · Ti potrebbe piacere",
                        color = Palette.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Perché hai visto «$source»",
                        color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!ended) {
                    TvButton("Continua a guardare", onClick = onDismiss)
                }
            }
            Spacer(Modifier.height(18.dp))
            // Finestra di locandine che segue la selezione (niente scorrimento da gestire col focus).
            val start = (selected - (POST_PLAY_VISIBLE - 1)).coerceAtLeast(0)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                titles.drop(start).take(POST_PLAY_VISIBLE).forEachIndexed { i, t ->
                    PostPlayCard(t, selected = start + i == selected, onClick = { onOpen(start + i) })
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (ended) "◀ ▶  scegli   ·   OK  apri   ·   Indietro  esci"
                else "◀ ▶  scegli   ·   OK  apri   ·   ▲  continua a guardare",
                color = Palette.TextDim, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PostPlayCard(title: Title, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.width(150.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .focusRing(selected || hovered, shape)
                .clip(shape)
                .background(CardPlaceholder)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        ) {
            Text(
                title.name,
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
                color = Palette.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center,
                maxLines = 4, overflow = TextOverflow.Ellipsis,
            )
            if (title.image.isNotBlank()) {
                RemoteImage(
                    url = title.image,
                    contentDescription = title.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (title is Series) {
                Text(
                    "SERIE",
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                        .clip(RoundedCornerShape(4.dp)).background(AccentGradient)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title.name,
            color = if (selected || hovered) Color.White else Palette.TextDim,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
