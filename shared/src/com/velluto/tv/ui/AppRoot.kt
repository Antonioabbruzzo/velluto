package com.velluto.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import com.velluto.tv.platform.PlatformBackHandler

/** Schermata principale: login, caricamento, oppure la pila di schermate dell'app. */
@Composable
fun AppRoot(vm: AppViewModel) {
    val catalog = vm.catalog
    when {
        vm.account == null -> LoginScreen(vm)
        catalog == null -> LoadingScreen(vm)
        else -> {
            PlatformBackHandler(enabled = vm.backStack.size > 1) { vm.back() }
            val holder = rememberSaveableStateHolder()
            val screen = vm.backStack.last()
            val content: @Composable () -> Unit = {
                holder.SaveableStateProvider(screen.stateKey) {
                    when (screen) {
                        Screen.Home -> HomeScreen(vm, catalog)
                        is Screen.SeriesDetail -> SeriesDetailScreen(vm, screen.series)
                        is Screen.MovieDetail -> MovieDetailScreen(vm, screen.movie)
                        is Screen.Player -> PlayerScreen(vm, screen.request)
                    }
                }
            }
            if (screen is Screen.Player) {
                content() // il video resta a tutto schermo
            } else {
                // Menu laterale sempre visibile: Home e schede di serie e film.
                Row(Modifier.fillMaxSize()) {
                    AppSideMenu(vm)
                    Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                }
            }
        }
    }
}
