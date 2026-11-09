package com.pleiades.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Hue ring with a saturation/value square inside. */
@Composable
fun ColorWheel(color: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var hsv by remember { mutableStateOf(floatArrayOf(0f, 0f, 0f).also { android.graphics.Color.colorToHSV(color, it) }) }
    LaunchedEffect(color) {
        if (android.graphics.Color.HSVToColor(hsv) != color) {
            val n = FloatArray(3); android.graphics.Color.colorToHSV(color, n)
            // keep hue when the colour is grey/black so the ring doesn't jump
            if (n[1] < 0.01f || n[2] < 0.01f) n[0] = hsv[0]
            hsv = n
        }
    }
    fun push(h: Float, s: Float, v: Float) {
        hsv = floatArrayOf(h, s, v)
        onChange(android.graphics.Color.HSVToColor(hsv))
    }
    Canvas(
        modifier.fillMaxWidth().aspectRatio(1f).pointerInput(Unit) {
            awaitEachGesture {
                val d = awaitFirstDown()
                val sz = size.width.toFloat()
                val c = Offset(sz / 2, sz / 2)
                val outer = sz / 2; val thick = sz * 0.13f; val innerR = outer - thick
                val side = innerR * sqrt(2f) * 0.92f
                val sqL = c.x - side / 2; val sqT = c.y - side / 2
                val ring = (d.position - c).getDistance() > innerR
                fun apply(p: Offset) {
                    if (ring) {
                        val a = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()
                        push((a + 360f) % 360f, hsv[1], hsv[2])
                    } else push(hsv[0], ((p.x - sqL) / side).coerceIn(0f, 1f), 1f - ((p.y - sqT) / side).coerceIn(0f, 1f))
                }
                apply(d.position); d.consume()
                drag(d.id) { ch -> apply(ch.position); ch.consume() }
            }
        },
    ) {
        val sz = size.width
        val c = Offset(sz / 2, sz / 2)
        val outer = sz / 2; val thick = sz * 0.13f; val innerR = outer - thick
        val hues = (0..12).map { Color.hsv((it * 30f) % 360f, 1f, 1f) }
        drawCircle(Brush.sweepGradient(hues, c), radius = outer - thick / 2, center = c, style = Stroke(thick))
        val side = innerR * sqrt(2f) * 0.92f
        val tl = Offset(c.x - side / 2, c.y - side / 2)
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hsv[0], 1f, 1f)), tl.x, tl.x + side), tl, Size(side, side))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), tl.y, tl.y + side), tl, Size(side, side))
        // markers
        val ang = Math.toRadians(hsv[0].toDouble())
        val hr = outer - thick / 2
        val hp = Offset(c.x + (hr * cos(ang)).toFloat(), c.y + (hr * sin(ang)).toFloat())
        drawCircle(Color.White, thick * 0.42f, hp, style = Stroke(3f))
        drawCircle(Color.Black, thick * 0.42f + 2f, hp, style = Stroke(1.5f))
        val sp = Offset(tl.x + hsv[1] * side, tl.y + (1f - hsv[2]) * side)
        drawCircle(Color.White, 9f, sp, style = Stroke(3f))
        drawCircle(Color.Black, 11f, sp, style = Stroke(1.5f))
    }
}

fun Int.hex(): String = "#%06X".format(this and 0xFFFFFF)
fun Int.toC() = Color(this)
fun Color.toInt() = this.toArgb()
