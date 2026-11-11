package com.pleiades.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleiades.app.core.Kind
import com.pleiades.app.core.Sprite
import com.pleiades.app.core.Tool
import kotlinx.coroutines.delay

private val toolIcons: List<Pair<Tool, ImageVector>> = listOf(
    Tool.PEN to Icons.Filled.Edit,
    Tool.ERASER to Icons.Filled.RemoveCircleOutline,
    Tool.PICKER to Icons.Filled.Colorize,
    Tool.FILL to Icons.Filled.FormatColorFill,
    Tool.RECT to Icons.Filled.CropSquare,
    Tool.ELLIPSE to Icons.Filled.RadioButtonUnchecked,
)

@Composable
fun ToolBar(app: AppState, sp: Sprite) {
    Column(
        Modifier.width(60.dp).fillMaxHeight().background(P.panel).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        toolIcons.forEach { (t, ic) -> IconBtn(ic, t.label, { app.tool = t }, selected = app.tool == t, size = 48.dp) }
        IconBtn(Icons.Filled.CenterFocusStrong, "Centre", { sp.centreLayer() }, size = 48.dp)
        Box(Modifier.width(36.dp).height(1.dp).background(P.line))
        IconBtn(Icons.Filled.Undo, "Undo", { sp.undo() }, enabled = sp.canUndo, size = 48.dp)
        IconBtn(Icons.Filled.Redo, "Redo", { sp.redo() }, enabled = sp.canRedo, size = 48.dp)
        IconBtn(Icons.Filled.ZoomOutMap, "Fit", { sp.zoom = 0f }, size = 48.dp)
        if (sp.kind == Kind.PIXEL) IconBtn(Icons.Filled.GridOn, "Grid", { app.grid = !app.grid }, selected = app.grid, size = 48.dp)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TopBar(app: AppState, onSave: () -> Unit, onSaveAs: () -> Unit, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(P.panel).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            app.tabs.forEachIndexed { i, s ->
                val sel = i == app.active
                Row(
                    Modifier.height(40.dp).padding(start = 6.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) P.bg else Color.Transparent)
                        .then(if (sel) Modifier.border(1.dp, P.accent, RoundedCornerShape(10.dp)) else Modifier)
                        .combinedClickable(onClick = { app.active = i; app.playing = false }, onLongClick = { app.dlg = Dlg.Rename(i) })
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.name + if (s.dirty) " •" else "", color = if (sel) P.text else P.dim, fontSize = 14.sp, maxLines = 1)
                    IconBtn(Icons.Filled.Close, "Close", { if (s.dirty) app.dlg = Dlg.Close(i) else app.close(i) }, size = 36.dp, tint = P.dim)
                }
            }
            IconBtn(Icons.Filled.Add, "New sprite", { app.dlg = Dlg.New })
        }
        IconBtn(Icons.Filled.FolderOpen, "Open", onOpen)
        IconBtn(Icons.Filled.Save, "Save project", onSave, enabled = app.sprite != null)
        IconBtn(Icons.Filled.FileCopy, "Save project as", onSaveAs, enabled = app.sprite != null)
        IconBtn(Icons.Filled.SaveAlt, "Export", { app.dlg = Dlg.Export }, enabled = app.sprite != null)
    }
}

@Composable
fun Timeline(app: AppState, sp: Sprite) {
    val rev = sp.rev
    LaunchedEffect(app.playing, sp, sp.curAnim) {
        if (!app.playing) return@LaunchedEffect
        val start = sp.cur
        try {
            while (true) {
                delay((1000L / sp.fps.coerceAtLeast(1)).coerceAtLeast(16))
                sp.cur = (sp.cur + 1) % sp.frames.size
            }
        } finally { if (!app.playing) sp.cur = start.coerceAtMost(sp.frames.size - 1) }
    }
    Column(Modifier.fillMaxWidth().background(P.panel).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                sp.anims.forEachIndexed { i, a ->
                    val sel = i == sp.curAnim
                    Chip(a.name, sel, { if (!app.playing) sp.selectAnim(i) })
                }
            }
            IconBtn(Icons.Filled.Add, "New animation", { sp.addAnim(false) }, enabled = !app.playing, size = 36.dp)
            IconBtn(Icons.Filled.ContentCopy, "Duplicate animation", { sp.addAnim(true) }, enabled = !app.playing, size = 36.dp)
            IconBtn(Icons.Filled.Edit, "Rename animation", { app.dlg = Dlg.RenameAnim }, enabled = !app.playing, size = 36.dp)
            IconBtn(Icons.Filled.Delete, "Delete animation", { sp.deleteAnim() }, enabled = !app.playing && sp.anims.size > 1, size = 36.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
            IconBtn(if (app.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", { app.playing = !app.playing }, selected = app.playing)
            IconBtn(Icons.Filled.Add, "New frame", { sp.addFrame(false) }, enabled = !app.playing)
            IconBtn(Icons.Filled.ContentCopy, "Duplicate frame", { sp.addFrame(true) }, enabled = !app.playing)
            IconBtn(Icons.Filled.Delete, "Delete frame", { sp.deleteFrame() }, enabled = !app.playing && sp.frames.size > 1)
            IconBtn(Icons.Filled.ChevronLeft, "Move left", { sp.moveFrame(-1) }, enabled = !app.playing && sp.cur > 0)
            IconBtn(Icons.Filled.ChevronRight, "Move right", { sp.moveFrame(1) }, enabled = !app.playing && sp.cur < sp.frames.size - 1)
            Spacer8()
            Chip("Onion skin", app.onion, { app.onion = !app.onion })
            if (app.onion) {
                Spacer8()
                Stepper("Before", app.onionBefore, 0, 4) { app.onionBefore = it }
                Spacer8()
                Stepper("After", app.onionAfter, 0, 4) { app.onionAfter = it }
            }
            Spacer8()
            Stepper("FPS", sp.fps, 1, 60) { sp.fps = it }
            Spacer8()
            Stepper("ms", sp.frames[sp.cur].durationMs, 20, 2000, 10) { sp.frames[sp.cur].durationMs = it; sp.touch() }
        }
        LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(sp.frames) { i, _ ->
                val thumb = remember(rev, i, sp.frames.size, sp.curAnim) { sp.composite(i).asImageBitmap() }
                val sel = i == sp.cur
                Column(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) P.accent.copy(alpha = 0.2f) else P.panel2)
                        .then(if (sel) Modifier.border(1.dp, P.accent, RoundedCornerShape(8.dp)) else Modifier)
                        .clickable { if (!app.playing) sp.cur = i }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(thumb, null, Modifier.size(56.dp).background(Color(0xFF2C2C3A)), contentScale = ContentScale.Fit,
                        filterQuality = if (sp.kind == Kind.PIXEL) FilterQuality.None else FilterQuality.Medium)
                    Text("${i + 1}", color = if (sel) P.accent else P.dim, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable private fun Spacer8() = Box(Modifier.width(10.dp))

@Composable
fun Stepper(label: String, value: Int, min: Int, max: Int, step: Int = 1, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = P.dim, fontSize = 12.sp)
        IconBtn(Icons.Filled.ChevronLeft, "Less", { onChange((value - step).coerceAtLeast(min)) }, size = 34.dp)
        Text("$value", color = P.text, fontSize = 13.sp)
        IconBtn(Icons.Filled.ChevronRight, "More", { onChange((value + step).coerceAtMost(max)) }, size = 34.dp)
    }
}
