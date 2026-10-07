package com.softmusic.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun Vinyl(spinning: Boolean, modifier: Modifier = Modifier) {
    var angle by remember { mutableFloatStateOf(0f) }

    // Constant-speed rotation; keeps its angle when paused (no snap-back).
    LaunchedEffect(spinning) {
        if (spinning) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                angle = (angle + (now - last) / 1_000_000_000f * 60f) % 360f
                last = now
            }
        }
    }

    Canvas(modifier.graphicsLayer { rotationZ = angle }) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(Color(0xFF232323), r, c)
        listOf(0.95f, 0.89f, 0.83f, 0.77f, 0.71f, 0.65f, 0.59f, 0.53f).forEach {
            drawCircle(Color(0xFF333333), r * it, c, style = Stroke(1.dp.toPx()))
        }
        // soft highlights (also make the rotation visible)
        val hr = r * 0.78f
        listOf(200f, 20f).forEach { start ->
            drawArc(
                Color(0x26FFFFFF), start, 42f, false,
                topLeft = Offset(c.x - hr, c.y - hr),
                size = Size(hr * 2, hr * 2),
                style = Stroke(r * 0.14f)
            )
        }
        drawCircle(Color(0xFFE6E2D6), r * 0.34f, c)
        drawCircle(Color(0xFFCFCABB), r * 0.29f, c, style = Stroke(1.dp.toPx()))
        drawCircle(Color(0xFF8A877C), r * 0.03f, Offset(c.x, c.y - r * 0.2f))
        drawCircle(Color(0xFFF8F7F3), r * 0.04f, c)
    }
}
