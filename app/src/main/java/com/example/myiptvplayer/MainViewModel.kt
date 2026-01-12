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
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist = _selectedPlaylist.asStateFlow()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels = _channels.asStateFlow()

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
                val selectedId = Prefs.getSelectedPlaylistId(context)
                val playlistToLoad = savedPlaylists.find { it.id.toString() == selectedId } ?: savedPlaylists.first()
                loadPlaylist(playlistToLoad)
                _isConfigured.value = true
            } else {
                _isConfigured.value = false
            }
        }
    }

    private suspend fun loadPlaylist(playlist: Playlist) {
        val loadedChannels = if (playlist.sourceType == "url") {
            PlaylistRepository.loadFromUrl(playlist.sourceValue)
        } else {
            PlaylistRepository.loadFromFile(context, Uri.parse(playlist.sourceValue))
        }

        if (loadedChannels.isNotEmpty()) {
            _selectedPlaylist.value = playlist
            _channels.value = loadedChannels
            Prefs.saveSelectedPlaylist(context, playlist.id.toString())

            val lastId = Prefs.getLastChannel(context)
            _selectedChannel.value = loadedChannels.find { it.id == lastId } ?: loadedChannels.first()
        }
    }

    fun addPlaylistFromUrl(name: String, url: String) {
        viewModelScope.launch {
            val list = PlaylistRepository.loadFromUrl(url)
            if (list.isNotEmpty()) {
                val newPlaylist = Playlist(
                    name = name,
                    sourceType = "url",
                    sourceValue = url,
                    order = _playlists.value.size
                )
                val updatedPlaylists = _playlists.value + newPlaylist
                _playlists.value = updatedPlaylists
                Prefs.savePlaylists(context, updatedPlaylists)
                loadPlaylist(newPlaylist)
                _isConfigured.value = true
            }
        }
    }

    fun addPlaylistFromFile(name: String, uri: Uri) {
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
                val updatedPlaylists = _playlists.value + newPlaylist
                _playlists.value = updatedPlaylists
                Prefs.savePlaylists(context, updatedPlaylists)
                loadPlaylist(newPlaylist)
                _isConfigured.value = true
            }
        }
    }

    fun selectPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            loadPlaylist(playlist)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val updatedPlaylists = _playlists.value.filter { it.id != playlist.id }
                .mapIndexed { index, pl -> pl.copy(order = index) }
            
            _playlists.value = updatedPlaylists
            Prefs.savePlaylists(context, updatedPlaylists)

            if (updatedPlaylists.isEmpty()) {
                _channels.value = emptyList()
                _selectedChannel.value = null
                _selectedPlaylist.value = null
                _isConfigured.value = false
            } else if (_selectedPlaylist.value?.id == playlist.id) {
                loadPlaylist(updatedPlaylists.first())
            }
        }
    }

    fun reorderPlaylists(from: Int, to: Int) {
        val updated = _playlists.value.toMutableList()
        val item = updated.removeAt(from)
        updated.add(to, item)
        val reordered = updated.mapIndexed { index, playlist -> playlist.copy(order = index) }
        _playlists.value = reordered
        Prefs.savePlaylists(context, reordered)
    }

    fun playChannel(channel: Channel) {
        _selectedChannel.value = channel
        Prefs.saveLastChannel(context, channel.id)
    }

    fun resetConfiguration() {
        viewModelScope.launch {
            Prefs.clearAll(context)
            _playlists.value = emptyList()
            _channels.value = emptyList()
            _selectedChannel.value = null
            _selectedPlaylist.value = null
            _isConfigured.value = false
        }
    }
}