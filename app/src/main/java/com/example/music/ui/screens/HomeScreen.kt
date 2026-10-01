package com.example.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.music.model.Song
import com.example.music.ui.viewmodel.MusicViewModel
import com.example.music.ui.theme.AccentPrimary
import com.example.music.ui.theme.BackgroundDark
import com.example.music.ui.theme.SurfaceDark
import com.example.music.ui.theme.SurfaceVariant
import com.example.music.ui.theme.getGenreColor
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    songs: List<Song>,
    favorites: List<Song>,
    onSongClick: (Song) -> Unit,
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onFoldersClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRescan: () -> Unit = {},
    onImportFolder: () -> Unit = {},
    shrinkTopBar: Boolean = false
) {
    val scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val currentTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }

    // El hero se alimenta del estado real de reproduccion en vez de de songs.first().
    // Se lee el ViewModel con viewModel() en vez de anadir parametros, para no romper la
    // firma que llama MusicNavHost (fichero compartido, solo lectura).
    val vm: MusicViewModel = viewModel()
    val nowPlaying by vm.currentSong.collectAsStateWithLifecycle()
    val playbackState by vm.playbackState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Column {
                        Text(
                            text = "Bienvenido de nuevo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentTime,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onFavoritesClick) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Favoritos",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onFoldersClick) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = "Carpetas",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Ajustes",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            var menuExpanded by remember { mutableStateOf(false) }
            Column(horizontalAlignment = Alignment.End) {
                if (menuExpanded) {
                    FilledTonalButton(
                        onClick = { menuExpanded = false; onRescan() },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(" Escanear", modifier = Modifier.padding(start = 4.dp))
                    }
                    FilledTonalButton(
                        onClick = { menuExpanded = false; onImportFolder() },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(" Importar carpeta", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                FloatingActionButton(
                    onClick = { menuExpanded = !menuExpanded },
                    containerColor = AccentPrimary
                ) {
                    Icon(
                        if (menuExpanded) Icons.Default.Close else Icons.Default.MoreVert,
                        contentDescription = if (menuExpanded) "Cerrar menÃº" else "Acciones",
                        tint = Color.White
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(paddingValues)
        ) {
            // --- HERO ---
            // Si hay algo sonando se muestra eso; si no, se propone la primera de la
            // biblioteca. Antes ponia siempre songs.first() bajo el rotulo "Ahora
            // reproduciendo", que es mentira: era la cancion alfabetica, no la que suena.
            val heroSong = nowPlaying ?: songs.firstOrNull()
            if (heroSong != null) {
                // Tres estados, no dos: que haya una cancion cargada no es lo mismo que este
                // sonando. Con el rotulo de "Ahora reproduciendo" en pausa era mentira.
                val isNowPlaying = nowPlaying != null && playbackState.isPlaying
                val heroLabel = when {
                    isNowPlaying -> "Ahora reproduciendo"
                    nowPlaying != null -> "En pausa"
                    else -> "Empieza por aqui"
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark)
                        .padding(16.dp)
                ) {
                    Text(
                        text = heroLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Album art circular
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(getGenreColor(heroSong.genre).copy(alpha = 0.3f)),
                            content = {
                                if (heroSong.coverUri != null) {
                                    AsyncImage(
                                        model = heroSong.coverUri,
                                        contentDescription = heroSong.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        tint = getGenreColor(heroSong.genre)
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = heroSong.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = heroSong.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Home,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = heroSong.genre,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            // Antes este boton era onSongClick(heroSong), o sea NADA de lo que
                            // prometia el icono: aparecia Pause y al tocarlo se abria el
                            // reproductor mientras la cancion seguia sonando. Ahora reproduce
                            // o pausa de verdad; solo navega si el hero no es lo que suena.
                            onClick = {
                                if (nowPlaying?.id == heroSong.id) {
                                    vm.togglePlayPause()
                                } else {
                                    vm.playSong(heroSong)
                                    onSongClick(heroSong)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    AccentPrimary,
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                if (isNowPlaying && playbackState.isPlaying) {
                                    Icons.Default.Pause
                                } else {
                                    Icons.Default.PlayArrow
                                },
                                contentDescription = if (isNowPlaying && playbackState.isPlaying) {
                                    "Pausar"
                                } else {
                                    "Reproducir"
                                },
                                tint = Color.White,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    // Progreso de la cancion que suena, en el borde inferior del hero. Solo
                    // si hay algo en cola: con duration 0 una barra llena parece un bug.
                    if (isNowPlaying && playbackState.durationMs > 0L) {
                        LinearProgressIndicator(
                            progress = {
                                (playbackState.currentPositionMs.toFloat() /
                                    playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = AccentPrimary,
                            trackColor = Color(0xFF3A3A3A),
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                    }
                }
            }

            // --- SECCIÃ“N: Favoritos rÃ¡pidos ---
            if (favorites.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Favoritos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = onFavoritesClick) {
                            Text(
                                text = "Ver todo",
                                style = MaterialTheme.typography.labelMedium,
                                color = AccentPrimary
                            )
                        }
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(favorites.take(5)) { song ->
                            FavoriteChip(song = song, onClick = { onSongClick(song) })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- SECCIÃ“N: Toda la mÃºsica ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Toda tu mÃºsica",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onFoldersClick) {
                    Text(
                        text = "Ver carpetas",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentPrimary
                    )
                }
            }

            LazyColumn(
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

@Composable
fun FavoriteChip(
    song: Song,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(getGenreColor(song.genre).copy(alpha = 0.3f)),
                content = {
                    if (song.coverUri != null) {
                        AsyncImage(
                            model = song.coverUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            tint = getGenreColor(song.genre).copy(alpha = 0.7f)
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = song.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SongItemCard(
    song: Song,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini artwork
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(getGenreColor(song.genre).copy(alpha = 0.2f)),
                content = {
                    if (song.coverUri != null) {
                        AsyncImage(
                            model = song.coverUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            tint = getGenreColor(song.genre).copy(alpha = 0.5f)
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = song.genre,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "MÃ¡s",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
