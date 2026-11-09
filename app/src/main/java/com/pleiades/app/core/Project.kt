package com.pleiades.app.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** .pls project = zip of meta.json + one PNG per cel. */
object Project {
    private fun vJson(o: VObj): JSONObject = JSONObject().apply {
        put("c", o.color); put("o", o.opacity.toDouble()); put("z", o.size.toDouble())
        when (o) {
            is VStroke -> { put("t", "s"); put("p", JSONArray().also { a -> o.pts.forEach { a.put(it.x.toDouble()); a.put(it.y.toDouble()) } }) }
            is VShape -> { put("t", if (o.kind == 0) "r" else "e"); put("f", o.filled)
                put("b", JSONArray(listOf(o.r.left, o.r.top, o.r.right, o.r.bottom).map { it.toDouble() })) }
        }
    }

    private fun vObj(j: JSONObject): VObj {
        val c = j.getInt("c"); val op = j.getDouble("o").toFloat(); val z = j.getDouble("z").toFloat()
        return if (j.getString("t") == "s") {
            val a = j.getJSONArray("p")
            VStroke((0 until a.length() / 2).map { PointF(a.getDouble(it * 2).toFloat(), a.getDouble(it * 2 + 1).toFloat()) }, c, op, z)
        } else {
            val b = j.getJSONArray("b")
            VShape(if (j.getString("t") == "r") 0 else 1,
                RectF(b.getDouble(0).toFloat(), b.getDouble(1).toFloat(), b.getDouble(2).toFloat(), b.getDouble(3).toFloat()),
                j.getBoolean("f"), c, op, z)
        }
    }

    fun save(s: Sprite, out: OutputStream) {
        val meta = JSONObject().apply {
            put("name", s.name); put("w", s.w); put("h", s.h); put("kind", s.kind.name); put("fps", s.fps)
            put("layers", JSONArray().also { a -> s.layers.forEach { a.put(JSONObject().put("n", it.name).put("v", it.visible).put("o", it.opacity.toDouble())) } })
            put("frames", JSONArray().also { a -> s.frames.forEach { f ->
                a.put(JSONObject().put("ms", f.durationMs).put("vec", JSONArray().also { va ->
                    f.vecs.forEach { l -> va.put(JSONArray().also { la -> l.forEach { la.put(vJson(it)) } }) } }))
            } })
        }
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("meta.json")); z.write(meta.toString().toByteArray()); z.closeEntry()
            for (f in s.frames.indices) for (l in s.layers.indices) {
                z.putNextEntry(ZipEntry("c_${f}_$l.png"))
                val bo = ByteArrayOutputStream(); s.frames[f].cels[l].compress(Bitmap.CompressFormat.PNG, 100, bo)
                z.write(bo.toByteArray()); z.closeEntry()
            }
        }
    }

    fun load(inp: InputStream): Sprite {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(inp).use { z -> while (true) { val e = z.nextEntry ?: break; files[e.name] = z.readBytes() } }
        val m = JSONObject(String(files["meta.json"] ?: error("Not a Pleiades project")))
        val s = Sprite(m.getString("name"), m.getInt("w"), m.getInt("h"), Kind.valueOf(m.getString("kind")))
        s.fps = m.optInt("fps", 12)
        s.layers.clear()
        val la = m.getJSONArray("layers")
        for (i in 0 until la.length()) la.getJSONObject(i).let { s.layers.add(Layer(it.getString("n"), it.getBoolean("v"), it.getDouble("o").toFloat())) }
        s.frames.clear()
        val fa = m.getJSONArray("frames")
        for (f in 0 until fa.length()) {
            val fj = fa.getJSONObject(f)
            val cels = ArrayList<Bitmap>(); val vecs = ArrayList<MutableList<VObj>>()
            val vj = fj.optJSONArray("vec")
            for (l in s.layers.indices) {
                val bytes = files["c_${f}_$l.png"]
                val bmp = Bitmap.createBitmap(s.w, s.h, Bitmap.Config.ARGB_8888)
                if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { android.graphics.Canvas(bmp).drawBitmap(it, 0f, 0f, null) }
                cels.add(bmp)
                val list = ArrayList<VObj>()
                vj?.optJSONArray(l)?.let { a -> for (k in 0 until a.length()) list.add(vObj(a.getJSONObject(k))) }
                vecs.add(list)
            }
            s.frames.add(Frame(cels, vecs, fj.optInt("ms", 100)))
        }
        return s
    }

    /** Import a flat image as a single-frame raster sprite (pixel sprite if tiny). */
    fun importImage(name: String, inp: InputStream): Sprite {
        val src = BitmapFactory.decodeStream(inp) ?: error("Unsupported image")
        val s = Sprite(name, src.width, src.height, if (src.width <= 256 && src.height <= 256) Kind.PIXEL else Kind.RASTER)
        android.graphics.Canvas(s.cel()).drawBitmap(src, 0f, 0f, null)
        return s
    }
}
