package com.velluto.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import com.velluto.tv.ui.AppViewModel
import com.velluto.tv.ui.HomeScreen
import com.velluto.tv.ui.LoadingScreen
import com.velluto.tv.ui.LoginScreen
import com.velluto.tv.ui.MovieDetailScreen
import com.velluto.tv.ui.Palette
import com.velluto.tv.ui.PlayerScreen
import com.velluto.tv.ui.Screen
import com.velluto.tv.ui.SeriesDetailScreen
import com.velluto.tv.ui.VellutoTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VellutoTheme {
                Box(Modifier.fillMaxSize().background(Palette.Background)) {
                    AppRoot(vm)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val catalog = vm.catalog
    when {
        vm.account == null -> LoginScreen(vm)
        catalog == null -> LoadingScreen(vm)
        else -> {
            BackHandler(enabled = vm.backStack.size > 1) { vm.back() }
            val holder = rememberSaveableStateHolder()
            val screen = vm.backStack.last()
            holder.SaveableStateProvider(screen.stateKey) {
                when (screen) {
                    Screen.Home -> HomeScreen(vm, catalog)
                    is Screen.SeriesDetail -> SeriesDetailScreen(vm, screen.series)
                    is Screen.MovieDetail -> MovieDetailScreen(vm, screen.movie)
                    is Screen.Player -> PlayerScreen(vm, screen.request)
                }
            }
        }
    }
}
