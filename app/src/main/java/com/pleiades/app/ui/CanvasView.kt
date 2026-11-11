package com.pleiades.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pleiades.app.core.Kind
import com.pleiades.app.core.Painter
import com.pleiades.app.core.Sprite
import com.pleiades.app.core.Tool
import kotlin.math.roundToInt

@Composable
fun CanvasView(app: AppState, sp: Sprite) {
    val painter = remember(sp) { Painter(sp) }
    val pixel = sp.kind == Kind.PIXEL
    val rev = sp.rev
    val cur = sp.cur
    val img = remember(rev, cur) { sp.composite(cur).asImageBitmap() }
    val onionImgs = remember(rev, cur, app.onion, app.onionBefore, app.onionAfter, app.playing) {
        if (!app.onion || app.playing) emptyList() else {
            val l = ArrayList<Pair<androidx.compose.ui.graphics.ImageBitmap, Boolean>>()
            for (k in 1..app.onionBefore) (cur - k).takeIf { it >= 0 }?.let { l.add(sp.composite(it).asImageBitmap() to true) }
            for (k in 1..app.onionAfter) (cur + k).takeIf { it < sp.frames.size }?.let { l.add(sp.composite(it).asImageBitmap() to false) }
            l
        }
    }
    val checkerPx = with(LocalDensity.current) { 10.dp.toPx() }

    Box(Modifier.fillMaxSize().background(Color(0xFF101016)).clipToBounds()) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(sp, app.playing) {
                if (app.playing) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun toS(o: Offset) = Offset((o.x - sp.panX) / sp.zoom, (o.y - sp.panY) / sp.zoom)
                        var p = toS(down.position)
                        val picked = painter.begin(app.toolState(), p.x, p.y)
                        if (picked != null) app.color = picked
                        down.consume()
                        var multi = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                if (!multi) { multi = true; painter.cancel() }
                                val z = ev.calculateZoom(); val pan = ev.calculatePan(); val c = ev.calculateCentroid()
                                val nz = (sp.zoom * z).coerceIn(0.25f, 80f)
                                val k = nz / sp.zoom
                                sp.panX = c.x - (c.x - sp.panX) * k + pan.x
                                sp.panY = c.y - (c.y - sp.panY) * k + pan.y
                                sp.zoom = nz
                                ev.changes.forEach { it.consume() }
                            } else if (!multi) {
                                val ch = ev.changes.firstOrNull() ?: continue
                                p = toS(ch.position)
                                if (!ch.pressed) { painter.end(p.x, p.y); ch.consume(); break }
                                if (app.tool == Tool.PICKER) {
                                    painter.begin(app.toolState(), p.x, p.y)?.let { app.color = it }
                                } else painter.move(p.x, p.y)
                                ch.consume()
                            }
                            if (pressed.isEmpty()) { if (painter.active) painter.cancel(); break }
                        }
                    }
                }
            },
        ) {
            val vw = size.width; val vh = size.height
            if (sp.zoom <= 0f) {
                sp.zoom = (minOf(vw / sp.w, vh / sp.h) * 0.85f).let { if (pixel && it > 1f) it.toInt().toFloat() else it }.coerceAtLeast(0.25f)
                sp.panX = (vw - sp.w * sp.zoom) / 2f; sp.panY = (vh - sp.h * sp.zoom) / 2f
            }
            val z = sp.zoom
            val l = sp.panX; val t = sp.panY; val r = l + sp.w * z; val b = t + sp.h * z
            clipRect(l, t, r, b) {
                val n = checkerPx
                var yy = t; var row = 0
                while (yy < b) {
                    var xx = l; var col = row % 2
                    while (xx < r) {
                        drawRect(if (col % 2 == 0) Color(0xFF3A3A4A) else Color(0xFF2C2C3A), Offset(xx, yy), Size(n, n))
                        xx += n; col++
                    }
                    yy += n; row++
                }
            }
            val dst = IntSize((sp.w * z).roundToInt(), (sp.h * z).roundToInt())
            val off = IntOffset(l.roundToInt(), t.roundToInt())
            val fq = if (pixel || z >= 3f) FilterQuality.None else FilterQuality.Medium
            for ((im, before) in onionImgs) {
                drawImage(im, dstOffset = off, dstSize = dst, alpha = if (before) 0.35f else 0.28f, filterQuality = fq)
            }
            drawImage(img, dstOffset = off, dstSize = dst, filterQuality = fq)
            if (app.grid && pixel && z >= 6f) {
                val gc = Color(0x33FFFFFF)
                for (x in 0..sp.w) drawLine(gc, Offset(l + x * z, t), Offset(l + x * z, b), 1f)
                for (y in 0..sp.h) drawLine(gc, Offset(l, t + y * z), Offset(r, t + y * z), 1f)
            }
            drawRect(Color(0x66FFFFFF), Offset(l, t), Size(r - l, b - t), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        }
    }
}
