package com.velluto.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.velluto.tv.platform.BackDispatcher
import com.velluto.tv.platform.LocalBackDispatcher
import com.velluto.tv.platform.desktopServices
import com.velluto.tv.ui.AppRoot
import com.velluto.tv.ui.AppViewModel
import com.velluto.tv.ui.Palette
import com.velluto.tv.ui.VellutoTheme

private class FocusHolder {
    var manager: FocusManager? = null
}

fun main() = application {
    val vm = remember { AppViewModel(desktopServices()) }
    val back = remember { BackDispatcher() }
    val focus = remember { FocusHolder() }
    val windowState = rememberWindowState(
        size = DpSize(1280.dp, 760.dp),
        position = WindowPosition(Alignment.Center),
    )

    fun toggleFullscreen() {
        windowState.placement =
            if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating
            else WindowPlacement.Fullscreen
    }

    Window(
        onCloseRequest = {
            vm.dispose()
            exitApplication()
        },
        title = "Velluto",
        state = windowState,
        // Tasti non usati dalle schermate: navigazione con le frecce (come il telecomando),
        // Esc/Backspace = Indietro, F11 = schermo intero.
        onKeyEvent = { e ->
            if (e.type != KeyEventType.KeyDown) {
                false
            } else when (e.key) {
                Key.F11 -> {
                    toggleFullscreen()
                    true
                }
                Key.Escape, Key.Backspace -> {
                    if (back.dispatch()) true
                    else if (windowState.placement == WindowPlacement.Fullscreen) {
                        windowState.placement = WindowPlacement.Floating
                        true
                    } else false
                }
                Key.DirectionUp -> focus.manager?.moveFocus(FocusDirection.Up) ?: false
                Key.DirectionDown -> focus.manager?.moveFocus(FocusDirection.Down) ?: false
                Key.DirectionLeft -> focus.manager?.moveFocus(FocusDirection.Left) ?: false
                Key.DirectionRight -> focus.manager?.moveFocus(FocusDirection.Right) ?: false
                else -> false
            }
        },
    ) {
        val fm = LocalFocusManager.current
        SideEffect { focus.manager = fm }
        CompositionLocalProvider(LocalBackDispatcher provides back) {
            VellutoTheme {
                Box(Modifier.fillMaxSize().background(Palette.Background)) {
                    AppRoot(vm)
                }
            }
        }
    }
}
