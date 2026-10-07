package com.softmusic.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.softmusic.app.PlayerController
import com.softmusic.app.PlaylistStore
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val Bg = Color(0xFFF8F7F3)
private val Dark = Color(0xFF2E2E2E)
private val Grey = Color(0xFFA8A7A2)
private val Soft = Color(0xFFE8E5DB)
private val Line = Color(0xFFE7E4DA)

@Composable
fun SoftMusicScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pc = remember {
        val app = context.applicationContext
        PlayerController(app, PlaylistStore(app), scope)
    }
    DisposableEffect(Unit) {
        pc.connect()
        onDispose { pc.release() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            pc.refreshPosition()
            delay(250)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        pc.addUris(it)
    }

    MaterialTheme(colorScheme = lightColorScheme(background = Bg, surface = Bg, primary = Dark)) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp)
            ) {
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Vinyl(pc.isPlaying, Modifier.fillMaxWidth(0.62f).aspectRatio(1f))
                }
                Spacer(Modifier.height(16.dp))
                SeekSection(pc)
                Spacer(Modifier.height(4.dp))
                Controls(pc)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Line)
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 112.dp)
                ) {
                    itemsIndexed(pc.tracks) { i, t ->
                        TrackRow(
                            name = t.name,
                            current = i == pc.currentIndex,
                            canUp = i > 0,
                            canDown = i < pc.tracks.size - 1,
                            onPlay = { pc.playAt(i) },
                            onUp = { pc.moveUp(i) },
                            onDown = { pc.moveDown(i) },
                            onRemove = { pc.remove(i) }
                        )
                        HorizontalDivider(color = Line)
                    }
                }
            }

            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(24.dp)
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Dark)
                    .clickable { picker.launch(arrayOf("audio/*", "video/mp4", "application/ogg")) },
                contentAlignment = Alignment.Center
            ) { Text("♫", color = Color.White, fontSize = 26.sp) }
        }
    }
}

@Composable
private fun SeekSection(pc: PlayerController) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val dur = pc.duration
    val shown = if (dragging) dragValue else min(pc.position, dur).coerceAtLeast(0L).toFloat()

    Slider(
        value = shown,
        onValueChange = { dragging = true; dragValue = it },
        onValueChangeFinished = { pc.seekTo(dragValue.toLong()); dragging = false },
        valueRange = 0f..max(dur, 1L).toFloat(),
        enabled = dur > 0,
        colors = SliderDefaults.colors(
            thumbColor = Dark,
            activeTrackColor = Dark,
            inactiveTrackColor = Color(0xFFE2DFD4),
            disabledThumbColor = Color(0xFFD5D2C7),
            disabledActiveTrackColor = Color(0xFFD5D2C7),
            disabledInactiveTrackColor = Color(0xFFE2DFD4)
        )
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(shown.toLong()), color = Grey, fontSize = 12.sp)
        Text(formatTime(dur), color = Grey, fontSize = 12.sp)
    }
}

@Composable
private fun Controls(pc: PlayerController) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundButton(active = pc.shuffle, onClick = pc::toggleShuffle) {
            Text("⇄", fontSize = 22.sp, color = if (pc.shuffle) Dark else Grey)
        }
        RoundButton(onClick = pc::previous) { SkipIcon(flip = true) }
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(Dark).clickable { pc.togglePlay() },
            contentAlignment = Alignment.Center
        ) {
            Text(if (pc.isPlaying) "Ⅱ" else "▶", color = Color.White, fontSize = 26.sp)
        }
        RoundButton(onClick = pc::next) { SkipIcon(flip = false) }
        val repeatOn = pc.repeatMode != Player.REPEAT_MODE_OFF
        RoundButton(active = repeatOn, onClick = pc::cycleRepeat) {
            Text(
                if (pc.repeatMode == Player.REPEAT_MODE_ONE) "↻¹" else "↻",
                fontSize = 22.sp,
                color = if (repeatOn) Dark else Grey
            )
        }
    }
}

@Composable
private fun RoundButton(
    active: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .background(if (active) Soft else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun SkipIcon(flip: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        fun x(f: Float) = if (flip) w - f * w else f * w
        val tri = Path().apply {
            moveTo(x(0.08f), h * 0.15f)
            lineTo(x(0.68f), h * 0.5f)
            lineTo(x(0.08f), h * 0.85f)
            close()
        }
        drawPath(tri, Dark)
        val l = min(x(0.78f), x(0.92f))
        drawRect(Dark, Offset(l, h * 0.15f), Size(w * 0.14f, h * 0.7f))
    }
}

@Composable
private fun TrackRow(
    name: String,
    current: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onPlay: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).clickable(onClick = onPlay),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.padding(end = 12.dp).size(7.dp).clip(CircleShape)
                .background(if (current) Dark else Color.Transparent)
        )
        Text(
            name,
            Modifier.weight(1f),
            color = if (current) Dark else Color(0xFF6E6D68),
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TextAction("↑", canUp, onUp)
        TextAction("↓", canDown, onDown)
        TextAction("×", true, onRemove)
    }
}

@Composable
private fun TextAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 20.sp, color = if (enabled) Dark else Color(0xFFD5D2C7)) }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%02d:%02d", m, sec)
}
