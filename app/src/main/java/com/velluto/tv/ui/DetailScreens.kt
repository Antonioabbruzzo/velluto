package com.velluto.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.velluto.tv.data.Episode
import com.velluto.tv.data.LibraryState
import com.velluto.tv.data.Movie
import com.velluto.tv.data.MovieDetail
import com.velluto.tv.data.Progress
import com.velluto.tv.data.Series
import com.velluto.tv.data.SeriesDetail

@Composable
private fun DetailBackground(image: String) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f },
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Palette.Background, 0.6f to Palette.Background.copy(alpha = 0.5f), 1f to Color.Transparent)
            )
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Palette.Background)))
    }
}

// ======================= SERIE =======================

/** Cosa riprodurre quando premi "Riprendi": l'episodio a metà, oppure il successivo. */
private data class ResumeTarget(val index: Int, val startMs: Long, val label: String)

private fun resumeTarget(series: Series, detail: SeriesDetail, lib: LibraryState): ResumeTarget? {
    val eps = detail.allEpisodes
    if (eps.isEmpty()) return null
    val last = lib.latestFor(series.key)
        ?: return ResumeTarget(0, 0, "Riproduci S${eps[0].season}:E${eps[0].number}")
    val idx = eps.indexOfFirst { it.season == last.season && it.number == last.episode }
    if (idx < 0) return ResumeTarget(0, 0, "Riproduci")
    return if (!last.finished) {
        ResumeTarget(idx, last.positionMs, "Riprendi S${last.season}:E${last.episode}")
    } else if (idx + 1 < eps.size) {
        val next = eps[idx + 1]
        ResumeTarget(idx + 1, 0, "Riproduci S${next.season}:E${next.number}")
    } else {
        ResumeTarget(idx, 0, "Guarda di nuovo S${last.season}:E${last.episode}")
    }
}

@Composable
fun SeriesDetailScreen(vm: AppViewModel, series: Series) {
    val lib by vm.library.state.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<SeriesDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(series.id, attempt) {
        error = null
        try {
            detail = vm.client?.seriesDetail(series.id)
        } catch (e: Exception) {
            error = e.message ?: "Impossibile caricare la serie"
        }
    }

    val d = detail
    Box(Modifier.fillMaxSize()) {
        DetailBackground(d?.backdrop?.ifBlank { null } ?: series.backdrop.ifBlank { series.image })
        Column(Modifier.fillMaxSize().padding(top = 48.dp)) {
            Column(Modifier.padding(horizontal = 48.dp).fillMaxWidth(0.6f)) {
                Text(series.name, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
                Spacer(Modifier.height(8.dp))
                val seasonsCount = d?.seasons?.size ?: 0
                MetaLine(
                    year = d?.year?.ifBlank { null } ?: series.year,
                    rating = d?.rating?.ifBlank { null } ?: series.rating,
                    extra = listOfNotNull(
                        if (seasonsCount > 0) "$seasonsCount ${if (seasonsCount == 1) "stagione" else "stagioni"}" else null,
                        d?.genre?.ifBlank { null },
                    ).joinToString(" · "),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    d?.plot?.ifBlank { null } ?: series.plot,
                    color = Palette.Text, fontSize = 14.sp, lineHeight = 20.sp,
                    maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
                if (!d?.cast.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Cast: ${d?.cast}", color = Palette.TextDim, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            when {
                error != null -> Column(Modifier.padding(horizontal = 48.dp)) {
                    Text(error ?: "", color = Palette.Accent, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    val fr = remember { FocusRequester() }
                    TvButton("Riprova", onClick = { attempt++ }, icon = Icons.Filled.Refresh, focusRequester = fr)
                    RequestFocus(fr, error)
                }
                d == null -> Box(Modifier.padding(horizontal = 48.dp)) {
                    CircularProgressIndicator(color = Palette.Accent)
                }
                else -> SeriesBody(vm, series, d, lib)
            }
        }
    }
}

@Composable
private fun SeriesBody(vm: AppViewModel, series: Series, d: SeriesDetail, lib: LibraryState) {
    val eps = d.allEpisodes
    val target = resumeTarget(series, d, lib)
    val seasons = d.seasons.keys.sorted()
    var season by rememberSaveable {
        mutableStateOf(target?.let { eps[it.index].season } ?: seasons.firstOrNull() ?: 1)
    }
    val playFr = remember { FocusRequester() }
    val isFav = lib.favorites.containsKey(series.key)

    fun play(index: Int, startMs: Long) {
        vm.navigate(Screen.Player(PlayRequest.EpisodeReq(series, eps, index, startMs)))
    }

    Row(Modifier.padding(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (target != null) {
            TvButton(
                target.label,
                onClick = { play(target.index, target.startMs) },
                icon = Icons.Filled.PlayArrow,
                primary = true,
                focusRequester = playFr,
            )
        }
        TvButton(
            if (isFav) "Nella mia lista" else "La mia lista",
            onClick = { vm.library.toggleFavorite(series) },
            icon = if (isFav) Icons.Filled.Check else Icons.Filled.Add,
        )
    }
    Spacer(Modifier.height(16.dp))

    if (seasons.size > 1) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(seasons) { s ->
                SeasonChip("Stagione $s", selected = s == season, onSelect = { season = s })
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    val seasonEps = d.seasons[season].orEmpty()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(seasonEps) { ep ->
            val p = lib.progress[Progress.episodeKey(series.key, ep.season, ep.number)]
            EpisodeCard(
                ep = ep,
                fallbackImage = series.image,
                progress = p,
                onClick = {
                    val idx = eps.indexOf(ep)
                    val start = if (p != null && !p.finished) p.positionMs else 0L
                    play(idx, start)
                },
            )
        }
    }
    if (target != null) RequestFocus(playFr, series.id)
}

@Composable
private fun SeasonChip(text: String, selected: Boolean, onSelect: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Come su Netflix: basta spostarsi sulla stagione per vederne gli episodi.
    LaunchedEffect(focused) { if (focused && !selected) onSelect() }
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    focused -> Color.White
                    selected -> Palette.SurfaceHigh
                    else -> Color.Transparent
                }
            )
            .then(if (!focused && selected) Modifier.border(1.dp, Color.White, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            color = if (focused) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun EpisodeCard(ep: Episode, fallbackImage: String, progress: Progress?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "epScale")
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier
            .width(250.dp)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(Palette.Surface)
                .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier),
        ) {
            AsyncImage(
                model = ep.image.ifBlank { fallbackImage },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (progress?.finished == true) {
                WatchedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            if (progress != null && !progress.finished && progress.fraction > 0f) {
                ProgressBar(progress.fraction, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${ep.number}. ${ep.title.ifBlank { "Episodio ${ep.number}" }}",
            color = if (focused) Color.White else Palette.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (focused && ep.plot.isNotBlank()) {
            Text(ep.plot, color = Palette.TextDim, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
        } else if (ep.durationSecs > 0) {
            Text("${ep.durationSecs / 60} min", color = Palette.TextDim, fontSize = 11.sp)
        }
    }
}

// ======================= FILM =======================

@Composable
fun MovieDetailScreen(vm: AppViewModel, movie: Movie) {
    val lib by vm.library.state.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<MovieDetail?>(null) }
    LaunchedEffect(movie.id) {
        detail = runCatching { vm.client?.movieDetail(movie.id) }.getOrNull()
    }
    val d = detail
    val progress = lib.progress[movie.key]
    val isFav = lib.favorites.containsKey(movie.key)
    val playFr = remember { FocusRequester() }
    val ext = d?.ext?.ifBlank { null } ?: movie.ext

    fun play(startMs: Long) {
        vm.navigate(Screen.Player(PlayRequest.MovieReq(movie, ext, startMs)))
    }

    Box(Modifier.fillMaxSize()) {
        DetailBackground(d?.backdrop?.ifBlank { null } ?: movie.image)
        Column(
            Modifier.fillMaxSize().padding(start = 48.dp, top = 56.dp, end = 48.dp).verticalScroll(rememberScrollState())
        ) {
            Column(Modifier.fillMaxWidth(0.6f)) {
                Text(movie.name, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
                Spacer(Modifier.height(8.dp))
                MetaLine(
                    year = d?.year ?: "",
                    rating = movie.rating,
                    extra = listOfNotNull(d?.duration?.ifBlank { null }, d?.genre?.ifBlank { null }).joinToString(" · "),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    d?.plot ?: "", color = Palette.Text, fontSize = 14.sp, lineHeight = 20.sp,
                    maxLines = 5, overflow = TextOverflow.Ellipsis,
                )
                if (!d?.cast.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Cast: ${d?.cast}", color = Palette.TextDim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(22.dp))
            val resumable = progress != null && !progress.finished && progress.positionMs > 10_000
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                TvButton(
                    if (resumable) "Riprendi da ${formatTime(progress!!.positionMs)}" else "Riproduci",
                    onClick = { play(if (resumable) progress!!.positionMs else 0L) },
                    icon = Icons.Filled.PlayArrow,
                    primary = true,
                    focusRequester = playFr,
                )
                if (resumable) {
                    TvButton("Dall'inizio", onClick = { play(0L) }, icon = Icons.Filled.Refresh)
                }
                TvButton(
                    if (isFav) "Nella mia lista" else "La mia lista",
                    onClick = { vm.library.toggleFavorite(movie) },
                    icon = if (isFav) Icons.Filled.Check else Icons.Filled.Add,
                )
            }
            if (resumable) {
                Spacer(Modifier.height(12.dp))
                ProgressBar(progress!!.fraction, Modifier.width(300.dp))
            }
        }
    }
    RequestFocus(playFr, movie.id)
}
