package com.velluto.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velluto.tv.platform.RemoteImage
import com.velluto.tv.data.Catalog
import com.velluto.tv.data.Category
import com.velluto.tv.data.buildTopChannels
import com.velluto.tv.data.EpgEntry
import com.velluto.tv.data.LiveChannel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Id della categoria virtuale "Top canali" (non esiste nel provider). */
private const val TOP_ID = "velluto-top"

@Composable
fun LiveSection(vm: AppViewModel, catalog: Catalog) {
    // "★ Top canali": selezione dei canali italiani principali, prima di tutte le categorie del provider.
    val top = remember(catalog) {
        buildTopChannels(catalog.live, catalog.liveCategories.associate { it.id to it.name })
    }
    val topChannels = remember(top) { top.flatMap { it.channels } }
    val cats = remember(catalog, top) {
        val provider = catalog.liveCategories.filter { catalog.liveByCategory[it.id].orEmpty().isNotEmpty() }
        if (top.isEmpty()) provider else listOf(Category(TOP_ID, "★  Top canali")) + provider
    }
    if (cats.isEmpty()) {
        EmptyMessage("Nessun canale disponibile")
        return
    }
    var selectedCat by remember {
        mutableStateOf(vm.lastFocus["live-cat"]?.takeIf { id -> cats.any { it.id == id } } ?: cats.first().id)
    }
    // Ricerca: con almeno 2 lettere mostra i canali di tutte le categorie che corrispondono.
    var query by remember { mutableStateOf(vm.lastFocus["live-query"] ?: "") }
    val q = query.trim()
    val searching = q.length >= 2
    val results = remember(catalog, q) {
        if (q.length < 2) emptyList() else catalog.live.filter { it.name.contains(q, ignoreCase = true) }
    }
    val showingTop = !searching && selectedCat == TOP_ID
    val channels = when {
        searching -> results
        showingTop -> topChannels
        else -> catalog.liveByCategory[selectedCat].orEmpty()
    }
    var focused by remember { mutableStateOf<LiveChannel?>(null) }
    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    val catFr = remember { FocusRequester() }

    LaunchedEffect(focused) {
        val ch = focused ?: return@LaunchedEffect
        epg = emptyList()
        delay(350) // evita richieste mentre scorri velocemente
        epg = runCatching { vm.client?.shortEpg(ch.id) }.getOrNull().orEmpty()
    }

    Column(Modifier.fillMaxSize()) {
    // In alto: "← Indietro" torna alla Home (come il tasto Indietro del telecomando).
    Row(Modifier.padding(start = 24.dp, top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        BackButton(onClick = { vm.section = Section.HOME })
        Spacer(Modifier.width(20.dp))
        Text("Canali TV", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
    Row(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
        LazyColumn(
            Modifier.width(250.dp).fillMaxHeight(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
        ) {
            itemsIndexed(cats) { i, c ->
                // Linea sottile tra "Top canali" e le categorie del provider.
                if (i == 1 && cats[0].id == TOP_ID) {
                    Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth().height(1.dp).background(Palette.SurfaceHigh))
                }
                ListEntry(
                    text = c.name,
                    selected = c.id == selectedCat && !searching,
                    onFocused = {
                        // Durante una ricerca passare sulle categorie non cambia l'elenco dei risultati.
                        if (!searching) {
                            selectedCat = c.id
                            vm.lastFocus["live-cat"] = c.id
                        }
                    },
                    onClick = {
                        // OK su una categoria chiude la ricerca e mostra i canali di quella categoria.
                        query = ""
                        vm.lastFocus["live-query"] = ""
                        selectedCat = c.id
                        vm.lastFocus["live-cat"] = c.id
                    },
                    focusRequester = if (c.id == selectedCat) catFr else null,
                )
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    vm.lastFocus["live-query"] = it
                },
                singleLine = true,
                label = { Text("Cerca canale") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Palette.TextDim,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Palette.TextDim,
                    cursorColor = Palette.Accent,
                ),
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .dpadVerticalFocus(),
            )
            if (searching) {
                Text(
                    if (results.isEmpty()) "Nessun canale trovato per \"$q\"" else "${results.size} canali trovati",
                    modifier = Modifier.padding(start = 14.dp, top = 6.dp),
                    color = Palette.TextDim,
                    fontSize = 13.sp,
                )
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
            ) {
                if (showingTop) {
                    // Top canali divisi per gruppo, con un titolino; nel player ▲▼ li scorre tutti in ordine.
                    var offset = 0
                    top.forEach { group ->
                        val start = offset
                        item(key = "h-${group.title}") {
                            Text(
                                group.title.uppercase(),
                                modifier = Modifier.padding(start = 4.dp, top = if (start == 0) 4.dp else 18.dp, bottom = 6.dp),
                                color = Palette.TextDim,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                            )
                        }
                        itemsIndexed(group.channels) { i, ch ->
                            ChannelEntry(
                                channel = ch,
                                onFocused = { focused = ch },
                                onClick = { vm.navigate(Screen.Player(PlayRequest.Live(channels, start + i))) },
                            )
                        }
                        offset += group.channels.size
                    }
                } else {
                    itemsIndexed(channels) { index, ch ->
                        ChannelEntry(
                            channel = ch,
                            onFocused = { focused = ch },
                            // Nel player ▲▼ cambia canale all'interno di questo elenco (categoria o risultati).
                            onClick = { vm.navigate(Screen.Player(PlayRequest.Live(channels, index))) },
                        )
                    }
                }
            }
        }
        Column(Modifier.width(300.dp).fillMaxHeight().padding(16.dp)) {
            val ch = focused
            if (ch != null) {
                if (ch.icon.isNotBlank()) {
                    RemoteImage(
                        url = ch.icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(width = 160.dp, height = 90.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(ch.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                EpgPanel(epg)
            }
        }
    }
    }
    RequestFocus(catFr, "live")
}

@Composable
fun EpgPanel(epg: List<EpgEntry>) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.ITALY) }
    if (epg.isEmpty()) {
        Text("Guida TV non disponibile", color = Palette.TextDim, fontSize = 13.sp)
        return
    }
    val now = System.currentTimeMillis()
    epg.take(3).forEachIndexed { i, e ->
        val live = now in e.start until e.end
        Text(
            if (live) "ORA IN ONDA" else if (i == 0 || epg[i - 1].end <= now) "A SEGUIRE" else "POI",
            color = if (live) Palette.Accent else Palette.TextDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${fmt.format(Date(e.start))} – ${fmt.format(Date(e.end))}  ${e.title}",
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (live && e.end > e.start) {
            Spacer(Modifier.height(4.dp))
            ProgressBar((now - e.start).toFloat() / (e.end - e.start), Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun ListEntry(
    text: String,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused = interaction.isHighlighted()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .optionalFocusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isFocused -> Color.White
                    selected -> Palette.SurfaceHigh
                    else -> Color.Transparent
                }
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            color = if (isFocused) Color.Black else if (selected) Color.White else Palette.TextDim,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChannelEntry(channel: LiveChannel, onFocused: () -> Unit, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused = interaction.isHighlighted()
    LaunchedEffect(isFocused) { if (isFocused) onFocused() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clip(RoundedCornerShape(6.dp))
            .background(if (isFocused) Color.White else Palette.Surface)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 56.dp, height = 32.dp), contentAlignment = Alignment.Center) {
            if (channel.icon.isNotBlank()) {
                RemoteImage(
                    url = channel.icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (channel.num > 0) {
            Text(
                "${channel.num}",
                color = if (isFocused) Color.DarkGray else Palette.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.width(40.dp),
            )
        }
        Text(
            channel.name,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
