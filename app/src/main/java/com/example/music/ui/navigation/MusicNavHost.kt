package com.example.music.ui.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.music.model.Folder
import com.example.music.model.Song
import com.example.music.player.MusicManager
import com.example.music.player.PlaybackState
import com.example.music.ui.screens.FavoritesScreen
import com.example.music.ui.screens.FoldersScreen
import com.example.music.ui.screens.HomeScreen
import com.example.music.ui.screens.PlayerScreen
import com.example.music.ui.screens.SearchScreen
import com.example.music.ui.screens.SongItemCard
import com.example.music.ui.theme.BackgroundDark
import com.example.music.ui.theme.SurfaceDark
import com.example.music.ui.theme.getGenreColor
import com.example.music.ui.screens.SettingsScreen
import com.example.music.settings.AppSettings

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Player : Screen("player/{songId}") {
        fun createRoute(songId: Long) = "player/$songId"
    }
    data object Search : Screen("search")
    data object Favorites : Screen("favorites")
    data object Folders : Screen("folders")
    data object FolderDetail : Screen("folder/{folderId}/{folderName}") {
        fun createRoute(folderId: Long, folderName: String) = "folder/$folderId/" + Uri.encode(folderName)
    }
    data object Settings : Screen("settings")
}

@Composable
fun MusicNavHost(
    navController: NavHostController = rememberNavController(),
    allSongs: List<Song>,
    favorites: List<Song>,
    folders: List<Folder>,
    songsByFolder: Map<Long, List<Song>>,
    musicManager: MusicManager,
    currentSong: Song?,
    playbackState: PlaybackState,
    onNavigateToPlayer: (Song) -> Unit,
    onRescan: () -> Unit = {},
    onImportFolder: () -> Unit = {},
    onToggleFavorite: (Long, Boolean) -> Unit = { _, _ -> },
    onAddFolder: (String, String, String) -> Unit = { _, _, _ -> },
    settings: AppSettings? = null,
    onAudioSettingsChanged: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                songs = allSongs,
                favorites = favorites,
                onSongClick = { song ->
                    onNavigateToPlayer(song)
                    navController.navigate(Screen.Player.createRoute(song.id))
                },
                onSearchClick = { navController.navigate(Screen.Search.route) },
                onFavoritesClick = { navController.navigate(Screen.Favorites.route) },
                onFoldersClick = { navController.navigate(Screen.Folders.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onRescan = onRescan,
                onImportFolder = onImportFolder
            )
        }

        composable(Screen.Settings.route) {
            settings?.let { appSettings ->
                SettingsScreen(
                    settings = appSettings,
                    onBack = { navController.popBackStack() },
                    onAudioSettingsChanged = onAudioSettingsChanged
                )
            }
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(navArgument("songId") { type = NavType.LongType })
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getLong("songId") ?: return@composable
            val currentIndex = allSongs.indexOfFirst { it.id == songId }
            PlayerScreen(
                currentSong = currentSong,
                playbackState = playbackState,
                allSongs = allSongs,
                currentSongIndex = currentIndex,
                musicManager = musicManager,
                onBackClick = { navController.popBackStack() },
                onClosePlayer = { navController.popBackStack() },
                onToggleFavorite = onToggleFavorite
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                songs = allSongs,
                onSongClick = { song ->
                    onNavigateToPlayer(song)
                    navController.navigate(Screen.Player.createRoute(song.id))
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Favorites.route) {
            FavoritesScreen(
                favorites = favorites,
                onSongClick = { song ->
                    onNavigateToPlayer(song)
                    navController.navigate(Screen.Player.createRoute(song.id))
                },
                onRemoveFavorite = { songId -> onToggleFavorite(songId, false) },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Folders.route) {
            FoldersScreen(
                folders = folders,
                songsByFolder = songsByFolder,
                onFolderClick = { folder ->
                    navController.navigate(Screen.FolderDetail.createRoute(folder.id, folder.name))
                },
                onAddFolder = { name, genre, color -> onAddFolder(name, genre, color) },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.FolderDetail.route,
            arguments = listOf(
                navArgument("folderId") { type = NavType.LongType },
                navArgument("folderName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getLong("folderId") ?: return@composable
            val folderName = backStackEntry.arguments?.getString("folderName") ?: "Carpeta"
            FolderDetailScreen(
                folderId = folderId,
                folderName = folderName,
                songs = songsByFolder[folderId] ?: emptyList(),
                onSongClick = { song ->
                    onNavigateToPlayer(song)
                    navController.navigate(Screen.Player.createRoute(song.id))
                },
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folderId: Long,
    folderName: String,
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark
                )
            )
        }
    ) { paddingValues ->
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(
                        text = "Esta carpeta está vacía",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(songs) { song ->
                    SongItemCard(
                        song = song,
                        onClick = { onSongClick(song) }
                    )
                }
            }
        }
    }
}
