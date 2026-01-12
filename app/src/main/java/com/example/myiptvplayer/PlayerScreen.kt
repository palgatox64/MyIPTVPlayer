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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
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
import androidx.compose.ui.graphics.vector.ImageVector
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

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    channels: List<Channel>,
    currentChannel: Channel?,
    onChannelSelected: (Channel) -> Unit,
    onResetPlaylist: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var isMenuVisible by remember { mutableStateOf(true) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    var isBuffering by remember { mutableStateOf(true) }

    val videoFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val settingsButtonFocus = remember { FocusRequester() }
    val channelsListFocus = remember { FocusRequester() }

    BackHandler(enabled = isMenuVisible) {
        if (isSettingsOpen) {
            isSettingsOpen = false
        } else {
            isMenuVisible = false
            videoFocusRequester.requestFocus()
        }
    }

    val exoPlayer = remember {
        val trackSelector = DefaultTrackSelector(context)
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build().apply {
                playWhenReady = true
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_BUFFERING) isBuffering = true
                        else if (playbackState == Player.STATE_READY) isBuffering = false
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
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                isMenuVisible = true
                                isSettingsOpen = false
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
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                            isMenuVisible = false
                            videoFocusRequester.requestFocus()
                            return@onPreviewKeyEvent true
                        }
                        false
                    }
            ) {
                Crossfade(targetState = isSettingsOpen, label = "MenuSwitch") { showSettings ->

                    if (showSettings) {
                        SettingsView(
                            onBack = { isSettingsOpen = false },
                            onReset = onResetPlaylist
                        )
                    } else {
                        ChannelsView(
                            channels = channels,
                            currentChannel = currentChannel,
                            listState = listState,
                            onChannelSelected = onChannelSelected,
                            onOpenSettings = { isSettingsOpen = true }
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
    onOpenSettings: () -> Unit
) {
    val headerFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (currentChannel != null) {
            val index = channels.indexOf(currentChannel)
            if (index >= 0) listState.scrollToItem(index)
        } else {
            headerFocus.requestFocus()
        }
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .focusRequester(headerFocus)
                .clickable { onOpenSettings() }
                .focusable()
                .padding(8.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.LightGray)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Configuración", color = Color.LightGray, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(items = channels, key = { it.id }) { channel ->
                val isSelected = channel == currentChannel
                var isFocused by remember { mutableStateOf(false) }
                val itemFocus = remember { FocusRequester() }

                if (isSelected) {
                    LaunchedEffect(Unit) {
                        delay(50)
                        itemFocus.requestFocus()
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
    onReset: () -> Unit
) {
    val backButtonFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        backButtonFocus.requestFocus()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("Opciones", style = androidx.tv.material3.MaterialTheme.typography.headlineMedium, color = Color.White)

        Spacer(modifier = Modifier.height(30.dp))

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
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.colors(containerColor = Color(0xFFB00020), contentColor = Color.White)
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Eliminar Lista / Cambiar")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Al eliminar la lista, volverás a la pantalla de configuración inicial.",
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}