package com.example.myiptvplayer

import android.content.Context

object Prefs {
    private const val PREFS_NAME = "iptv_prefs"
    private const val KEY_SOURCE_TYPE = "source_type"
    private const val KEY_SOURCE_VALUE = "source_value"
    private const val KEY_LAST_CHANNEL = "last_channel_id"

    fun savePlaylist(context: Context, type: String, value: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_SOURCE_TYPE, type)
            .putString(KEY_SOURCE_VALUE, value)
            .apply()
    }

    fun getPlaylist(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val type = prefs.getString(KEY_SOURCE_TYPE, null)
        val value = prefs.getString(KEY_SOURCE_VALUE, null)
        return if (type != null && value != null) type to value else null
    }

    fun saveLastChannel(context: Context, channelId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_CHANNEL, channelId).apply()
    }

    fun getLastChannel(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CHANNEL, null)
    }
}