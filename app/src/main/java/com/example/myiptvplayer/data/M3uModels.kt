package com.example.myiptvplayer.data

// Modelo de un Canal
data class Channel(
    val id: String,          // tvg-id (ej: "24horas.cl")
    val name: String,        // Nombre para mostrar (ej: "24 Horas")
    val logoUrl: String?,    // tvg-logo
    val group: String?,      // group-title (ej: "Nacional")
    val streamUrl: String,   // El enlace largo http...
    val playlistId: Int = 0,  // Lo usaremos después para la base de datos
    val playlistName: String = "" // NUEVO CAMPO: Para saber de qué lista vino
)
