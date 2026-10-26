package com.velluto.tv.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
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
import kotlinx.coroutines.delay

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
                    // Posizione verticale del mouse, letta prima di qualsiasi schermata (passaggio Initial):
                    // serve alla barra "Esci da schermo intero", qualunque cosa ci sia sotto il mouse.
                    var pointerY by remember { mutableStateOf(Float.MAX_VALUE) }
                    Box(
                        Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val e = awaitPointerEvent(PointerEventPass.Initial)
                                    pointerY = if (e.type == PointerEventType.Exit) Float.MAX_VALUE
                                    else e.changes.firstOrNull()?.position?.y ?: pointerY
                                }
                            }
                        },
                    ) {
                        AppRoot(vm)
                        if (fullscreen) {
                            FullscreenExitBar(
                                pointerY = pointerY,
                                onExit = { windowState.placement = WindowPlacement.Floating },
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        } else {
                            ResizeGrip(windowState, Modifier.align(Alignment.BottomEnd))
                        }
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

/**
 * In schermo intero: portando il mouse sul bordo in alto scende una barra con la X per tornare
 * alla finestra normale (come nei browser). Si richiude da sola quando il mouse si allontana.
 */
@Composable
private fun FullscreenExitBar(pointerY: Float, onExit: () -> Unit, modifier: Modifier) {
    val density = LocalDensity.current
    val close = remember { MutableInteractionSource() }
    val closeHovered by close.collectIsHoveredAsState()
    var visible by remember { mutableStateOf(false) }
    // Si apre col mouse negli ultimi pixel in alto; resta aperta finché il mouse è sulla barra.
    val atEdge = with(density) { pointerY <= 12.dp.toPx() }
    val onBar = with(density) { pointerY <= 80.dp.toPx() }
    LaunchedEffect(atEdge, onBar) {
        if (atEdge) visible = true
        else if (!onBar) {
            delay(600)
            visible = false
        }
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .shadow(16.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xE6141418))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                    .padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Esci da schermo intero", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("   Esc", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                Spacer(Modifier.width(14.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (closeHovered) Palette.Accent else Color.White.copy(alpha = 0.14f))
                        .hoverable(close)
                        .pointerInput(onExit) { detectTapGestures { onExit() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
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
