package com.example.myiptvplayer.data

import java.io.InputStream

object M3uParser {

    // Expresiones regulares para encontrar los datos específicos en la línea #EXTINF
    private val regexId = """tvg-id="(.*?)"""".toRegex()
    private val regexLogo = """tvg-logo="(.*?)"""".toRegex()
    private val regexGroup = """group-title="(.*?)"""".toRegex()

    // Función principal que convierte el archivo en una lista de canales
    fun parse(inputStream: InputStream): List<Channel> {
        val channels = mutableListOf<Channel>()
        val reader = inputStream.bufferedReader()

        // Variables temporales para guardar datos mientras leemos línea por línea
        var currentName: String? = null
        var currentLogo: String? = null
        var currentGroup: String? = null
        var currentId: String? = null

        // Set para evitar IDs duplicados que rompen LazyColumn
        val seenIds = HashSet<String>()

        reader.forEachLine { line ->
            val trimmed = line.trim()

            if (trimmed.startsWith("#EXTINF:")) {
                // 1. Extraemos el Nombre (lo que está después de la última coma)
                currentName = trimmed.substringAfterLast(",").trim()

                // 2. Extraemos los atributos usando Regex
                // find(trimmed) busca el patrón en la línea.
                // groupValues[1] es lo que está dentro de las comillas.
                currentId = regexId.find(trimmed)?.groupValues?.get(1) ?: ""
                currentLogo = regexLogo.find(trimmed)?.groupValues?.get(1)
                currentGroup = regexGroup.find(trimmed)?.groupValues?.get(1) ?: "General"
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                // 3. Si la línea no es comentario y no está vacía, ES LA URL
                if (currentName != null) {
                    // Lógica para garantizar ID único
                    var finalId = currentId ?: "channel_${channels.size}"

                    if (seenIds.contains(finalId)) {
                        var counter = 1
                        while (seenIds.contains("${finalId}_$counter")) {
                            counter++
                        }
                        finalId = "${finalId}_$counter"
                    }
                    seenIds.add(finalId)

                    channels.add(
                            Channel(
                                    id = finalId,
                                    name = currentName!!,
                                    logoUrl = currentLogo,
                                    group = currentGroup,
                                    streamUrl = trimmed, // Aquí va tu URL larga de mdstrm.com
                                    playlistId = 0
                            )
                    )
                }
                // Limpiamos variables para el siguiente canal
                currentName = null
                currentLogo = null
                currentId = null
                currentGroup = null
            }
        }
        return channels
    }
}
