package com.softmusic.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bridges the Media3 service (single source of truth) to Compose state. */
class PlayerController(
    private val context: Context,
    private val store: PlaylistStore,
    private val scope: CoroutineScope
) : Player.Listener {

    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(-1)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var position by mutableLongStateOf(0L)
        private set
    var duration by mutableLongStateOf(0L)
        private set

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var ready = false
    private var released = false

    fun connect() {
        released = false
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            if (released) return@addListener
            val c = try { f.get() } catch (e: Exception) { return@addListener }
            controller = c
            c.addListener(this)
            scope.launch {
                restoreIfEmpty(c)
                ready = true
                sync(c)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        released = true
        controller?.removeListener(this)
        future?.let { MediaController.releaseFuture(it) }
        controller = null
        future = null
        ready = false
    }

    private suspend fun restoreIfEmpty(c: MediaController) {
        if (c.mediaItemCount > 0) return
        val (saved, cur) = store.load()
        if (saved.isEmpty()) return
        val valid = withContext(Dispatchers.IO) { saved.filter { isReadable(it.uri) } }
        if (valid.size < saved.size) {
            Toast.makeText(context, "ข้ามไฟล์ที่หายไป ${saved.size - valid.size} ไฟล์", Toast.LENGTH_SHORT).show()
        }
        if (valid.isEmpty()) {
            store.save(emptyList(), null)
            return
        }
        val idx = valid.indexOfFirst { it.uri == cur }.coerceAtLeast(0)
        c.setMediaItems(valid.map { it.toMediaItem() }, idx, 0L)
        c.prepare()
        store.save(valid, valid[idx].uri)
    }

    private fun isReadable(uri: String): Boolean = try {
        context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
    } catch (e: Exception) {
        false
    }

    // ---- Player.Listener ----

    override fun onEvents(player: Player, events: Player.Events) {
        sync(player)
        if (ready && events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION
            )
        ) persist(player)
    }

    override fun onPlayerError(error: PlaybackException) {
        // File missing / unreadable: drop it from the queue and carry on.
        val c = controller ?: return
        val i = c.currentMediaItemIndex
        if (i in 0 until c.mediaItemCount) {
            c.removeMediaItem(i)
            Toast.makeText(context, "เล่นไฟล์นี้ไม่ได้ ข้ามไปแล้ว", Toast.LENGTH_SHORT).show()
            if (c.mediaItemCount > 0) {
                c.prepare()
                c.play()
            }
        }
    }

    private fun sync(p: Player) {
        val n = p.mediaItemCount
        tracks = (0 until n).map {
            val m = p.getMediaItemAt(it)
            Track(m.mediaId, m.mediaMetadata.title?.toString() ?: "")
        }
        currentIndex = if (n == 0) -1 else p.currentMediaItemIndex
        isPlaying = p.isPlaying
        shuffle = p.shuffleModeEnabled
        repeatMode = p.repeatMode
        refreshPosition()
    }

    private fun persist(p: Player) {
        val list = (0 until p.mediaItemCount).map {
            val m = p.getMediaItemAt(it)
            Track(m.mediaId, m.mediaMetadata.title?.toString() ?: "")
        }
        val cur = if (list.isEmpty()) null else list.getOrNull(p.currentMediaItemIndex)?.uri
        scope.launch { store.save(list, cur) }
    }

    fun refreshPosition() {
        val c = controller ?: return
        position = c.currentPosition.coerceAtLeast(0L)
        duration = if (c.duration == C.TIME_UNSET) 0L else c.duration.coerceAtLeast(0L)
    }

    // ---- Commands ----

    fun addUris(uris: List<Uri>) {
        val c = controller ?: return
        if (uris.isEmpty()) return
        scope.launch {
            val newTracks = withContext(Dispatchers.IO) {
                uris.distinct().map { u ->
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            u, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                    }
                    Track(u.toString(), displayName(u))
                }
            }
            val existing = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }.toSet()
            val fresh = newTracks.filter { it.uri !in existing }
            if (fresh.isEmpty()) return@launch
            val wasEmpty = c.mediaItemCount == 0
            c.addMediaItems(fresh.map { it.toMediaItem() })
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (wasEmpty) c.play()
        }
    }

    private fun displayName(uri: Uri): String {
        try {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cur ->
                if (cur.moveToFirst()) {
                    val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) cur.getString(i)?.let { return it }
                }
            }
        } catch (e: Exception) {
        }
        return uri.lastPathSegment ?: "Unknown"
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun previous() { controller?.seekToPrevious() }
    fun next() { controller?.seekToNext() }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        position = ms
    }

    fun playAt(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekToDefaultPosition(index)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun remove(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun moveUp(index: Int) {
        val c = controller ?: return
        if (index > 0 && index < c.mediaItemCount) c.moveMediaItem(index, index - 1)
    }

    fun moveDown(index: Int) {
        val c = controller ?: return
        if (index >= 0 && index < c.mediaItemCount - 1) c.moveMediaItem(index, index + 1)
    }
}
