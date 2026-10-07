package com.example.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.music.model.Song
import com.example.music.settings.AppSettings
import com.example.music.ui.navigation.MusicNavHost
import com.example.music.ui.theme.MusicTheme
import com.example.music.ui.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    // Launcher de permiso a nivel de Activity (no depende de rememberPermissionState,
    // que no resuelve con este Compose BOM). API 33+ -> READ_MEDIA_AUDIO (granular).
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setPermissionGranted(granted) }

    // Launcher SAF para que el usuario elija una carpeta (p.ej. Download/).
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.importFolder(uri)
    }

    // Notificación multimedia (API 33+). Sin esto el servicio arranca pero no se ve nada.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* tanto si se concede como si no, la app sigue siendo utilizable */ }

    // READ_MEDIA_IMAGES (API 33+). La carátula de MediaStore vive en
    // content://media/external/audio/albumart y sin este permiso el ContentResolver la
    // rechaza: la portada sale en negro y el vinilo "girando" no se ve. Es independiente
    // de READ_MEDIA_AUDIO, así que se pide aparte y su denegación no bloquea la música.
    private val imagesPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* la música sigue funcionando sin carátula */ }

    private fun requestImagesPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val already = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        if (!already) imagesPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
    }

    private val requiredPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Sincronizar estado de permiso actual antes de renderizar
        val granted = ContextCompat.checkSelfPermission(this, requiredPermission) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.setPermissionGranted(granted)
        requestNotificationPermissionIfNeeded()
        requestImagesPermissionIfNeeded()
        setContent {
            MusicApp(
                viewModel = viewModel,
                onRescan = { viewModel.scanDeviceMusic(forzar = true) },
                onImportFolder = { folderPickerLauncher.launch(null) },
                onToggleFavorite = { songId, isFav -> viewModel.toggleFavorite(songId, isFav) }
            )
        }
    }

    /**
     * Sin POST_NOTIFICATIONS el servicio de reproducción funciona pero la notificación no se
     * ve, y con ella se van los controles de bloqueo y de los auriculares. Se pide al entrar,
     * no cuando ya está sonando, porque llega con el resto de permisos y no interrumpe.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val already = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!already) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
fun MusicApp(viewModel: MusicViewModel, onRescan: () -> Unit, onImportFolder: () -> Unit, onToggleFavorite: (Long, Boolean) -> Unit = { _, _ -> }) {
    // Los ajustes se leen ANTES de MusicTheme: el tema se construye con el acento y el fondo
    // negro, así que si se recogieran dentro, el color aplicado sería siempre el del paso
    // anterior y cambiar el acento se vería con un fotograma de retraso.
    val accentColor = viewModel.settings.accentColor.collectAsStateWithLifecycle(
        AppSettings.DEFAULT_ACCENT
    )
    val pureBlack = viewModel.settings.pureBlack.collectAsStateWithLifecycle(true)

    MusicTheme(accentColor = accentColor.value, pureBlack = pureBlack.value) {
        val permissionGranted by viewModel.permissionGranted.collectAsStateWithLifecycle<Boolean>()
        val songs by viewModel.allSongs.collectAsStateWithLifecycle(emptyList())
        val favorites by viewModel.favoriteSongs.collectAsStateWithLifecycle(emptyList())
        val folders by viewModel.allFolders.collectAsStateWithLifecycle(emptyList())
        // Antes esto era `emptyMap()` fijo, así que FoldersScreen marcaba "0 canciones" en
        // todas las carpetas aunque la base tuviera la relación. El ViewModel ya lo
        // calculaba; simplemente no estaba cableado.
        val songsByFolder by viewModel.songsByFolder.collectAsStateWithLifecycle(emptyMap())
        // Type argument explícito evita el pitfall de inferencia nullable en el delegate
        val currentSongState = viewModel.currentSong.collectAsStateWithLifecycle<Song?>()
        val playbackStateState =
            viewModel.playbackState.collectAsStateWithLifecycle<com.example.music.player.PlaybackState>()

        // Con permiso -> escanear una vez; sin permiso la Activity solicita el permiso
        if (permissionGranted) {
            LaunchedEffect(Unit) { viewModel.scanDeviceMusic() }
        }

        val musicManager = viewModel.musicManagerPublic
        val navController = rememberNavController()

        MusicNavHost(
            navController = navController,
            allSongs = songs,
            favorites = favorites,
            folders = folders,
            songsByFolder = songsByFolder,
            musicManager = musicManager,
            currentSong = currentSongState.value,
            playbackState = playbackStateState.value,
            onNavigateToPlayer = { song -> viewModel.playSong(song) },
            onRescan = onRescan,
            onImportFolder = onImportFolder,
            onToggleFavorite = onToggleFavorite,
            onAddFolder = { name, genre, color -> viewModel.createFolder(name, genre, color) },
            settings = viewModel.settings,
            onAudioSettingsChanged = { musicManager.applySettings() }
        )
    }
}
