package com.velluto.tv.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import java.awt.Component
import java.awt.Dimension
import java.awt.Frame

/**
 * La finestra è quella normale di Windows (trascinamento, Aero Snap, Win+frecce, bordi per
 * ridimensionare, più monitor: tutto come le altre app). Lo schermo intero invece lo facciamo noi:
 * togliamo la cornice alla finestra e la stendiamo su tutto il monitor in cui si trova, come fanno
 * i browser. Lo schermo intero di Java lasciava visibile la barra del titolo.
 */
internal class WindowMode(private val state: WindowState) {
    private var frame: Frame? = null
    var fullscreen by mutableStateOf(false)
        private set
    /** Schermo intero aperto da solo all'avvio di un video: si chiude da solo uscendo dal player. */
    private var autoFullscreen = false
    private var savedStyle = 0L
    private val savedRect = WinRect()
    private var wasMaximized = false

    /** Da chiamare quando la finestra è visibile. */
    fun attach(window: Frame) {
        frame = window
        window.minimumSize = Dimension(800, 480)
        Win32.darkTitleBar(window)
    }

    fun toggleFullscreen() {
        autoFullscreen = false
        applyFullscreen(!fullscreen)
    }

    fun exitFullscreen() {
        autoFullscreen = false
        applyFullscreen(false)
    }

    fun enterAutoFullscreen() {
        if (fullscreen) return
        autoFullscreen = true
        applyFullscreen(true)
    }

    fun leaveAutoFullscreen() {
        if (!autoFullscreen) return
        autoFullscreen = false
        applyFullscreen(false)
    }

    private fun applyFullscreen(on: Boolean) {
        if (on == fullscreen) return
        val f = frame ?: return
        val hwnd = Win32.hwnd(f)
        if (hwnd == null) {
            // Non Windows (o JNA non disponibile): schermo intero di Compose.
            state.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
            fullscreen = on
            return
        }
        if (on) {
            wasMaximized = f.extendedState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH
            if (wasMaximized) f.extendedState = Frame.NORMAL
            savedStyle = Win32.style(hwnd)
            Win32.windowRect(hwnd, savedRect)
            Win32.enterFullscreen(hwnd, savedStyle)
        } else {
            Win32.exitFullscreen(hwnd, savedStyle, savedRect)
            if (wasMaximized) f.extendedState = Frame.MAXIMIZED_BOTH
        }
        fullscreen = on
    }
}

@Structure.FieldOrder("left", "top", "right", "bottom")
class WinRect : Structure() {
    @JvmField var left = 0
    @JvmField var top = 0
    @JvmField var right = 0
    @JvmField var bottom = 0
}

@Structure.FieldOrder("cbSize", "rcMonitor", "rcWork", "dwFlags")
class WinMonitorInfo : Structure() {
    @JvmField var cbSize = 0
    @JvmField var rcMonitor = WinRect()
    @JvmField var rcWork = WinRect()
    @JvmField var dwFlags = 0

    init {
        cbSize = size()
    }
}

@Suppress("FunctionName")
interface User32Api : Library {
    fun GetWindowLongPtrW(hwnd: Pointer, index: Int): Long
    fun SetWindowLongPtrW(hwnd: Pointer, index: Int, value: Long): Long
    fun GetWindowRect(hwnd: Pointer, rect: WinRect): Boolean
    fun SetWindowPos(hwnd: Pointer, insertAfter: Pointer?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean
    fun MonitorFromWindow(hwnd: Pointer, flags: Int): Pointer?
    fun GetMonitorInfoW(monitor: Pointer, info: WinMonitorInfo): Boolean
}

@Suppress("FunctionName")
interface DwmApi : Library {
    fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
}

/** Le poche funzioni di Windows che servono, via JNA (già incluso con il player VLC). */
private object Win32 {
    private const val GWL_STYLE = -16
    private const val WS_CAPTION = 0x00C00000L
    private const val WS_THICKFRAME = 0x00040000L
    private const val MONITOR_DEFAULTTONEAREST = 2
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    private val user32: User32Api? by lazy {
        if (isWindows) runCatching { Native.load("user32", User32Api::class.java) }.getOrNull() else null
    }
    private val dwm: DwmApi? by lazy {
        if (isWindows) runCatching { Native.load("dwmapi", DwmApi::class.java) }.getOrNull() else null
    }

    fun hwnd(c: Component): Pointer? =
        if (user32 == null) null else runCatching { Native.getComponentPointer(c) }.getOrNull()

    fun style(hwnd: Pointer): Long = user32!!.GetWindowLongPtrW(hwnd, GWL_STYLE)

    fun windowRect(hwnd: Pointer, out: WinRect) {
        user32!!.GetWindowRect(hwnd, out)
    }

    /** Toglie barra del titolo e bordi e stende la finestra sul suo monitor (barra delle applicazioni compresa). */
    fun enterFullscreen(hwnd: Pointer, style: Long) {
        val u = user32!!
        u.SetWindowLongPtrW(hwnd, GWL_STYLE, style and (WS_CAPTION or WS_THICKFRAME).inv())
        val monitor = u.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST) ?: return
        val info = WinMonitorInfo()
        if (!u.GetMonitorInfoW(monitor, info)) return
        val r = info.rcMonitor
        u.SetWindowPos(hwnd, null, r.left, r.top, r.right - r.left, r.bottom - r.top, SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED)
    }

    /** Rimette barra del titolo, bordi, posizione e misura di prima. */
    fun exitFullscreen(hwnd: Pointer, style: Long, rect: WinRect) {
        val u = user32!!
        u.SetWindowLongPtrW(hwnd, GWL_STYLE, style)
        u.SetWindowPos(
            hwnd, null, rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top,
            SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED,
        )
    }

    /** Barra del titolo scura come l'app (colore preciso su Windows 11, scura su Windows 10). */
    fun darkTitleBar(c: Component) {
        val h = hwnd(c) ?: return
        val d = dwm ?: return
        runCatching {
            d.DwmSetWindowAttribute(h, DWMWA_USE_IMMERSIVE_DARK_MODE, IntByReference(1), 4)
            d.DwmSetWindowAttribute(h, DWMWA_CAPTION_COLOR, IntByReference(0x00080707), 4) // 0x00BBGGRR
            d.DwmSetWindowAttribute(h, DWMWA_TEXT_COLOR, IntByReference(0x00FFFFFF), 4)
        }
    }
}
