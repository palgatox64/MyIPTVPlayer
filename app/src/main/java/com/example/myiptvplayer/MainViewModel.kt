package com.example.myiptvplayer

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myiptvplayer.data.Channel
import com.example.myiptvplayer.data.Playlist
import com.example.myiptvplayer.data.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist = _selectedPlaylist.asStateFlow()

    private val _allChannels = MutableStateFlow<List<Channel>>(emptyList())

    // Inicializamos vacío
    private val _selectedGroup = MutableStateFlow("")
    val selectedGroup = _selectedGroup.asStateFlow()

    private val _groups = MutableStateFlow<List<String>>(emptyList())
    val groups = _groups.asStateFlow()

    // Lógica de filtrado: Ahora será puramente por Playlist
    val channels = combine(_allChannels, _selectedGroup, _playlists) { all, group, playlists ->
        val isPlaylistName = playlists.any { it.name == group }

        when {
            group.isEmpty() -> emptyList()
            // Si el nombre coincide con una Playlist, mostramos todo el contenido de esa lista
            isPlaylistName -> all.filter { it.playlistName == group }
            // Mantenemos la lógica de grupo por seguridad, aunque ya no la mostremos en la barra
            else -> all.filter { it.group == group }
        }
    }

    private val _selectedChannel = MutableStateFlow<Channel?>(null)
    val selectedChannel = _selectedChannel.asStateFlow()

    private val _isConfigured = MutableStateFlow<Boolean?>(null)
    val isConfigured = _isConfigured.asStateFlow()

    init {
        loadSavedPlaylists()
    }

    private fun loadSavedPlaylists() {
        viewModelScope.launch {
            val savedPlaylists = Prefs.getPlaylists(context)
            _playlists.value = savedPlaylists

            if (savedPlaylists.isNotEmpty()) {
                _isConfigured.value = true
                loadAllChannels(savedPlaylists)
            } else {
                _isConfigured.value = false
            }
        }
    }

    private suspend fun loadAllChannels(playlists: List<Playlist>) {
        val totalChannels = mutableListOf<Channel>()

        playlists.forEach { playlist ->
            val loaded = try {
                if (playlist.sourceType == "url") {
                    PlaylistRepository.loadFromUrl(playlist.sourceValue)
                } else {
                    PlaylistRepository.loadFromFile(context, Uri.parse(playlist.sourceValue))
                }
            } catch (e: Exception) {
                emptyList()
            }
            val taggedChannels = loaded.map { it.copy(playlistName = playlist.name) }
            totalChannels.addAll(taggedChannels)
        }

        if (totalChannels.isNotEmpty()) {
            _allChannels.value = totalChannels

            // CAMBIO CLAVE: Solo usamos los nombres de las Playlists como categorías.
            // Ignoramos 'group-title' internos para no ensuciar la barra.
            val playlistNames = playlists.map { it.name }.sorted()

            _groups.value = playlistNames

            // Seleccionar la primera lista por defecto si la actual no es válida
            if (_selectedGroup.value !in playlistNames && playlistNames.isNotEmpty()) {
                _selectedGroup.value = playlistNames.first()
            }

            val lastId = Prefs.getLastChannel(context)
            val foundLast = totalChannels.find { it.id == lastId }
            if (foundLast != null) {
                _selectedChannel.value = foundLast
            } else if (_selectedChannel.value == null) {
                _selectedChannel.value = totalChannels.first()
            }
        } else {
            _allChannels.value = emptyList()
        }
    }

    fun selectGroup(group: String) {
        _selectedGroup.value = group
    }

    fun addPlaylistFromUrl(name: String, url: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val list = PlaylistRepository.loadFromUrl(url)
            if (list.isNotEmpty()) {
                val newPlaylist = Playlist(
                    name = name,
                    sourceType = "url",
                    sourceValue = url,
                    order = _playlists.value.size
                )
                saveAndReloadAll(newPlaylist)
                onSuccess()
            }
        }
    }

    fun addPlaylistFromFile(name: String, uri: Uri, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.e("MI_IPTV", "No se pudo persistir el permiso URI", e)
            }

            val list = PlaylistRepository.loadFromFile(context, uri)
            if (list.isNotEmpty()) {
                val newPlaylist = Playlist(
                    name = name,
                    sourceType = "file",
                    sourceValue = uri.toString(),
                    order = _playlists.value.size
                )
                saveAndReloadAll(newPlaylist)
                onSuccess()
            }
        }
    }

    private suspend fun saveAndReloadAll(newPlaylist: Playlist) {
        val updatedPlaylists = _playlists.value + newPlaylist
        _playlists.value = updatedPlaylists
        Prefs.savePlaylists(context, updatedPlaylists)
        _isConfigured.value = true
        loadAllChannels(updatedPlaylists)
    }

    fun selectPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            _selectedGroup.value = playlist.name
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val updatedPlaylists = _playlists.value.filter { it.id != playlist.id }
                .mapIndexed { index, pl -> pl.copy(order = index) }

            _playlists.value = updatedPlaylists
            Prefs.savePlaylists(context, updatedPlaylists)

            if (updatedPlaylists.isEmpty()) {
                _allChannels.value = emptyList()
                _selectedChannel.value = null
                _isConfigured.value = false
            } else {
                loadAllChannels(updatedPlaylists)
            }
        }
    }

    fun playChannel(channel: Channel) {
        _selectedChannel.value = channel
        Prefs.saveLastChannel(context, channel.id)
    }

    fun resetConfiguration() {
        viewModelScope.launch {
            Prefs.clearAll(context)
            _playlists.value = emptyList()
            _allChannels.value = emptyList()
            _selectedChannel.value = null
            _selectedPlaylist.value = null
            _isConfigured.value = false
        }
    }
}