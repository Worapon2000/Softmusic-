package com.softmusic.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "soft_music")

/** Persists the playlist as an ordered JSON array of {uri, name}. */
class PlaylistStore(private val context: Context) {
    private val keyList = stringPreferencesKey("playlist_json")
    private val keyCurrent = stringPreferencesKey("current_uri")

    suspend fun save(tracks: List<Track>, currentUri: String?) {
        val arr = JSONArray()
        tracks.forEach { arr.put(JSONObject().put("uri", it.uri).put("name", it.name)) }
        context.dataStore.edit { p ->
            p[keyList] = arr.toString()
            if (currentUri != null) p[keyCurrent] = currentUri else p.remove(keyCurrent)
        }
    }

    suspend fun load(): Pair<List<Track>, String?> {
        val p = context.dataStore.data.first()
        val json = p[keyList] ?: return emptyList<Track>() to null
        val list = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Track(o.getString("uri"), o.optString("name", ""))
            }
        } catch (e: Exception) {
            emptyList()
        }
        return list to p[keyCurrent]
    }
}
