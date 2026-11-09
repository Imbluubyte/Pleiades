package com.pleiades.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream

enum class Fmt(val label: String, val ext: String, val mime: String) {
    PNG("PNG", "png", "image/png"), JPEG("JPEG", "jpg", "image/jpeg"), WEBP("WebP", "webp", "image/webp"),
}

enum class Mode(val label: String) {
    CURRENT("Current frame"), FRAMES("All frames (separate files)"), SHEET("Sprite sheet"), GIF("Animated GIF"), SVG("SVG (vector)"),
}

class ExportOpts(
    val mode: Mode, val fmt: Fmt, val scale: Int, val cols: Int, val padding: Int,
    val bg: Int, val withJson: Boolean,
)

object Export {
    fun scaled(b: Bitmap, k: Int, smooth: Boolean): Bitmap =
        if (k <= 1) b else Bitmap.createScaledBitmap(b, b.width * k, b.height * k, smooth)

    fun encode(b: Bitmap, fmt: Fmt, bg: Int): ByteArray {
        var src = b
        if (fmt == Fmt.JPEG) {
            src = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
            Canvas(src).apply { drawColor(if (bg == 0) 0xFFFFFFFF.toInt() else bg); drawBitmap(b, 0f, 0f, null) }
        }
        val o = ByteArrayOutputStream()
        val cf = when (fmt) {
            Fmt.PNG -> Bitmap.CompressFormat.PNG
            Fmt.JPEG -> Bitmap.CompressFormat.JPEG
            Fmt.WEBP -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        }
        src.compress(cf, 100, o)
        return o.toByteArray()
    }

    fun sheet(s: Sprite, o: ExportOpts): Pair<Bitmap, String> {
        val n = s.frames.size
        val cols = o.cols.coerceIn(1, n)
        val rows = (n + cols - 1) / cols
        val fw = s.w * o.scale; val fh = s.h * o.scale; val pad = o.padding
        val out = Bitmap.createBitmap(cols * fw + (cols + 1) * pad, rows * fh + (rows + 1) * pad, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (o.bg != 0) c.drawColor(o.bg)
        val p = Paint().apply { isFilterBitmap = s.kind != Kind.PIXEL }
        val json = StringBuilder("{\"image\":\"${s.name}.${o.fmt.ext}\",\"frameWidth\":$fw,\"frameHeight\":$fh,\"frames\":[")
        for (i in 0 until n) {
            val x = pad + (i % cols) * (fw + pad); val y = pad + (i / cols) * (fh + pad)
            c.drawBitmap(s.composite(i), null, Rect(x, y, x + fw, y + fh), p)
            json.append("{\"x\":$x,\"y\":$y,\"w\":$fw,\"h\":$fh,\"duration\":${s.frames[i].durationMs}}")
            if (i < n - 1) json.append(',')
        }
        return out to json.append("]}").toString()
    }

    fun gif(s: Sprite, o: ExportOpts): ByteArray {
        val fr = s.frames.indices.map { scaled(s.composite(it, o.bg), o.scale, s.kind != Kind.PIXEL) }
        return Gif.encode(fr, s.frames.map { it.durationMs })
    }

    /** Writes [bytes] as [name] inside [dir], replacing any existing file of that name. */
    fun write(ctx: Context, dir: DocumentFile, name: String, mime: String, bytes: ByteArray) {
        dir.findFile(name)?.delete()
        val f = dir.createFile(mime, name.substringBeforeLast('.')) ?: error("Cannot create $name")
        ctx.contentResolver.openOutputStream(f.uri, "wt")!!.use { it.write(bytes) }
    }

    /** Runs an export; returns a human-readable summary. */
    fun run(ctx: Context, s: Sprite, dirUri: Uri, o: ExportOpts): String {
        val dir = DocumentFile.fromTreeUri(ctx, dirUri) ?: error("Folder unavailable")
        val base = s.name
        val smooth = s.kind != Kind.PIXEL
        when (o.mode) {
            Mode.CURRENT -> write(ctx, dir, "$base.${o.fmt.ext}", o.fmt.mime,
                encode(scaled(s.composite(s.cur, if (o.fmt == Fmt.JPEG) o.bg else o.bg), o.scale, smooth), o.fmt, o.bg))
            Mode.FRAMES -> for (i in s.frames.indices) write(ctx, dir, "${base}_%03d.${o.fmt.ext}".format(i + 1), o.fmt.mime,
                encode(scaled(s.composite(i, o.bg), o.scale, smooth), o.fmt, o.bg))
            Mode.SHEET -> {
                val (b, json) = sheet(s, o)
                write(ctx, dir, "$base.${o.fmt.ext}", o.fmt.mime, encode(b, o.fmt, o.bg))
                if (o.withJson) write(ctx, dir, "$base.json", "application/json", json.toByteArray())
            }
            Mode.GIF -> write(ctx, dir, "$base.gif", "image/gif", gif(s, o))
            Mode.SVG -> if (s.kind == Kind.VECTOR) {
                if (s.frames.size == 1) write(ctx, dir, "$base.svg", "image/svg+xml", Vec.svg(s, 0).toByteArray())
                else for (i in s.frames.indices) write(ctx, dir, "${base}_%03d.svg".format(i + 1), "image/svg+xml", Vec.svg(s, i).toByteArray())
            } else error("SVG is only available for vector sprites")
        }
        return "Exported ${o.mode.label.lowercase()} to ${dir.name ?: "folder"}"
    }
}
