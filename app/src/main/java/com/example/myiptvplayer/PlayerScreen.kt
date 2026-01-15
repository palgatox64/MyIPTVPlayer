package com.example.myiptvplayer

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.myiptvplayer.data.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    channels: List<Channel>,
    currentChannel: Channel?,
    onChannelSelected: (Channel) -> Unit,
    onResetPlaylist: () -> Unit,
    playlists: List<com.example.myiptvplayer.data.Playlist>,
    selectedPlaylist: com.example.myiptvplayer.data.Playlist?,
    onPlaylistSelected: (com.example.myiptvplayer.data.Playlist) -> Unit,
    onDeletePlaylist: (com.example.myiptvplayer.data.Playlist) -> Unit,
    onAddPlaylist: () -> Unit,
    onEditPlaylist: (com.example.myiptvplayer.data.Playlist) -> Unit, // NUEVO CALLBACK
    groups: List<String>,
    selectedGroup: String,
    onGroupSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isMenuVisible by remember { mutableStateOf(true) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }

    // Estados para el control de volumen
    var currentVolume by remember { mutableFloatStateOf(1f) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    // Trigger para reiniciar el timer incluso si el volumen no cambia
    var volumeTrigger by remember { mutableLongStateOf(0L) }

    // Ocultar indicador de volumen automáticamente
    LaunchedEffect(volumeTrigger) {
        if (showVolumeIndicator) {
            delay(2000)
            showVolumeIndicator = false
        }
    }

    val videoFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    BackHandler(enabled = isMenuVisible) {
        if (isSettingsOpen) {
            isSettingsOpen = false
        } else {
            isMenuVisible = false
            videoFocusRequester.requestFocus()
        }
    }

    val exoPlayer = remember {
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 2_500, 5_000)
            .build()

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(true)
                    .setTunnelingEnabled(true)
            )
        }

        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_BUFFERING) isBuffering = true
                        else if (playbackState == Player.STATE_READY) isBuffering = false
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        prepare()
                    }
                })
            }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    LaunchedEffect(currentChannel) {
        if (currentChannel != null) {
            try {
                // CARGAR VOLUMEN ESPECÍFICO DEL CANAL
                val savedVolume = Prefs.getChannelVolume(context, currentChannel.id)
                currentVolume = savedVolume
                exoPlayer.volume = savedVolume

                // INICIAR REPRODUCCIÓN
                isBuffering = true
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                if (currentChannel.streamUrl.isNotBlank()) {
                    val mediaItem = MediaItem.fromUri(currentChannel.streamUrl)
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                }
            } catch (e: Exception) {
                android.util.Log.e("MI_IPTV", "Error loading video", e)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(videoFocusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            // MOSTRAR MENÚ
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                isMenuVisible = true
                                isSettingsOpen = false
                                return@onKeyEvent true
                            }

                            // SUBIR VOLUMEN
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_VOLUME_UP -> {
                                val currentInt = (currentVolume * 10).roundToInt()
                                val newVol = ((currentInt + 1).coerceAtMost(10) / 10f)

                                currentVolume = newVol
                                exoPlayer.volume = newVol
                                showVolumeIndicator = true
                                volumeTrigger = System.currentTimeMillis()

                                if (currentChannel != null) {
                                    Prefs.saveChannelVolume(context, currentChannel.id, newVol)
                                }
                                return@onKeyEvent true
                            }

                            // BAJAR VOLUMEN
                            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                                val currentInt = (currentVolume * 10).roundToInt()
                                val newVol = ((currentInt - 1).coerceAtLeast(0) / 10f)

                                currentVolume = newVol
                                exoPlayer.volume = newVol
                                showVolumeIndicator = true
                                volumeTrigger = System.currentTimeMillis()

                                if (currentChannel != null) {
                                    Prefs.saveChannelVolume(context, currentChannel.id, newVol)
                                }
                                return@onKeyEvent true
                            }
                        }
                    }
                    false
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        keepScreenOn = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(50.dp),
                    color = Color(0xFF00BFA5),
                    strokeWidth = 4.dp
                )
            }
        }

        // INDICADOR VISUAL DE VOLUMEN
        AnimatedVisibility(
            visible = showVolumeIndicator,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(30.dp),
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it }
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Volumen: ${(currentVolume * 100).roundToInt()}%",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = androidx.tv.material3.MaterialTheme.typography.titleMedium
                )
            }
        }

        AnimatedVisibility(
            visible = isMenuVisible,
            enter = slideInHorizontally(),
            exit = slideOutHorizontally()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(420.dp)
                    .background(Brush.horizontalGradient(colors = listOf(Color.Black.copy(alpha = 0.98f), Color.Transparent)))
                    .padding(20.dp)
            ) {
                Crossfade(targetState = isSettingsOpen, label = "MenuSwitch") { showSettings ->

                    if (showSettings) {
                        SettingsView(
                            onBack = { isSettingsOpen = false },
                            onReset = onResetPlaylist,
                            playlists = playlists,
                            selectedPlaylist = selectedPlaylist,
                            onPlaylistSelected = onPlaylistSelected,
                            onDeletePlaylist = onDeletePlaylist,
                            onAddPlaylist = onAddPlaylist,
                            onEditPlaylist = onEditPlaylist
                        )
                    } else {
                        ChannelsView(
                            channels = channels,
                            currentChannel = currentChannel,
                            listState = listState,
                            onChannelSelected = { channel ->
                                onChannelSelected(channel)
                                isMenuVisible = false
                                videoFocusRequester.requestFocus()
                            },
                            onOpenSettings = { isSettingsOpen = true },
                            groups = groups,
                            selectedGroup = selectedGroup,
                            onGroupSelected = onGroupSelected,
                            onCloseMenu = {
                                isMenuVisible = false
                                videoFocusRequester.requestFocus()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelsView(
    channels: List<Channel>,
    currentChannel: Channel?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onChannelSelected: (Channel) -> Unit,
    onOpenSettings: () -> Unit,
    groups: List<String>,
    selectedGroup: String,
    onGroupSelected: (String) -> Unit,
    onCloseMenu: () -> Unit
) {
    val headerFocus = remember { FocusRequester() }

    // ESTADO CLAVE: Controla que el foco automático SOLO ocurra al abrir el menú
    val needsInitialFocus = remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (currentChannel != null) {
            val index = channels.indexOf(currentChannel)
            if (index >= 0) listState.scrollToItem(index)
        } else {
            headerFocus.requestFocus()
        }
    }

    Column {
        var isSettingsFocused by remember { mutableStateOf(false) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .focusRequester(headerFocus)
                .onFocusChanged { isSettingsFocused = it.isFocused }
                .focusable()
                .clickable { onOpenSettings() }
                .background(
                    color = if (isSettingsFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            val contentColor = if (isSettingsFocused) Color.White else Color.LightGray
            Icon(Icons.Default.Settings, contentDescription = null, tint = contentColor)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Configuración", color = contentColor, fontWeight = FontWeight.Bold)
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(groups) { group ->
                val isSelected = group == selectedGroup
                var isFocused by remember { mutableStateOf(false) }

                Text(
                    text = group,
                    color = if (isSelected || isFocused) Color.White else Color.Gray,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .clickable { onGroupSelected(group) }
                        .background(
                            if (isSelected) Color(0xFF00BFA5).copy(alpha = 0.8f)
                            else if (isFocused) Color.White.copy(alpha = 0.2f)
                            else Color.Transparent,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                        keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        onCloseMenu()
                        return@onPreviewKeyEvent true
                    }
                    false
                }
        ) {
            items(items = channels, key = { it.id }) { channel ->
                val isSelected = channel == currentChannel
                var isFocused by remember { mutableStateOf(false) }
                val itemFocus = remember { FocusRequester() }

                if (isSelected && needsInitialFocus.value) {
                    LaunchedEffect(Unit) {
                        delay(50)
                        itemFocus.requestFocus()
                        needsInitialFocus.value = false
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .focusRequester(itemFocus)
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .clickable { onChannelSelected(channel) }
                        .background(
                            color = when {
                                isSelected -> Color(0xFF00BFA5).copy(alpha = 0.9f)
                                isFocused -> Color.White.copy(alpha = 0.2f)
                                else -> Color.Transparent
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = null,
                        modifier = Modifier.size(45.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = channel.name,
                        color = if (isSelected || isFocused) Color.White else Color.Gray,
                        maxLines = 1,
                        style = androidx.tv.material3.MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsView(
    onBack: () -> Unit,
    onReset: () -> Unit,
    playlists: List<com.example.myiptvplayer.data.Playlist>,
    selectedPlaylist: com.example.myiptvplayer.data.Playlist?,
    onPlaylistSelected: (com.example.myiptvplayer.data.Playlist) -> Unit,
    onDeletePlaylist: (com.example.myiptvplayer.data.Playlist) -> Unit,
    onAddPlaylist: () -> Unit,
    onEditPlaylist: (com.example.myiptvplayer.data.Playlist) -> Unit // Recibimos el callback
) {
    val backButtonFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        backButtonFocus.requestFocus()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("Listas de Reproducción", style = androidx.tv.material3.MaterialTheme.typography.headlineMedium, color = Color.White)

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.focusRequester(backButtonFocus).fillMaxWidth(),
            colors = ButtonDefaults.colors(containerColor = Color.DarkGray, contentColor = Color.White)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Volver a Canales")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onAddPlaylist,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.colors(containerColor = Color(0xFF00BFA5), contentColor = Color.White)
        ) {
            Text("+ Agregar Lista")
        }

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(playlists, key = { it.id }) { playlist ->
                val isSelected = playlist == selectedPlaylist

                var isItemFocused by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. ZONA DE TEXTO (Seleccionar)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { isItemFocused = it.isFocused }
                            .clickable { onPlaylistSelected(playlist) }
                            .focusable()
                            .background(
                                color = when {
                                    isSelected -> Color(0xFF00BFA5).copy(alpha = 0.7f)
                                    isItemFocused -> Color.White.copy(alpha = 0.2f)
                                    else -> Color.Transparent
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = playlist.name,
                                color = Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = if (playlist.sourceType == "url") "URL" else "Archivo",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 2. BOTÓN DE EDITAR (Amarillo)
                    Button(
                        onClick = { onEditPlaylist(playlist) },
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFFFFD600), // Amarillo
                            contentColor = Color.Black,
                            focusedContainerColor = Color(0xFFFFEA00),
                            focusedContentColor = Color.Black
                        ),
                        shape = ButtonDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(20.dp))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 3. BOTÓN DE BORRAR (Rojo)
                    Button(
                        onClick = { onDeletePlaylist(playlist) },
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFFB00020), // Rojo
                            contentColor = Color.White,
                            focusedContainerColor = Color(0xFFFF1744),
                            focusedContentColor = Color.White
                        ),
                        shape = ButtonDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Eliminar", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.colors(containerColor = Color(0xFFB00020), contentColor = Color.White)
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Eliminar Todas las Listas")
        }
    }
}