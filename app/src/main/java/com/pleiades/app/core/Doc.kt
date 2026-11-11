package com.pleiades.app.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.runtime.getValue
import android.net.Uri
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Kind(val label: String) { PIXEL("Pixel"), RASTER("Raster"), VECTOR("Vector") }

class Layer(var name: String, var visible: Boolean = true, var opacity: Float = 1f)

class Frame(
    val cels: MutableList<Bitmap>,
    val vecs: MutableList<MutableList<VObj>> = cels.map { mutableListOf<VObj>() }.toMutableList(),
    var durationMs: Int = 100,
) {
    fun copy() = Frame(
        cels.map { it.copy(Bitmap.Config.ARGB_8888, true) }.toMutableList(),
        vecs.map { it.toMutableList() }.toMutableList(), durationMs,
    )
}

/** A named animation (walk, idle, hit…): its own frame list and speed. All animations of a sprite share its layers. */
class Anim(name: String, val frames: MutableList<Frame>, fps: Int = 12) {
    var name by mutableStateOf(name)
    var fps by mutableIntStateOf(fps)
}

private class Snap(val frame: Frame, val layer: Int, val bmp: Bitmap, val vec: List<VObj>)

/**
 * One open document. Layers are shared by all frames and animations; each frame owns one cel (bitmap) per layer.
 * [frames], [fps] and [cur] always refer to the current animation ([curAnim]).
 */
class Sprite(var name: String, val w: Int, val h: Int, val kind: Kind) {
    /** Bump to make Compose redraw. */
    var rev by mutableIntStateOf(0)
    val layers = mutableListOf(Layer("Layer 1"))
    val anims = mutableListOf(Anim("Anim 1", mutableListOf(Frame(mutableListOf(blank())))))
    var curAnim by mutableIntStateOf(0)
    val anim get() = anims[curAnim]
    val frames get() = anim.frames
    val allFrames get() = anims.flatMap { it.frames }
    var cur by mutableIntStateOf(0)
    var curLayer by mutableIntStateOf(0)
    var fps: Int
        get() = anim.fps
        set(v) { anim.fps = v }
    /** View transform (zoom 0 = not yet fitted) and file state; UI-owned but per document. */
    var zoom by mutableFloatStateOf(0f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)
    var fileUri: Uri? = null
    var savedRev = 0
    val dirty get() = rev != savedRev

    private val undoS = ArrayDeque<Snap>()
    private val redoS = ArrayDeque<Snap>()
    val canUndo get() = undoS.isNotEmpty()
    val canRedo get() = redoS.isNotEmpty()

    fun blank(): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    fun cel(): Bitmap = frames[cur].cels[curLayer]
    fun vec(): MutableList<VObj> = frames[cur].vecs[curLayer]
    fun touch() { rev++ }

    fun pushUndo() {
        undoS.addLast(Snap(frames[cur], curLayer, cel().copy(Bitmap.Config.ARGB_8888, true), vec().toList()))
        while (undoS.size > 40 || undoS.sumOf { it.bmp.byteCount.toLong() } > 96_000_000L) undoS.removeFirst()
        redoS.clear()
    }

    private fun swap(from: ArrayDeque<Snap>, to: ArrayDeque<Snap>) {
        val s = from.removeLastOrNull() ?: return
        val fi = frames.indexOf(s.frame)
        if (fi < 0 || s.layer >= layers.size) return
        val target = s.frame.cels[s.layer]
        val v = s.frame.vecs[s.layer]
        to.addLast(Snap(s.frame, s.layer, target.copy(Bitmap.Config.ARGB_8888, true), v.toList()))
        Canvas(target).drawBitmap(s.bmp, 0f, 0f, SRC)
        v.clear(); v.addAll(s.vec)
        cur = fi; curLayer = s.layer
        touch()
    }
    fun undo() = swap(undoS, redoS)
    fun redo() = swap(redoS, undoS)
    fun dropUndo() { undoS.removeLastOrNull() }
    fun clearHistory() { undoS.clear(); redoS.clear() }

    /** Flatten one frame (visible layers). [layerOnly] limits to a single layer index. */
    fun composite(f: Int, bg: Int = 0): Bitmap = composite(frames[f], bg)

    fun composite(fr: Frame, bg: Int = 0): Bitmap {
        val out = blank()
        val c = Canvas(out)
        if (bg != 0) c.drawColor(bg)
        val p = Paint()
        for (i in layers.indices) {
            val l = layers[i]
            if (!l.visible) continue
            p.alpha = (l.opacity * 255).toInt()
            c.drawBitmap(fr.cels[i], 0f, 0f, p)
        }
        return out
    }

    // ---- frames ----
    fun addFrame(duplicate: Boolean) {
        val nf = if (duplicate) frames[cur].copy() else Frame(layers.map { blank() }.toMutableList(), durationMs = frames[cur].durationMs)
        frames.add(cur + 1, nf); cur++; clearHistory(); touch()
    }
    fun deleteFrame() {
        if (frames.size <= 1) return
        frames.removeAt(cur); cur = cur.coerceAtMost(frames.size - 1); clearHistory(); touch()
    }
    fun moveFrame(dir: Int) {
        val t = cur + dir
        if (t !in frames.indices) return
        frames.add(t, frames.removeAt(cur)); cur = t; clearHistory(); touch()
    }

    // ---- animations ----
    fun uniqueAnimName(base: String, except: Anim? = null): String {
        val taken = anims.filter { it !== except }.map { it.name }.toSet()
        if (base !in taken) return base
        var n = 2
        while ("$base $n" in taken) n++
        return "$base $n"
    }
    fun selectAnim(i: Int) {
        if (i !in anims.indices || i == curAnim) return
        curAnim = i; cur = 0; clearHistory(); touch()
    }
    fun addAnim(duplicate: Boolean) {
        val a = if (duplicate) Anim(uniqueAnimName("${anim.name} copy"), frames.map { it.copy() }.toMutableList(), anim.fps)
        else Anim(uniqueAnimName("Anim ${anims.size + 1}"),
            mutableListOf(Frame(layers.map { blank() }.toMutableList(), durationMs = frames[cur].durationMs)), anim.fps)
        anims.add(curAnim + 1, a); curAnim++; cur = 0; clearHistory(); touch()
    }
    fun deleteAnim() {
        if (anims.size <= 1) return
        anims.removeAt(curAnim); curAnim = curAnim.coerceAtMost(anims.size - 1); cur = 0; clearHistory(); touch()
    }
    fun renameAnim(n: String) { anim.name = uniqueAnimName(n, anim); touch() }

    // ---- layers ----
    fun addLayer() {
        layers.add(curLayer + 1, Layer("Layer ${layers.size + 1}"))
        allFrames.forEach { it.cels.add(curLayer + 1, blank()); it.vecs.add(curLayer + 1, mutableListOf()) }
        curLayer++; clearHistory(); touch()
    }
    fun deleteLayer() {
        if (layers.size <= 1) return
        layers.removeAt(curLayer); allFrames.forEach { it.cels.removeAt(curLayer); it.vecs.removeAt(curLayer) }
        curLayer = curLayer.coerceAtMost(layers.size - 1); clearHistory(); touch()
    }
    fun moveLayer(dir: Int) {
        val t = curLayer + dir
        if (t !in layers.indices) return
        layers.add(t, layers.removeAt(curLayer))
        allFrames.forEach { it.cels.add(t, it.cels.removeAt(curLayer)); it.vecs.add(t, it.vecs.removeAt(curLayer)) }
        curLayer = t; clearHistory(); touch()
    }

    /** Shift the current layer's content so its bounding box is centred on the canvas. */
    fun centreLayer() {
        val b = cel()
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) != 0) {
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
        }
        if (x1 < 0) return
        if (kind == Kind.VECTOR) { pushUndo(); val v = vec(); val dxv = ((w - (x1 - x0 + 1)) / 2 - x0).toFloat(); val dyv = ((h - (y1 - y0 + 1)) / 2 - y0).toFloat(); val moved = v.map { it.moved(dxv, dyv) }; v.clear(); v.addAll(moved); Vec.render(this, cel(), v); touch(); return }
        val dx = (w - (x1 - x0 + 1)) / 2 - x0
        val dy = (h - (y1 - y0 + 1)) / 2 - y0
        if (dx == 0 && dy == 0) return
        pushUndo()
        val copy = b.copy(Bitmap.Config.ARGB_8888, true)
        b.eraseColor(0)
        Canvas(b).drawBitmap(copy, dx.toFloat(), dy.toFloat(), null)
        touch()
    }

    /** Unique colours across every cel, most used first (bucketed for non-pixel art). */
    fun palette(max: Int = 48): List<Int> {
        val counts = HashMap<Int, Int>()
        val q = kind != Kind.PIXEL
        val px = IntArray(w * h)
        val stride = if (w * h > 400_000) 7 else 1
        for (f in allFrames) for (c in f.cels) {
            c.getPixels(px, 0, w, 0, 0, w, h)
            var i = 0
            while (i < px.size) {
                val v = px[i]
                if ((v ushr 24) >= 128) {
                    val k = if (q) (0xFF shl 24) or (v and 0xF8F8F8) else (0xFF shl 24) or (v and 0xFFFFFF)
                    counts[k] = (counts[k] ?: 0) + 1
                }
                i += stride
            }
        }
        return counts.entries.sortedByDescending { it.value }.take(max).map { it.key }
    }

    companion object {
        val SRC = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
    }
}
