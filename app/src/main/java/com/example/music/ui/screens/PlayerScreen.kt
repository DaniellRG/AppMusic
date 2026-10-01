package com.example.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.music.network.LrcLine
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.music.model.Song
import com.example.music.player.MusicManager
import com.example.music.ui.viewmodel.LyricsUi
import com.example.music.ui.viewmodel.MusicViewModel
import com.example.music.player.RepeatMode
import com.example.music.ui.theme.getGenreColor
import com.example.music.ui.theme.*
import com.example.music.ui.components.VinylArtwork
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    currentSong: Song?,
    playbackState: com.example.music.player.PlaybackState,
    allSongs: List<Song>,
    currentSongIndex: Int,
    musicManager: MusicManager,
    onBackClick: () -> Unit,
    onClosePlayer: () -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit
) {
    val scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    // VM (activity-scope): estado online (letras + portada) y fetch al cambiar de canción.
    val vm: MusicViewModel = viewModel()
    val lyricsState by vm.lyrics.collectAsStateWithLifecycle(LyricsUi.Idle)
    val coverUrl by vm.coverUrl.collectAsStateWithLifecycle<String?>(null)
    val coverLoading by vm.coverLoading.collectAsStateWithLifecycle(false)
    LaunchedEffect(currentSong?.id) {
        vm.fetchLyrics(currentSong)
        vm.fetchCover(currentSong)
    }

    // Determinar índice actual de la canción reproduciéndose
    val actualCurrentIndex = remember(allSongs, currentSong) {
        allSongs.indexOfFirst { it.id == currentSong?.id }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showLyrics = !showLyrics; android.util.Log.d("Player", "toggle showLyrics=$showLyrics") }) {
                        Icon(
                            if (showLyrics) Icons.Filled.MusicNote else Icons.Outlined.MusicNote,
                            contentDescription = "Letras",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showQueue = !showQueue }) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Cola",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onClosePlayer) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark.copy(alpha = 0.8f)
                )
            )
        }
    ) { paddingValues ->
        // Con el panel de letra/cola abierto hay UN solo scroll: el del panel. Antes la raiz
        // llevaba .verticalScroll y el panel otro LazyColumn dentro, y los dos se peleaban por
        // el mismo gesto: en la cola larga no se podia bajar bien y el arrastre movia las dos
        // zonas a la vez. Con el panel cerrado el scroll es el de la raiz.
        val panelOpen = showLyrics || showQueue
        val rootScroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(paddingValues)
                .then(if (panelOpen) Modifier else Modifier.verticalScroll(rootScroll))
        ) {
            if (currentSong == null) {
                // Estado vacío
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No hay música reproduciéndose",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // --- PORTADA ---
                    // Con el panel abierto la portada se encoge: si se quedara a pantalla
                    // completa no le quedaria alto al panel, que es lo que se esta mirando.
                    val coverModel = coverUrl ?: currentSong.coverUri
                    VinylArtwork(
                        model = coverModel,
                        title = currentSong.title,
                        isPlaying = playbackState.isPlaying,
                        fallbackColor = getGenreColor(currentSong.genre),
                        modifier = if (panelOpen) {
                            Modifier
                                .padding(top = 12.dp)
                                .size(96.dp)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(24.dp)
                        }
                    )

                    // --- INFO DE LA CANCIÓN ---
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = currentSong.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentSong.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentSong.album,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // --- BOTONES DE CONTROL (fila horizontal) ---
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            // Antes se calculaba prevIndex/nextIndex aqui y no se usaba. Con la
                            // lista vacia daba -1 y, si alguien lo llega a usar, revienta. La
                            // cola ya la lleva ExoPlayer, asi que se le pregunta a el.
                            onClick = { musicManager.skipToPrevious() },
                            modifier = Modifier
                                .size(56.dp)
                                .background(SurfaceVariant, CircleShape)
                        ) {
                            Icon(
                                Icons.Filled.SkipPrevious,
                                contentDescription = "Anterior",
                                modifier = Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { musicManager.togglePlayPause() },
                            modifier = Modifier
                                .size(72.dp)
                                .background(AccentPrimary, CircleShape)
                        ) {
                            Icon(
                                if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pausar" else "Reproducir",
                                modifier = Modifier.padding(16.dp),
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { musicManager.skipToNext() },
                            modifier = Modifier
                                .size(56.dp)
                                .background(SurfaceVariant, CircleShape)
                        ) {
                            Icon(
                                Icons.Filled.SkipNext,
                                contentDescription = "Siguiente",
                                modifier = Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // --- BARRA DE PROGRESO ---
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Slider(
                            value = playbackState.currentPositionMs.toFloat(),
                            onValueChange = { musicManager.seekTo(it.toLong()) },
                            valueRange = 0f..playbackState.durationMs.toFloat().coerceAtLeast(1f),
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = AccentPrimary,
                                activeTrackColor = AccentPrimary,
                                inactiveTrackColor = SurfaceVariant
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(playbackState.currentPositionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatTime(playbackState.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // --- CONTROLES SECUNDARIOS (etiquetados, visibles) ---
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(
                                onClick = {
                                    val nextMode = when (playbackState.repeatMode) {
                                        RepeatMode.OFF -> RepeatMode.ALL
                                        RepeatMode.ALL -> RepeatMode.ONE
                                        RepeatMode.ONE -> RepeatMode.OFF
                                    }
                                    musicManager.setRepeatMode(nextMode)
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (playbackState.repeatMode != RepeatMode.OFF) AccentPrimary else Color(0xFF3A3A3A),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    when (playbackState.repeatMode) {
                                        RepeatMode.ONE -> Icons.Filled.RepeatOne
                                        RepeatMode.ALL -> Icons.Filled.Repeat
                                        RepeatMode.OFF -> Icons.Outlined.Repeat
                                    },
                                    contentDescription = "Repetir",
                                    tint = if (playbackState.repeatMode != RepeatMode.OFF) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text("Repetir", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(
                                onClick = { musicManager.toggleShuffle() },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (playbackState.shuffleMode) AccentPrimary else Color(0xFF3A3A3A),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.Shuffle,
                                    contentDescription = "Aleatorio",
                                    tint = if (playbackState.shuffleMode) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text("Aleatorio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(
                                onClick = { onToggleFavorite(currentSong.id, !currentSong.isFavorite) },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (currentSong.isFavorite) AccentTertiary else Color(0xFF3A3A3A),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    if (currentSong.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favorito",
                                    tint = if (currentSong.isFavorite) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text("Favorito", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SleepTimerControl(
                            manager = musicManager,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // --- SECCIÓN: LETRAS / COLA ---
                    if (showLyrics) {
                        LyricsSection(
                            song = currentSong,
                            modifier = Modifier.weight(1f)
                        )
                    } else if (showQueue) {
                        QueueSection(
                            songs = allSongs,
                            currentIndex = actualCurrentIndex,
                            onSongClick = { index ->
                                if (index in allSongs.indices) musicManager.playSong(allSongs[index])
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerControl(manager: MusicManager, modifier: Modifier = Modifier) {
    val left by manager.sleepTimer.collectAsStateWithLifecycle(0)
    var showDialog by remember { mutableStateOf(false) }
    val active = left > 0

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = { showDialog = true },
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (active) AccentPrimary else Color(0xFF3A3A3A),
                    CircleShape
                )
        ) {
            Icon(
                Icons.Filled.Bedtime,
                contentDescription = "Temporizador de sueño",
                tint = if (active) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = if (active) formatSleepRemaining(left) else "Dormir",
            style = MaterialTheme.typography.labelSmall,
            color = if (active) AccentPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (!showDialog) return
    val presets = listOf(15, 30, 45, 60)
    AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { Text("Temporizador de sueño") },
        text = {
            Column {
                presets.forEach { minutes ->
                    val seconds = minutes * 60
                    val running = left in 1..seconds && (left > seconds - 65)
                    TextButton(
                        onClick = {
                            manager.setSleepTimer(if (running) 0 else seconds)
                            showDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (running) "$minutes min · activo" else "$minutes min",
                            color = if (running) AccentPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (active) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            manager.setSleepTimer(0)
                            showDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cancelar temporizador", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showDialog = false }) { Text("Cerrar") }
        }
    )
}

private fun formatSleepRemaining(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "%d:%02d".format(m, s) else "%ds".format(s)
}

@Composable
fun LyricsSection(song: Song, modifier: Modifier = Modifier) {
    val vm: MusicViewModel = viewModel()
    val lyricsState by vm.lyrics.collectAsStateWithLifecycle(LyricsUi.Idle)
    val coverUrl by vm.coverUrl.collectAsStateWithLifecycle<String?>(null)
    val coverLoading by vm.coverLoading.collectAsStateWithLifecycle(false)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Letras",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            // Descargar portada (online iTunes o embebida en el ID3)
            IconButton(
                onClick = { vm.downloadCover(song, coverUrl ?: song.coverUri) },
                enabled = !coverLoading && (coverUrl != null || song.coverUri != null)
            ) {
                Icon(
                    imageVector = if (coverLoading) Icons.Default.Download else Icons.Outlined.Download,
                    contentDescription = "Descargar portada",
                    tint = if (coverUrl != null || song.coverUri != null)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        val ly = lyricsState
        when (ly) {
            is LyricsUi.Loading -> Text(
                text = "Buscando letra…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            is LyricsUi.Success -> {
                if (ly.synced.isNotEmpty()) {
                    SyncedLyrics(ly.synced)
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = ly.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Fuente: ${ly.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is LyricsUi.Error -> Text(
                text = "${ly.message}\n(Usa la portada embebida si la pista la tiene.)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            is LyricsUi.Idle -> Text(
                text = "Letra de ${song.title} en vivo (online). Se busca al reproducir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Letra sincronizada: resalta la línea que suena y la mantiene a la vista.
 *
 * Recibe las líneas YA PARSEADAS (con su tiempo en ms) desde el ViewModel, así que aquí no
 * hay que interpretar nada: solo se busca cuál es la última que ya ha empezado. Con esa
 * técnica no hace falta binsearch porque los tiempos llegan ordenados de `parseLrc`.
 */
@Composable
fun SyncedLyrics(lines: List<LrcLine>) {
    val vm: MusicViewModel = viewModel()
    val playbackState by vm.playbackState.collectAsStateWithLifecycle()
    val posicion = playbackState.currentPositionMs
    val scroll = rememberScrollState()

    // Índice de la línea activa: la última cuyo tiempo ya pasó. -1 si aún no empieza ninguna.
    val activa = remember(lines, posicion) {
        lines.indexOfLast { it.timeMs <= posicion }
    }

    // Auto-scroll: sigue a la línea activa, pero sin pelearse con el usuario si está
    // scrolleando a mano. Se recentra sola un poco después de que pare.
    LaunchedEffect(activa) {
        if (activa >= 0) {
            // Altura aproximada de una línea + separación; suficiente para centrar sin
            // necesitar medir el texto de verdad.
            scroll.animateScrollTo(activa * LINE_HEIGHT_PX)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(scroll)
    ) {
        lines.forEachIndexed { i, linea ->
            val esActiva = i == activa
            Text(
                text = linea.text,
                style = if (esActiva) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (esActiva) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                },
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }
}

/** Alto aproximado de una línea de letra, para el auto-scroll centrado. */
private const val LINE_HEIGHT_PX = 64

@Composable
fun QueueSection(
    songs: List<Song>,
    currentIndex: Int,
    onSongClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                text = "Cola de reproducción",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(songs.size) { index ->
            val song = songs.getOrNull(index)
            if (song != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSongClick(index) }
                        .background(
                            if (index == currentIndex) AccentPrimary.copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = index == currentIndex,
                        onClick = { },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = AccentPrimary,
                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
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
                    }
                    if (index == currentIndex) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = AccentPrimary
                        )
                    }
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
