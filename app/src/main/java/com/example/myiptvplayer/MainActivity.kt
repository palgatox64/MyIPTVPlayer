package com.example.myiptvplayer

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import okhttp3.OkHttpClient
import com.example.myiptvplayer.data.Playlist
import com.example.myiptvplayer.ui.theme.MyIPTVPlayerTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; MyIPTVPlayer) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                            .addHeader("Accept", "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                            .build()
                        chain.proceed(request)
                    }
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
        Coil.setImageLoader(imageLoader)

        setContent {
            MyIPTVPlayerTheme(isInDarkTheme = true) {
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

    val channels by viewModel.channels.collectAsState(initial = emptyList())
    val currentChannel by viewModel.selectedChannel.collectAsState()
    val isConfigured by viewModel.isConfigured.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()

    // Estado para saber si estamos editando una lista específica
    var playlistToEdit by remember { mutableStateOf<Playlist?>(null) }

    NavHost(navController = navController, startDestination = "loading") {

        composable("loading") {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            LaunchedEffect(isConfigured) {
                if (isConfigured == true) {
                    navController.navigate("player") { popUpTo("loading") { inclusive = true } }
                } else if (isConfigured == false) {
                    navController.navigate("config") { popUpTo("loading") { inclusive = true } }
                }
            }
        }

        composable("config") {
            ConfigScreen(
                playlistToEdit = playlistToEdit,
                onUrlSelected = { name, url, onError ->
                    if (playlistToEdit != null) {
                        viewModel.updatePlaylist(playlistToEdit!!, name, url, "url",
                            onSuccess = {
                                playlistToEdit = null
                                navController.navigate("player") { popUpTo("config") { inclusive = true } }
                            },
                            onError = onError
                        )
                    } else {
                        viewModel.addPlaylistFromUrl(name, url,
                            onSuccess = {
                                navController.navigate("player") { popUpTo("config") { inclusive = true } }
                            },
                            onError = onError
                        )
                    }
                },
                onFileSelected = { name, uri, onError ->
                    if (playlistToEdit != null) {
                        viewModel.updatePlaylist(playlistToEdit!!, name, uri.toString(), "file",
                            onSuccess = {
                                playlistToEdit = null
                                navController.navigate("player") { popUpTo("config") { inclusive = true } }
                            },
                            onError = onError
                        )
                    } else {
                        // Nota: addPlaylistFromFile aún no maneja errores explícitos igual que URL,
                        // pero mantenemos la estructura para consistencia si se actualiza después.
                        viewModel.addPlaylistFromFile(name, uri) {
                            navController.navigate("player") { popUpTo("config") { inclusive = true } }
                        }
                    }
                },
                onCancel = {
                    playlistToEdit = null
                    if (isConfigured == true) navController.navigate("player")
                }
            )
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
                    playlistToEdit = null
                    navController.navigate("config")
                },
                onEditPlaylist = { playlist ->
                    playlistToEdit = playlist
                    navController.navigate("config")
                },
                groups = groups,
                selectedGroup = selectedGroup,
                onGroupSelected = { viewModel.selectGroup(it) }
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
fun ConfigScreen(
    playlistToEdit: Playlist? = null,
    onUrlSelected: (String, String, (String) -> Unit) -> Unit,
    onFileSelected: (String, Uri, (String) -> Unit) -> Unit,
    onCancel: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Estados del formulario
    var nameInput by remember { mutableStateOf(playlistToEdit?.name ?: "") }
    var urlInput by remember { mutableStateOf(if (playlistToEdit?.sourceType == "url") playlistToEdit.sourceValue else "") }
    var isLoading by remember { mutableStateOf(false) }

    // ESTADOS DE UX
    var isNameEditing by remember { mutableStateOf(false) }
    var isUrlEditing by remember { mutableStateOf(false) }

    // Controladores de Foco
    val urlFocusRequester = remember { FocusRequester() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && nameInput.isNotEmpty()) {
            isLoading = true
            onFileSelected(nameInput, uri) { errorMsg ->
                isLoading = false
                android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val title = if (playlistToEdit != null) "Editar Lista" else "Agregar Lista IPTV"
    val buttonText = if (playlistToEdit != null) "Guardar Cambios" else "Cargar URL"

    Column(modifier = Modifier.fillMaxSize().padding(40.dp)) {
        androidx.tv.material3.Text(title, style = androidx.tv.material3.MaterialTheme.typography.headlineLarge)

        if (playlistToEdit == null) {
            androidx.tv.material3.Text("Dale un nombre a tu lista (ej: Deportes, Cine)", style = androidx.tv.material3.MaterialTheme.typography.bodyMedium, color = Color.Gray)
        } else {
            androidx.tv.material3.Text("Modificando: ${playlistToEdit.name}", style = androidx.tv.material3.MaterialTheme.typography.bodyMedium, color = Color.Yellow)
        }

        Spacer(modifier = Modifier.height(30.dp))

        // CAMPO NOMBRE DE LISTA
        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text("Nombre de la lista") },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    if (!it.isFocused) isNameEditing = false
                }
                .onKeyEvent { event ->
                    if (!isNameEditing && (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                            isNameEditing = true
                        }
                        return@onKeyEvent true
                    }
                    false
                },
            readOnly = !isNameEditing,
            singleLine = true,
            // CONFIGURACIÓN TECLADO: SIGUIENTE
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = {
                    isNameEditing = false
                    isUrlEditing = true // Activa edición para el siguiente campo automáticamente
                    urlFocusRequester.requestFocus()
                }
            ),
            textStyle = TextStyle(color = Color.White),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isNameEditing) Color.Cyan else Color.White,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            // CAMPO URL
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text(if (playlistToEdit?.sourceType == "file") "Archivo seleccionado (solo lectura)" else "https://...") },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(urlFocusRequester) // Asigna el FocusRequester
                    .onFocusChanged { if (!it.isFocused) isUrlEditing = false }
                    .onKeyEvent { event ->
                        if (!isUrlEditing && (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                isUrlEditing = true
                            }
                            return@onKeyEvent true
                        }
                        false
                    },
                readOnly = !isUrlEditing,
                singleLine = true,
                enabled = playlistToEdit?.sourceType != "file",
                // CONFIGURACIÓN TECLADO: DONE (Confirmar)
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (urlInput.isNotEmpty() && nameInput.isNotEmpty()) {
                            isLoading = true
                            onUrlSelected(nameInput, urlInput) { errorMsg ->
                                isLoading = false
                                android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ),
                textStyle = TextStyle(color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isUrlEditing) Color.Cyan else Color.White,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledTextColor = Color.Gray,
                    disabledBorderColor = Color.DarkGray
                )
            )
            Spacer(modifier = Modifier.width(10.dp))

            Button(onClick = {
                if (urlInput.isNotEmpty() && nameInput.isNotEmpty()) {
                    isLoading = true
                    onUrlSelected(nameInput, urlInput) { errorMsg ->
                        isLoading = false
                        android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }) { Text(buttonText) }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (nameInput.isNotEmpty()) {
                    try {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "No hay explorador de archivos instalado", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        ) {
            Row {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (playlistToEdit != null) "Reemplazar Archivo Local" else "Cargar Archivo Local")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (playlistToEdit != null) {
            Button(
                onClick = onCancel,
                colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = Color.Gray)
            ) {
                Text("Cancelar")
            }
        }

        if (isLoading) androidx.tv.material3.Text("Procesando...", color = Color.Yellow, modifier = Modifier.padding(top=20.dp))
    }
}