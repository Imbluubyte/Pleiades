package com.pleiades.app.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pleiades.app.core.ExportOpts
import com.pleiades.app.core.Kind
import com.pleiades.app.core.Sprite
import com.pleiades.app.core.Tool
import com.pleiades.app.core.ToolState

sealed class Dlg {
    object New : Dlg()
    object Export : Dlg()
    class Close(val index: Int) : Dlg()
    class Rename(val index: Int) : Dlg()
}

class AppState {
    val tabs = mutableStateListOf<Sprite>()
    var active by mutableIntStateOf(0)
    var tool by mutableStateOf(Tool.PEN)
    var color by mutableIntStateOf(0xFF000000.toInt())
    var size by mutableFloatStateOf(1f)
    var opacity by mutableFloatStateOf(1f)
    var filled by mutableStateOf(false)
    var tolerance by mutableIntStateOf(0)
    var onion by mutableStateOf(false)
    var onionBefore by mutableIntStateOf(1)
    var onionAfter by mutableIntStateOf(1)
    var grid by mutableStateOf(true)
    var playing by mutableStateOf(false)
    var panel by mutableIntStateOf(0)
    var dlg: Dlg? by mutableStateOf(null)
    var message: String? by mutableStateOf(null)
    var lastExportDir: Uri? by mutableStateOf(null)
    var pendingExport: ExportOpts? = null

    val sprite: Sprite? get() = tabs.getOrNull(active)

    fun toolState() = ToolState(tool, color, size, opacity, filled, tolerance)

    fun open(s: Sprite) {
        tabs.add(s); active = tabs.size - 1; playing = false
        size = if (s.kind == Kind.PIXEL) 1f else 4f
        tolerance = if (s.kind == Kind.PIXEL) 0 else 24
    }

    fun close(i: Int) {
        tabs.removeAt(i); playing = false
        active = active.coerceAtMost(tabs.size - 1).coerceAtLeast(0)
    }

    fun say(m: String) { message = m }
}
