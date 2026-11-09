package com.pleiades.app.core

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Minimal animated GIF89a writer: 255 colours + transparency, looping. */
object Gif {
    fun encode(frames: List<Bitmap>, delaysMs: List<Int>): ByteArray {
        val w = frames[0].width; val h = frames[0].height
        val all = frames.map { f -> IntArray(w * h).also { f.getPixels(it, 0, w, 0, 0, w, h) } }
        val exact = LinkedHashMap<Int, Int>()
        var overflow = false
        outer@ for (px in all) for (v in px) if ((v ushr 24) >= 128) {
            val k = v and 0xFFFFFF
            if (!exact.containsKey(k)) { exact[k] = exact.size + 1; if (exact.size > 255) { overflow = true; break@outer } }
        }
        val pal = IntArray(256)
        if (!overflow) exact.forEach { (rgb, i) -> pal[i] = rgb }
        else for (r in 0..5) for (g in 0..5) for (b in 0..5) pal[1 + r * 36 + g * 6 + b] = ((r * 51) shl 16) or ((g * 51) shl 8) or (b * 51)
        fun index(v: Int): Int {
            if ((v ushr 24) < 128) return 0
            if (!overflow) return exact[v and 0xFFFFFF]!!
            val r = (((v shr 16) and 255) / 51f).roundToInt(); val g = (((v shr 8) and 255) / 51f).roundToInt(); val b = ((v and 255) / 51f).roundToInt()
            return 1 + r * 36 + g * 6 + b
        }
        val o = ByteArrayOutputStream()
        fun u16(v: Int) { o.write(v and 255); o.write((v shr 8) and 255) }
        o.write("GIF89a".toByteArray())
        u16(w); u16(h); o.write(0xF7); o.write(0); o.write(0)
        for (c in pal) { o.write((c shr 16) and 255); o.write((c shr 8) and 255); o.write(c and 255) }
        o.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B)); o.write("NETSCAPE2.0".toByteArray())
        o.write(byteArrayOf(3, 1, 0, 0, 0))
        for ((n, px) in all.withIndex()) {
            o.write(0x21); o.write(0xF9); o.write(4); o.write(0x09)
            u16((delaysMs[n] / 10).coerceAtLeast(2)); o.write(0); o.write(0)
            o.write(0x2C); u16(0); u16(0); u16(w); u16(h); o.write(0)
            o.write(8)
            val data = lzw(IntArray(px.size) { index(px[it]) })
            var p = 0
            while (p < data.size) { val n2 = minOf(255, data.size - p); o.write(n2); o.write(data, p, n2); p += n2 }
            o.write(0)
        }
        o.write(0x3B)
        return o.toByteArray()
    }

    private fun lzw(idx: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        var cur = 0; var curBits = 0
        var nBits = 9; var maxCode = 511; var freeEnt = 258
        val dict = HashMap<Int, Int>()
        fun write(code: Int) {
            cur = cur or (code shl curBits); curBits += nBits
            while (curBits >= 8) { out.write(cur and 255); cur = cur ushr 8; curBits -= 8 }
        }
        fun emit(code: Int) {
            write(code)
            if (freeEnt > maxCode) { nBits++; maxCode = if (nBits >= 12) 4096 else (1 shl nBits) - 1 }
        }
        write(256)
        var prefix = idx[0]
        for (i in 1 until idx.size) {
            val c = idx[i]; val key = (prefix shl 8) or c
            val v = dict[key]
            if (v != null) { prefix = v; continue }
            emit(prefix)
            if (freeEnt < 4096) dict[key] = freeEnt++
            else { write(256); dict.clear(); freeEnt = 258; nBits = 9; maxCode = 511 }
            prefix = c
        }
        emit(prefix); emit(257)
        if (curBits > 0) out.write(cur and 255)
        return out.toByteArray()
    }
}
