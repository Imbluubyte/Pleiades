package com.pleiades.app.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.hypot

/** Immutable vector objects; a cel's bitmap is always re-rendered from its list. */
sealed class VObj {
    abstract val color: Int
    abstract val opacity: Float
    abstract val size: Float
    abstract fun moved(dx: Float, dy: Float): VObj
    abstract fun hit(x: Float, y: Float, tol: Float): Boolean
    abstract fun draw(c: Canvas, p: Paint)
    abstract fun svg(): String
    protected fun hex() = "#%06X".format(color and 0xFFFFFF)
}

class VStroke(val pts: List<PointF>, override val color: Int, override val opacity: Float, override val size: Float) : VObj() {
    private fun path() = Path().also { p ->
        p.moveTo(pts[0].x, pts[0].y)
        if (pts.size == 1) p.lineTo(pts[0].x + 0.01f, pts[0].y)
        for (i in 1 until pts.size) p.lineTo(pts[i].x, pts[i].y)
    }
    override fun moved(dx: Float, dy: Float) = VStroke(pts.map { PointF(it.x + dx, it.y + dy) }, color, opacity, size)
    override fun hit(x: Float, y: Float, tol: Float) = pts.any { hypot(it.x - x, it.y - y) <= tol + size / 2 }
    override fun draw(c: Canvas, p: Paint) {
        p.style = Paint.Style.STROKE; p.strokeWidth = size
        p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.color = color; p.alpha = (opacity * 255).toInt()
        c.drawPath(path(), p)
    }
    override fun svg() = "<path d=\"" + pts.mapIndexed { i, q -> (if (i == 0) "M" else "L") + "${q.x} ${q.y}" }.joinToString(" ") +
        (if (pts.size == 1) " l0.01 0" else "") +
        "\" fill=\"none\" stroke=\"${hex()}\" stroke-opacity=\"$opacity\" stroke-width=\"$size\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>"
}

/** kind 0 = rectangle, 1 = ellipse. */
class VShape(val kind: Int, val r: RectF, val filled: Boolean, override val color: Int, override val opacity: Float, override val size: Float) : VObj() {
    fun withFill(f: Boolean, col: Int) = VShape(kind, r, f, col, opacity, size)
    override fun moved(dx: Float, dy: Float) = VShape(kind, RectF(r).apply { offset(dx, dy) }, filled, color, opacity, size)
    override fun hit(x: Float, y: Float, tol: Float): Boolean {
        val grown = RectF(r).apply { inset(-tol - size / 2, -tol - size / 2) }
        if (!grown.contains(x, y)) return false
        if (filled) return if (kind == 0) true else {
            val nx = (x - r.centerX()) / (grown.width() / 2); val ny = (y - r.centerY()) / (grown.height() / 2)
            nx * nx + ny * ny <= 1f
        }
        // outline only: near the edge band
        val inner = RectF(r).apply { inset(size / 2 + tol, size / 2 + tol) }
        return !(inner.width() > 0 && inner.height() > 0 && inner.contains(x, y))
    }
    override fun draw(c: Canvas, p: Paint) {
        p.style = if (filled) Paint.Style.FILL else Paint.Style.STROKE; p.strokeWidth = size
        p.color = color; p.alpha = (opacity * 255).toInt()
        if (kind == 0) c.drawRect(r, p) else c.drawOval(r, p)
    }
    override fun svg(): String {
        val fill = if (filled) "fill=\"${hex()}\" fill-opacity=\"$opacity\"" else "fill=\"none\" stroke=\"${hex()}\" stroke-opacity=\"$opacity\" stroke-width=\"$size\""
        return if (kind == 0) "<rect x=\"${r.left}\" y=\"${r.top}\" width=\"${r.width()}\" height=\"${r.height()}\" $fill/>"
        else "<ellipse cx=\"${r.centerX()}\" cy=\"${r.centerY()}\" rx=\"${r.width() / 2}\" ry=\"${r.height() / 2}\" $fill/>"
    }
}

object Vec {
    fun render(s: Sprite, bmp: Bitmap, list: List<VObj>) {
        bmp.eraseColor(0)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (o in list) o.draw(c, p)
    }

    fun svg(s: Sprite, frame: Int): String {
        val sb = StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${s.w}\" height=\"${s.h}\" viewBox=\"0 0 ${s.w} ${s.h}\">\n")
        for (i in s.layers.indices) {
            if (!s.layers[i].visible) continue
            sb.append("<g opacity=\"${s.layers[i].opacity}\">\n")
            s.frames[frame].vecs[i].forEach { sb.append(it.svg()).append('\n') }
            sb.append("</g>\n")
        }
        return sb.append("</svg>\n").toString()
    }
}
