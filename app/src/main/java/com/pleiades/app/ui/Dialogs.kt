package com.pleiades.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleiades.app.core.ExportOpts
import com.pleiades.app.core.Fmt
import com.pleiades.app.core.Kind
import com.pleiades.app.core.Mode
import com.pleiades.app.core.Sprite

fun cleanName(n: String) = n.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "sprite" }

@Composable
private fun Lbl(t: String) = Text(t, color = P.dim, fontSize = 12.sp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewDialog(onDismiss: () -> Unit, onCreate: (Sprite) -> Unit) {
    var name by remember { mutableStateOf("sprite") }
    var kind by remember { mutableStateOf(Kind.PIXEL) }
    var w by remember { mutableStateOf("32") }
    var h by remember { mutableStateOf("32") }
    val wi = w.toIntOrNull(); val hi = h.toIntOrNull()
    val ok = wi != null && hi != null && wi in 1..2048 && hi in 1..2048
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New sprite") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Lbl("Type")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kind.values().forEach { k -> Chip(k.label, kind == k, { kind = k; if (k != Kind.PIXEL && (wi ?: 0) < 128) { w = "512"; h = "512" } }) }
                }
                Lbl("Size")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(16, 32, 64, 128, 256, 512, 1024).forEach { n -> Chip("$n", w == "$n" && h == "$n", { w = "$n"; h = "$n" }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(w, { w = it.filter(Char::isDigit).take(4) }, label = { Text("Width") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(h, { h = it.filter(Char::isDigit).take(4) }, label = { Text("Height") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                if (!ok) Text("Size must be 1–2048", color = P.danger, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton({ onCreate(Sprite(cleanName(name), wi!!, hi!!, kind)) }, enabled = ok) { Text("Create") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportDialog(sp: Sprite, hasLast: Boolean, onDismiss: () -> Unit, onExport: (ExportOpts, Boolean) -> Unit) {
    var mode by remember { mutableStateOf(if (sp.frames.size > 1) Mode.GIF else Mode.CURRENT) }
    var fmt by remember { mutableStateOf(Fmt.PNG) }
    var scale by remember { mutableStateOf(if (sp.kind == Kind.PIXEL && sp.w <= 128) 4 else 1) }
    var cols by remember { mutableStateOf(sp.frames.size.coerceAtMost(8)) }
    var pad by remember { mutableStateOf(0) }
    var bg by remember { mutableStateOf(0) }
    var json by remember { mutableStateOf(true) }
    val modes = Mode.values().filter { it != Mode.SVG || sp.kind == Kind.VECTOR }
    fun opts() = ExportOpts(mode, fmt, scale, cols, pad, bg, json)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export “${sp.name}”") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Lbl("What")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEach { m -> Chip(m.label, mode == m, { mode = m }) }
                }
                if (mode == Mode.CURRENT || mode == Mode.FRAMES || mode == Mode.SHEET) {
                    Lbl("Format")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Fmt.values().forEach { f -> Chip(f.label, fmt == f, { fmt = f }) } }
                }
                if (mode != Mode.SVG) {
                    Lbl("Scale")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(1, 2, 4, 8, 16).forEach { n -> Chip("${n}x", scale == n, { scale = n }) } }
                    Lbl("Background")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Transparent", bg == 0, { bg = 0 })
                        Chip("White", bg == 0xFFFFFFFF.toInt(), { bg = 0xFFFFFFFF.toInt() })
                        Chip("Black", bg == 0xFF000000.toInt(), { bg = 0xFF000000.toInt() })
                    }
                }
                if (mode == Mode.SHEET) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stepper("Columns", cols, 1, 64) { cols = it }
                        Stepper("Padding", pad, 0, 32) { pad = it }
                    }
                    Chip("Also write ${sp.name}.json frame data", json, { json = !json })
                }
                Text("Files are written to a folder you choose, anywhere on the device.", color = P.dim, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton({ onExport(opts(), false) }) { Text("Choose folder…") } },
        dismissButton = {
            Row {
                if (hasLast) TextButton({ onExport(opts(), true) }) { Text("Last folder") }
                TextButton(onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun CloseDialog(name: String, onDismiss: () -> Unit, onDiscard: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Close “$name”?") },
    text = { Text("It has changes that haven't been saved to a project file.") },
    confirmButton = { TextButton(onDiscard) { Text("Discard", color = P.danger) } },
    dismissButton = { TextButton(onDismiss) { Text("Keep open") } },
)

@Composable
fun RenameDialog(cur: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var n by remember { mutableStateOf(cur) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Rename") },
        text = { OutlinedTextField(n, { n = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton({ onRename(cleanName(n)) }) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
