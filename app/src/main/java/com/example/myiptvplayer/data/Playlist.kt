package com.example.myiptvplayer.data

import java.util.UUID

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sourceType: String, // "url" o "file"
    val sourceValue: String,
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)