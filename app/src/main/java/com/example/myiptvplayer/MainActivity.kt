package com.example.myiptvplayer

import coil.Coil
import coil.ImageLoader
import okhttp3.OkHttpClient
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.example.myiptvplayer.ui.theme.MyIPTVPlayerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; MyIPTVPlayer) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            }
            .build()
        Coil.setImageLoader(imageLoader)

        setContent {
            MyIPTVPlayerTheme {
                Surface(modifier = Modifier.fillMaxSize(), shape = RectangleShape) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()

    val channels by viewModel.channels.collectAsState()
    val currentChannel by viewModel.selectedChannel.collectAsState()
    val isConfigured by viewModel.isConfigured.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()

    LaunchedEffect(isConfigured) {
        if (isConfigured == true) {
            navController.navigate("player") {
                popUpTo("config") { inclusive = true }
            }
        } else if (isConfigured == false) {
            if (navController.currentDestination?.route == "player") {
                navController.navigate("config") {
                    popUpTo("player") { inclusive = true }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = "config") {

        composable("config") {
            if (isConfigured == false) {
                ConfigScreen(
                    onUrlSelected = { name, url -> viewModel.addPlaylistFromUrl(name, url) },
                    onFileSelected = { name, uri -> viewModel.addPlaylistFromFile(name, uri) }
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.tv.material3.Text("Cargando perfil...", color = Color.White)
                }
            }
        }

        composable("player") {
            PlayerScreen(
                channels = channels,
                currentChannel = currentChannel,
                onChannelSelected = { viewModel.playChannel(it) },
                onResetPlaylist = { viewModel.resetConfiguration() },
                playlists = playlists,
                selectedPlaylist = selectedPlaylist,
                onPlaylistSelected = { viewModel.selectPlaylist(it) },
                onDeletePlaylist = { viewModel.deletePlaylist(it) },
                onAddPlaylist = {
                    navController.navigate("config")
                }
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
fun ConfigScreen(
    onUrlSelected: (String, String) -> Unit,
    onFileSelected: (String, Uri) -> Unit
) {
    val scope = rememberCoroutineScope()
    var nameInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && nameInput.isNotEmpty()) {
            isLoading = true
            onFileSelected(nameInput, uri)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(40.dp)) {
        androidx.tv.material3.Text("Agregar Lista IPTV", style = androidx.tv.material3.MaterialTheme.typography.headlineLarge)
        androidx.tv.material3.Text("Dale un nombre a tu lista", style = androidx.tv.material3.MaterialTheme.typography.bodyMedium, color = Color.Gray)

        Spacer(modifier = Modifier.height(30.dp))

        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text("Nombre de la lista (ej: Deportes)") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = Color.White),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("https://...") },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Cyan,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Button(onClick = {
                if (urlInput.isNotEmpty() && nameInput.isNotEmpty()) {
                    isLoading = true
                    onUrlSelected(nameInput, urlInput)
                }
            }) { Text("Cargar URL") }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (nameInput.isNotEmpty()) {
                    filePickerLauncher.launch(arrayOf("*/*"))
                }
            }
        ) {
            Row {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cargar Archivo Local")
            }
        }

        if (isLoading) androidx.tv.material3.Text("Procesando...", color = Color.Yellow, modifier = Modifier.padding(top=20.dp))
    }
}