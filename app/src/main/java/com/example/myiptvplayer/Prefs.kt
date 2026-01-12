package com.example.myiptvplayer

import android.content.Context
import com.example.myiptvplayer.data.Playlist
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val PREFS_NAME = "iptv_prefs"
    private const val KEY_PLAYLISTS = "playlists"
    private const val KEY_SELECTED_PLAYLIST = "selected_playlist_id"
    private const val KEY_LAST_CHANNEL = "last_channel_id"

    fun savePlaylists(context: Context, playlists: List<Playlist>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        playlists.forEach { playlist ->
            val jsonObject = JSONObject().apply {
                put("id", playlist.id)
                put("name", playlist.name)
                put("sourceType", playlist.sourceType)
                put("sourceValue", playlist.sourceValue)
                put("order", playlist.order)
                put("createdAt", playlist.createdAt)
            }
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString(KEY_PLAYLISTS, jsonArray.toString()).apply()
    }

    fun getPlaylists(context: Context): List<Playlist> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        
        val playlists = mutableListOf<Playlist>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                playlists.add(
                    Playlist(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        sourceType = obj.getString("sourceType"),
                        sourceValue = obj.getString("sourceValue"),
                        order = obj.optInt("order", 0),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("MI_IPTV", "Error parsing playlists", e)
        }
        return playlists.sortedBy { it.order }
    }

    fun saveSelectedPlaylist(context: Context, playlistId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SELECTED_PLAYLIST, playlistId).apply()
    }

    fun getSelectedPlaylistId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_PLAYLIST, null)
    }

    fun saveLastChannel(context: Context, channelId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_CHANNEL, channelId).apply()
    }

    fun getLastChannel(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CHANNEL, null)
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}