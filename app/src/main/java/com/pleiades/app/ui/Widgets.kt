package com.pleiades.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IconBtn(
    icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, selected: Boolean = false, tint: Color = P.text, size: Dp = 44.dp,
) {
    Box(
        modifier.size(size).alpha(if (enabled) 1f else 0.35f).clip(RoundedCornerShape(10.dp))
            .background(if (selected) P.accent.copy(alpha = 0.22f) else Color.Transparent)
            .then(if (selected) Modifier.border(1.dp, P.accent, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (selected) P.accent else tint, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) P.accent.copy(alpha = 0.25f) else P.panel2)
            .then(if (selected) Modifier.border(1.dp, P.accent, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) P.accent else P.text, fontSize = 13.sp) }
}

@Composable
fun LabeledSlider(
    label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit, steps: Int = 0,
) {
    Column {
        Row(Modifier.padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = P.dim, fontSize = 12.sp)
            Text(valueText, color = P.text, fontSize = 12.sp)
        }
        Slider(value, onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = P.accent, activeTrackColor = P.accent, inactiveTrackColor = P.line))
    }
}

@Composable
fun Swatch(color: Int, selected: Boolean, onClick: () -> Unit, size: Dp = 30.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(Color(color))
            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else P.line, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    )
}
