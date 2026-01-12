package com.example.myiptvplayer.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object PlaylistRepository {

    // Opción A: Cargar desde Internet (Como ya teníamos)
    suspend fun loadFromUrl(url: String): List<Channel> {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = URL(url).openStream()
                M3uParser.parse(inputStream)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    // Opción B: Cargar desde Archivo Local (NUEVO)
    suspend fun loadFromFile(context: Context, uri: Uri): List<Channel> {
        return withContext(Dispatchers.IO) {
            try {
                // Usamos el ContentResolver de Android para abrir el archivo local
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    M3uParser.parse(inputStream)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}