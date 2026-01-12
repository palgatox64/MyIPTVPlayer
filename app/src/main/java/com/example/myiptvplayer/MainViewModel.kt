package com.example.myiptvplayer

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myiptvplayer.data.Channel
import com.example.myiptvplayer.data.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels = _channels.asStateFlow()

    private val _selectedChannel = MutableStateFlow<Channel?>(null)
    val selectedChannel = _selectedChannel.asStateFlow()

    private val _isConfigured = MutableStateFlow<Boolean?>(null)
    val isConfigured = _isConfigured.asStateFlow()

    init {
        loadSavedPlaylist()
    }

    private fun loadSavedPlaylist() {
        viewModelScope.launch {
            val savedData = Prefs.getPlaylist(context)
            if (savedData != null) {
                val (type, value) = savedData
                val loadedList = if (type == "url") {
                    PlaylistRepository.loadFromUrl(value)
                } else {
                    PlaylistRepository.loadFromFile(context, Uri.parse(value))
                }

                if (loadedList.isNotEmpty()) {
                    _channels.value = loadedList

                    val lastId = Prefs.getLastChannel(context)
                    val channelToPlay = loadedList.find { it.id == lastId } ?: loadedList.first()

                    _selectedChannel.value = channelToPlay
                    _isConfigured.value = true
                } else {
                    _isConfigured.value = false
                }
            } else {
                _isConfigured.value = false
            }
        }
    }

    fun setPlaylistFromUrl(url: String) {
        viewModelScope.launch {
            val list = PlaylistRepository.loadFromUrl(url)
            if (list.isNotEmpty()) {
                Prefs.savePlaylist(context, "url", url)
                _channels.value = list
                _selectedChannel.value = list.first()
                _isConfigured.value = true
            }
        }
    }

    fun setPlaylistFromFile(uri: Uri) {
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
                Prefs.savePlaylist(context, "file", uri.toString())
                _channels.value = list
                _selectedChannel.value = list.first()
                _isConfigured.value = true
            }
        }
    }

    fun playChannel(channel: Channel) {
        _selectedChannel.value = channel
        Prefs.saveLastChannel(context, channel.id)
    }

    fun resetConfiguration() {
        viewModelScope.launch {
            Prefs.savePlaylist(context, "", "")
            Prefs.saveLastChannel(context, "")

            _channels.value = emptyList()
            _selectedChannel.value = null

            _isConfigured.value = false
        }
    }
}