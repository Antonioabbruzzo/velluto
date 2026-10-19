package com.velluto.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velluto.tv.data.Catalog
import com.velluto.tv.data.Movie
import com.velluto.tv.data.Series
import com.velluto.tv.data.Title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Sposta il focus con le frecce su/giù anche quando si è dentro un campo di testo. */
@Composable
fun Modifier.dpadVerticalFocus(): Modifier {
    val fm = LocalFocusManager.current
    return this.onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (e.key) {
            Key.DirectionDown -> fm.moveFocus(FocusDirection.Down)
            Key.DirectionUp -> fm.moveFocus(FocusDirection.Up)
            else -> false
        }
    }
}

@Composable
fun SearchSection(vm: AppViewModel, catalog: Catalog) {
    var query by remember { mutableStateOf(vm.lastFocus["search-query"] ?: "") }
    var results by remember { mutableStateOf<List<Title>>(emptyList()) }
    val fieldFr = remember { FocusRequester() }
    val fm = LocalFocusManager.current

    LaunchedEffect(query) {
        vm.lastFocus["search-query"] = query
        if (query.trim().length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        results = withContext(Dispatchers.Default) {
            val q = query.trim()
            val s: List<Title> = catalog.series.filter { it.name.contains(q, ignoreCase = true) }.take(60)
            val m: List<Title> = catalog.movies.filter { it.name.contains(q, ignoreCase = true) }.take(60)
            s + m
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Cerca serie e film") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { fm.moveFocus(FocusDirection.Down) }),
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
                .padding(horizontal = 40.dp)
                .width(520.dp)
                .focusRequester(fieldFr)
                .dpadVerticalFocus(),
        )
        if (query.trim().length >= 2 && results.isEmpty()) {
            Text(
                "Nessun risultato",
                color = Palette.TextDim,
                fontSize = 15.sp,
                modifier = Modifier.padding(start = 40.dp, top = 24.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(130.dp),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(results) { i, t ->
                PosterCard(
                    item = RowItem(
                        id = "search/$i/${t.id}",
                        title = t,
                        label = if (t is Series) "Serie" else null,
                        onClick = {
                            when (t) {
                                is Series -> vm.navigate(Screen.SeriesDetail(t))
                                is Movie -> vm.navigate(Screen.MovieDetail(t))
                            }
                        },
                    ),
                    onFocused = {},
                    width = 130.dp,
                )
            }
        }
    }
    RequestFocus(fieldFr, "search")
}
