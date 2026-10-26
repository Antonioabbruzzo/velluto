package com.velluto.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
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

private val TitleBarColor = Color(0xFF070708)
private val MIN_SIZE = DpSize(800.dp, 480.dp)

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
    // Il player (doppio clic, tasto F, pulsante "Schermo intero") usa la stessa funzione.
    vm.platform.toggleFullscreen = { toggleFullscreen() }

    // "Ingrandisci" (anche con Win+↑) porta direttamente a schermo intero, come una TV.
    LaunchedEffect(windowState.placement) {
        if (windowState.placement == WindowPlacement.Maximized) {
            windowState.placement = WindowPlacement.Fullscreen
        }
    }

    Window(
        onCloseRequest = {
            vm.dispose()
            exitApplication()
        },
        title = "Velluto",
        state = windowState,
        // Senza la cornice di Windows: la barra del titolo la disegniamo noi, così in schermo intero
        // sparisce del tutto (con la cornice standard Windows lasciava i bordi in alto).
        undecorated = true,
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
        val fullscreen = windowState.placement == WindowPlacement.Fullscreen
        CompositionLocalProvider(LocalBackDispatcher provides back) {
            VellutoTheme {
                Column(Modifier.fillMaxSize().background(Palette.Background)) {
                    if (!fullscreen) {
                        TitleBar(
                            onMinimize = { windowState.isMinimized = true },
                            onFullscreen = { toggleFullscreen() },
                            onClose = {
                                vm.dispose()
                                exitApplication()
                            },
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AppRoot(vm)
                        if (!fullscreen) ResizeGrip(windowState, Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
        }
    }
}

/** Barra del titolo di Velluto: si trascina per spostare la finestra. */
@Composable
private fun FrameWindowScope.TitleBar(onMinimize: () -> Unit, onFullscreen: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(34.dp).background(TitleBarColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WindowDraggableArea(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxSize().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("VELL", color = Palette.Accent, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text("UTO", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        }
        TitleButton("—", "Riduci a icona", Color.White.copy(alpha = 0.12f), onMinimize)
        TitleButton("□", "Schermo intero", Color.White.copy(alpha = 0.12f), onFullscreen)
        TitleButton("✕", "Chiudi", Color(0xFFC42B1C), onClose)
    }
}

/** Pulsante della barra del titolo: solo mouse, così le frecce della tastiera non ci finiscono sopra. */
@Composable
private fun TitleButton(symbol: String, description: String, hoverColor: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(if (hovered) hoverColor else Color.Transparent)
            .hoverable(interaction)
            .pointerInput(onClick) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = Color.White, fontSize = 14.sp)
    }
}

/** Angolo in basso a destra: trascinandolo si ridimensiona la finestra. */
@Composable
private fun ResizeGrip(state: WindowState, modifier: Modifier) {
    val density = LocalDensity.current
    Box(
        modifier
            .size(18.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    with(density) {
                        state.size = DpSize(
                            (state.size.width + drag.x.toDp()).coerceAtLeast(MIN_SIZE.width),
                            (state.size.height + drag.y.toDp()).coerceAtLeast(MIN_SIZE.height),
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("◢", color = Color.White.copy(alpha = 0.25f), fontSize = 12.sp)
    }
}
