package com.example.myiptvplayer.data

import android.content.Context
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PlaylistRepository {

    // Función auxiliar para ignorar certificados SSL (PELIGROSO: Solo para uso necesario)
    private fun getUnsafeUrlConnection(urlStr: String): HttpURLConnection {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection

        if (conn is HttpsURLConnection) {
            val trustAllCerts =
                    arrayOf<TrustManager>(
                            object : X509TrustManager {
                                override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                                override fun checkClientTrusted(
                                        certs: Array<X509Certificate>,
                                        authType: String
                                ) {}
                                override fun checkServerTrusted(
                                        certs: Array<X509Certificate>,
                                        authType: String
                                ) {}
                            }
                    )

            try {
                val sc = SSLContext.getInstance("SSL")
                sc.init(null, trustAllCerts, java.security.SecureRandom())
                conn.sslSocketFactory = sc.socketFactory
                conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return conn
    }

    // Opción A: Cargar desde Internet (Modificado para ignorar SSL incorrecto)
    suspend fun loadFromUrl(url: String): List<Channel> {
        return withContext(Dispatchers.IO) {
            try {
                val connection = getUnsafeUrlConnection(url)
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.connect()

                connection.inputStream.use { stream -> M3uParser.parse(stream) }
            } catch (e: Exception) {
                // Si falla, lanzamos la excepción para que el ViewModel la maneje
                throw e
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
