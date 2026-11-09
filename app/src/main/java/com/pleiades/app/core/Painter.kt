package com.pleiades.app.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Tool(val label: String) {
    PEN("Pen"), ERASER("Eraser"), PICKER("Eyedropper"), FILL("Fill"),
    RECT("Rectangle"), ELLIPSE("Ellipse"), CENTRE("Centre"),
}

class ToolState(
    val tool: Tool, val color: Int, val size: Float, val opacity: Float,
    val filled: Boolean, val tolerance: Int,
)

/** Runs one gesture (down -> move* -> up) of a tool against a sprite's current cel. */
class Painter(private val s: Sprite) {
    var active = false; private set
    private var st: ToolState? = null
    private var snap: Bitmap? = null
    private var mask: BooleanArray? = null
    private var pts = ArrayList<PointF>()
    private var sx = 0f; private var sy = 0f
    private var lastX = 0; private var lastY = 0
    private val pixel get() = s.kind == Kind.PIXEL
    private val vector get() = s.kind == Kind.VECTOR

    private val clear = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

    private fun paint(state: ToolState, erase: Boolean) = Paint().apply {
        isAntiAlias = !pixel; isDither = false; isFilterBitmap = false
        color = state.color; alpha = (state.opacity * 255).toInt()
        if (erase) xfermode = clear
    }

    /** Returns a picked colour for the eyedropper, else null. */
    fun begin(state: ToolState, x: Float, y: Float): Int? {
        st = state; sx = x; sy = y
        when (state.tool) {
            Tool.PICKER -> {
                val xi = x.toInt(); val yi = y.toInt()
                if (xi !in 0 until s.w || yi !in 0 until s.h) return null
                val c = s.composite(s.cur).getPixel(xi, yi)
                return if ((c ushr 24) == 0) null else c or (0xFF shl 24)
            }
            Tool.FILL -> { fill(state, x.toInt(), y.toInt()); return null }
            Tool.CENTRE -> return null
            else -> {}
        }
        s.pushUndo()
        snap = s.cel().copy(Bitmap.Config.ARGB_8888, true)
        active = true
        pts = arrayListOf(PointF(x, y))
        if (state.tool == Tool.PEN || state.tool == Tool.ERASER) {
            if (pixel) {
                mask = BooleanArray(s.w * s.h)
                lastX = floor(x).toInt(); lastY = floor(y).toInt()
                stamp(lastX, lastY)
            } else strokeRedraw()
        }
        s.touch()
        return null
    }

    fun move(x: Float, y: Float) {
        val state = st ?: return
        if (!active) return
        when (state.tool) {
            Tool.PEN, Tool.ERASER -> {
                if (pixel) {
                    val nx = floor(x).toInt(); val ny = floor(y).toInt()
                    line(lastX, lastY, nx, ny); lastX = nx; lastY = ny
                } else {
                    pts.add(PointF(x, y)); strokeRedraw()
                }
            }
            Tool.RECT, Tool.ELLIPSE -> shapeRedraw(x, y)
            else -> {}
        }
        s.touch()
    }

    fun end(x: Float, y: Float) {
        val state = st ?: return
        if (!active) return
        if (state.tool == Tool.RECT || state.tool == Tool.ELLIPSE) {
            shapeRedraw(x, y)
            if (vector) { s.vec().add(shapeObj(x, y)); Vec.render(s, s.cel(), s.vec()) }
        } else if ((state.tool == Tool.PEN) && vector) {
            s.vec().add(VStroke(pts.toList(), state.color, state.opacity, state.size)); Vec.render(s, s.cel(), s.vec())
        }
        finish()
    }

    fun cancel() {
        if (!active) return
        if (vector && st?.tool == Tool.ERASER) { finish(); s.undo(); return }
        snap?.let { restore(it) }
        s.dropUndo(); finish()
    }

    private fun finish() { active = false; snap = null; mask = null; st = null; s.touch() }

    private fun restore(b: Bitmap) { Canvas(s.cel()).drawBitmap(b, 0f, 0f, Sprite.SRC) }

    // ---- pen / eraser ----
    private fun strokeRedraw() {
        val state = st!!; val bmp = s.cel()
        val erase = state.tool == Tool.ERASER
        if (vector && erase) {
            val v = s.vec(); val p = pts.last()
            val kept = v.filterNot { it.hit(p.x, p.y, state.size / 2) }
            if (kept.size != v.size) { v.clear(); v.addAll(kept); Vec.render(s, bmp, v) }
            return
        }
        restore(snap!!)
        val c = Canvas(bmp)
        if (vector) { VStroke(pts, state.color, state.opacity, state.size).draw(c, Paint(Paint.ANTI_ALIAS_FLAG)); return }
        val p = paint(state, erase).apply {
            style = Paint.Style.STROKE; strokeWidth = state.size
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val path = android.graphics.Path()
        path.moveTo(pts[0].x, pts[0].y)
        if (pts.size == 1) path.lineTo(pts[0].x + 0.01f, pts[0].y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        c.drawPath(path, p)
    }

    private fun stamp(cx: Int, cy: Int) {
        val state = st!!; val m = mask!!; val bmp = s.cel()
        val n = state.size.roundToInt().coerceAtLeast(1)
        val off = (n - 1) / 2
        val p = paint(state, state.tool == Tool.ERASER)
        val c = Canvas(bmp)
        for (dy in 0 until n) for (dx in 0 until n) {
            val px = cx - off + dx; val py = cy - off + dy
            if (px < 0 || py < 0 || px >= s.w || py >= s.h) continue
            val i = py * s.w + px
            if (m[i]) continue
            m[i] = true
            c.drawRect(px.toFloat(), py.toFloat(), px + 1f, py + 1f, p)
        }
    }

    private fun line(x0: Int, y0: Int, x1: Int, y1: Int) {
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
        val sxs = if (x0 < x1) 1 else -1; val sys = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            stamp(x, y)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sxs }
            if (e2 <= dx) { err += dx; y += sys }
        }
    }

    // ---- shapes ----
    private fun shapeRect(x: Float, y: Float): RectF {
        var l = min(sx, x); var r = max(sx, x); var t = min(sy, y); var b = max(sy, y)
        if (pixel) { l = floor(l); t = floor(t); r = floor(r) + 1; b = floor(b) + 1 }
        return RectF(l, t, r, b)
    }

    private fun shapeObj(x: Float, y: Float): VShape {
        val state = st!!; val r = shapeRect(x, y)
        if (!state.filled) r.inset(state.size / 2, state.size / 2)
        return VShape(if (state.tool == Tool.RECT) 0 else 1, r, state.filled, state.color, state.opacity, state.size)
    }

    private fun shapeRedraw(x: Float, y: Float) {
        val state = st!!
        restore(snap!!)
        val c = Canvas(s.cel())
        val o = shapeObj(x, y)
        if (vector) { o.draw(c, Paint(Paint.ANTI_ALIAS_FLAG)); return }
        val p = paint(state, false)
        p.style = if (state.filled) Paint.Style.FILL else Paint.Style.STROKE
        p.strokeWidth = if (pixel) state.size.roundToInt().coerceAtLeast(1).toFloat() else state.size
        if (state.tool == Tool.RECT) c.drawRect(o.r, p) else c.drawOval(o.r, p)
    }

    // ---- fill ----
    private fun fill(state: ToolState, x: Int, y: Int) {
        if (x !in 0 until s.w || y !in 0 until s.h) return
        if (vector) {
            val v = s.vec()
            val i = v.indexOfLast { it is VShape && it.hit(x.toFloat(), y.toFloat(), 0f) }
            if (i < 0) return
            s.pushUndo()
            v[i] = (v[i] as VShape).withFill(true, state.color)
            Vec.render(s, s.cel(), v); s.touch(); return
        }
        val w = s.w; val h = s.h
        val bmp = s.cel()
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        val target = px[y * w + x]
        val tol = state.tolerance
        val fillCol = (state.color and 0xFFFFFF) or ((state.opacity * 255).toInt() shl 24)
        if (tol == 0 && state.opacity >= 1f && target == fillCol) return
        s.pushUndo()
        val seen = BooleanArray(w * h)
        val stack = IntArray(w * h * 2 / 1 + 4); var sp = 0
        fun match(c: Int) = max(max(abs((c ushr 24) - (target ushr 24)), abs(((c shr 16) and 255) - ((target shr 16) and 255))),
            max(abs(((c shr 8) and 255) - ((target shr 8) and 255)), abs((c and 255) - (target and 255)))) <= tol
        stack[sp++] = x; stack[sp++] = y
        while (sp > 0) {
            val cy = stack[--sp]; val cx = stack[--sp]
            var l = cx
            while (l >= 0 && !seen[cy * w + l] && match(px[cy * w + l])) l--
            l++
            var r = cx
            while (r < w && !seen[cy * w + r] && match(px[cy * w + r])) r++
            r--
            if (l > r) continue
            for (xx in l..r) seen[cy * w + xx] = true
            for (ny in intArrayOf(cy - 1, cy + 1)) {
                if (ny < 0 || ny >= h) continue
                var inRun = false
                for (xx in l..r) {
                    val ok = !seen[ny * w + xx] && match(px[ny * w + xx])
                    if (ok && !inRun && sp + 2 <= stack.size) { stack[sp++] = xx; stack[sp++] = ny }
                    inRun = ok
                }
            }
        }
        for (i in px.indices) if (seen[i]) px[i] = over(fillCol, px[i])
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        s.touch()
    }

    private fun over(src: Int, dst: Int): Int {
        val sa = src ushr 24
        if (sa == 255) return src
        val da = dst ushr 24
        val oa = sa + da * (255 - sa) / 255
        if (oa == 0) return 0
        fun ch(sh: Int): Int {
            val sc = (src shr sh) and 255; val dc = (dst shr sh) and 255
            return ((sc * sa + dc * da * (255 - sa) / 255) / oa).coerceIn(0, 255)
        }
        return (oa shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
