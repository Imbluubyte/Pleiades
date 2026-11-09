package com.pleiades.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleiades.app.core.Kind
import com.pleiades.app.core.Sprite
import com.pleiades.app.core.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SidePanel(app: AppState, sp: Sprite) {
    Column(
        Modifier.width(272.dp).fillMaxHeight().background(P.panel).verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Colour", app.panel == 0, { app.panel = 0 }, Modifier.weight(1f))
            Chip("Layers", app.panel == 1, { app.panel = 1 }, Modifier.weight(1f))
        }
        if (app.panel == 0) ColourTab(app, sp) else LayersTab(sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColourTab(app: AppState, sp: Sprite) {
    ColorWheel(app.color, { app.color = it })
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(app.color.toC()))
        Text(app.color.hex(), color = P.text, fontSize = 15.sp)
    }
    val pixel = sp.kind == Kind.PIXEL
    val usesSize = app.tool in listOf(Tool.PEN, Tool.ERASER, Tool.RECT, Tool.ELLIPSE)
    if (usesSize) {
        LabeledSlider("Brush size", "${app.size.roundToInt()} px", app.size,
            1f..(if (pixel) 16f else 100f), { app.size = if (pixel) it.roundToInt().toFloat() else it },
            steps = if (pixel) 14 else 0)
    }
    LabeledSlider("Opacity", "${(app.opacity * 100).roundToInt()}%", app.opacity, 0.05f..1f, { app.opacity = it })
    if (app.tool == Tool.RECT || app.tool == Tool.ELLIPSE) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Outline", !app.filled, { app.filled = false }, Modifier.weight(1f))
            Chip("Filled", app.filled, { app.filled = true }, Modifier.weight(1f))
        }
    }
    if (app.tool == Tool.FILL && sp.kind != Kind.VECTOR) {
        LabeledSlider("Fill tolerance", "${app.tolerance}", app.tolerance.toFloat(), 0f..128f, { app.tolerance = it.roundToInt() })
    }
    if (app.tool == Tool.FILL && sp.kind == Kind.VECTOR) {
        Text("Tap a rectangle or ellipse to fill it", color = P.dim, fontSize = 12.sp)
    }

    var pal by remember(sp) { mutableStateOf(emptyList<Int>()) }
    LaunchedEffect(sp, sp.rev) { delay(350); pal = withContext(Dispatchers.Default) { sp.palette() } }
    Text("Palette", color = P.dim, fontSize = 12.sp)
    if (pal.isEmpty()) Text("Colours you use appear here", color = P.dim, fontSize = 12.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pal.forEach { c -> Swatch(c, c == app.color || (c and 0xFFFFFF) == (app.color and 0xFFFFFF)) { app.color = c or (0xFF shl 24) } }
    }
}

@Composable
private fun LayersTab(sp: Sprite) {
    val rev = sp.rev
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in sp.layers.indices.reversed()) {
            val l = sp.layers[i]
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (i == sp.curLayer) P.accent.copy(alpha = 0.2f) else P.panel2)
                    .clickable { sp.curLayer = i }.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(if (l.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, "Toggle", { l.visible = !l.visible; sp.touch() }, size = 38.dp)
                Text(l.name, color = P.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        IconBtn(Icons.Filled.Add, "Add layer", { sp.addLayer() })
        IconBtn(Icons.Filled.Delete, "Delete layer", { sp.deleteLayer() }, enabled = sp.layers.size > 1)
        IconBtn(Icons.Filled.KeyboardArrowUp, "Move up", { sp.moveLayer(1) }, enabled = sp.curLayer < sp.layers.size - 1)
        IconBtn(Icons.Filled.KeyboardArrowDown, "Move down", { sp.moveLayer(-1) }, enabled = sp.curLayer > 0)
    }
    val cl = sp.layers[sp.curLayer]
    LabeledSlider("Layer opacity", "${(cl.opacity * 100).roundToInt()}%", cl.opacity, 0f..1f, { cl.opacity = it; sp.touch() })
    if (rev < 0) Text("")
}
