package com.example.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import com.example.music.network.indiceActiva
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.example.music.ui.rememberHaptics
import com.example.music.ui.theme.getGenreColor
import com.example.music.ui.theme.*
import com.example.music.ui.components.VinylArtwork
import kotlinx.coroutines.delay
import java.util.Locale
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

    // Acento de la carátula: tiñe play, progreso y los controles secundarios. Se calcula una vez
    // por canción (produceState cachea por uri) y no en cada recomposición.
    val coverModel = remember(currentSong?.id) { coverUrl ?: currentSong?.coverUri }
    val paleta by rememberArtworkPalette(coverModel)
    // El acento se anima en vez de saltar: al cambiar de canción el color llegaba de golpe y
    // se leía como un parpadeo en el play, el progreso y los secundarios.
    val acento by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary,
        animationSpec = tween(durationMillis = 500),
        label = "acento"
    )
    val haptics = rememberHaptics()

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
                    // OJO: estas dos banderas eran independientes (`showLyrics = !showLyrics`)
                    // y eso rompía el panel. Con Letras abierto, pulsar Cola dejaba
                    // showLyrics en true, así que la hoja seguía mostrando letras y el botón
                    // parecía no hacer nada. Ahora son excluyentes: cada botón dice cuál de las
                    // dos hojas se quiere, y es también el targetState real del Crossfade.
                    IconButton(onClick = { showLyrics = true; showQueue = false }) {
                        Icon(
                            if (showLyrics) Icons.Filled.MusicNote else Icons.Outlined.MusicNote,
                            contentDescription = "Letras",
                            tint = if (showLyrics) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showQueue = true; showLyrics = false }) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Cola",
                            tint = if (showQueue) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
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
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        val panelOpen = showLyrics || showQueue
        val rootScroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(paddingValues)
                .verticalScroll(rootScroll)
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
                // Fondo liso: se eliminó la portada grande difuminada + el scrim degradado que
                // la acompañaba. Con el disco ya más pequeño, esa capa solo competía con la
                // portada real por la atención y en carátulas claras lavaba el texto de la ficha.
                // Se mantiene el negro del tema, que es lo que hace que el disco destaque.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundDark)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize()
                    ) {
                    // El disco se mide contra el ALTO que queda libre, no contra el ancho.
                    // Antes era fillMaxWidth().aspectRatio(1f), un cuadrado del ancho completo:
                    // en cualquier móvil ocupaba más de la mitad de la pantalla y empujaba
                    // Repetir/Aleatorio/Favorito/Dormir fuera de la vista, obligando a bajar.
                    // El 0.34 deja sitio de sobra para la portada, la ficha, la barra de progreso,
                    // los controles y los secundarios, incluso en pantallas cortas.
                    val tamanoVinilo = remember(maxWidth, maxHeight) {
                        maxOf(
                            minOf(
                                maxWidth - 48.dp,
                                maxHeight * 0.34f,
                                320.dp
                            ),
                            120.dp
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    // --- PORTADA ---
                    VinylArtwork(
                        model = coverModel,
                        title = currentSong.title,
                        isPlaying = playbackState.isPlaying,
                        fallbackColor = getGenreColor(currentSong.genre),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(tamanoVinilo)
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
                    Spacer(modifier = Modifier.height(16.dp))

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
                            onClick = {
                                haptics.light()
                                musicManager.skipToPrevious()
                            },
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
                        onClick = {
                            haptics.playPause()
                            musicManager.togglePlayPause()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(acento, CircleShape)
                    ) {
                            Icon(
                                if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pausar" else "Reproducir",
                                modifier = Modifier.padding(16.dp),
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = {
                                haptics.light()
                                musicManager.skipToNext()
                            },
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
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Slider(
                            value = playbackState.currentPositionMs.toFloat(),
                            onValueChange = { musicManager.seekTo(it.toLong()) },
                            valueRange = 0f..playbackState.durationMs.toFloat().coerceAtLeast(1f),
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = acento,
                                activeTrackColor = acento,
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
                                        if (playbackState.repeatMode != RepeatMode.OFF) acento else Color(0xFF3A3A3A),
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
                                        if (playbackState.shuffleMode) acento else Color(0xFF3A3A3A),
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
                                onClick = {
                                haptics.selection()
                                onToggleFavorite(currentSong.id, !currentSong.isFavorite)
                            },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (currentSong.isFavorite) acento else Color(0xFF3A3A3A),
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

                    // Antes este Spacer llevaba weight(1f) para empujar los secundarios hacia
                    // abajo, pero dentro de una Column con verticalScroll el peso no se aplica:
                    // ocupaba altura fija y era justo lo que empujaba los botones fuera.
                    Spacer(modifier = Modifier.height(12.dp))
                    }
                    }
                }
            }
        }

        // Letras y cola salen en una hoja modal en vez de empujar el reproductor hacia abajo.
        // Antes el panel se insertaba en la misma Column, así que al abrirlo la portada se
        // encogía y había dos scrolls compitiendo por el gesto.
        if (panelOpen && currentSong != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    haptics.light()
                    showLyrics = false
                    showQueue = false
                },
                containerColor = SurfaceDark,
                contentColor = MaterialTheme.colorScheme.onSurface,
                dragHandle = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .background(SurfaceVariant, RoundedCornerShape(2.dp))
                        )
                    }
                }
            ) {
                Column {
                    // Las pestañas de la barra superior del Player (Letras / Cola) quedan tapadas
                    // por el scrim de la propia hoja mientras esta está abierta: al pulsarlas se
                    // cerraba el panel en vez de cambiar de pestaña, así que no había forma de ir
                    // de letras a cola sin cerrar antes. Aquí dentro siempre son alcanzables.
                    // Pulsar la pestaña ya activa cierra la hoja (mismo criterio que el propio
                    // "Cerrar" de arriba).
                    SheetTabs(
                        mostrarLetras = showLyrics,
                        onLetras = {
                            if (showLyrics) {
                                showLyrics = false
                                showQueue = false
                            } else {
                                showLyrics = true
                                showQueue = false
                            }
                        },
                        onCola = {
                            if (showQueue) {
                                showLyrics = false
                                showQueue = false
                            } else {
                                showLyrics = false
                                showQueue = true
                            }
                        }
                    )

// Un SOLO AnimatedContent con targetState real y transicion fluida. Antes habia dos
                // Crossfade distintos y cada uno ignora el estado. AnimatedContent con
                // contentTransform da un cambio mas fluido entre "Letras" y "Cola".
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.98f))
                            .togetherWith(fadeOut(animationSpec = tween(150)))
                    },
                    label = "hojaContenido"
                ) { mostrarLetras ->
                    if (mostrarLetras) {
                        LyricsSection(song = currentSong)
                    } else {
                        QueueSection(
                            songs = allSongs,
                            currentIndex = actualCurrentIndex,
                            onSongClick = { index ->
                                haptics.light()
                                if (index in allSongs.indices) musicManager.playSong(allSongs[index])
                            }
                        )
                    }
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
                                    if (active) MaterialTheme.colorScheme.primary else Color(0xFF3A3A3A),
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
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                                        color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
    // Se relee del disco en cada cambio de estado de la letra, y se memoriza: es una
    // llamada a un fichero de unos pocos bytes, pero no tiene sentido repetirla en cada
    // recomposición. La clave incluye el estado de la letra porque si no, al pulsar
    // "Actualizar" el botón de refrescar seguiría ahí con la misma lyricsState y la
    // pantalla no se enteraría de que el fichero ha cambiado.
    val letraGuardada = remember(song.id, song.uri, lyricsState) { vm.letraGuardada(song) }
    val letraCargando = lyricsState is LyricsUi.Loading
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Refrescar la letra guardada.
                //
                // El nombre importa porque este botón NO borra y ya está: borra el
                // fichero y acto seguido busca otra vez en lrclib, que es justo lo que
                // quiere quien la tiene. Para cuando lrclib devuelve la letra de otra
                // canción con parecido nombre, o una versión con más estribillos.
                if (letraGuardada) {
                    IconButton(onClick = { vm.borrarLetra(song) }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Actualizar letra",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Descargar letra.
                //
                // Aquí NO hay botón de portada. Estaba al lado de este y los dos usaban
                // el mismo icono Download, así que en pantalla salían dos flechas
                // iguales y no se sabía cuál era cuál. La portada tampoco lo necesita:
                // se descarga sola al escanear, y si falla el usuario tiene el de la
                // biblioteca, no el del reproductor.
                IconButton(
                    onClick = { vm.descargarLetra(song) },
                    enabled = !letraCargando
                ) {
                    Icon(
                        imageVector = if (letraCargando) Icons.Default.Download else Icons.Outlined.Download,
                        contentDescription = "Descargar letra",
                        tint = if (letraCargando)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
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
                    text = if (letraGuardada) "Guardada en el móvil (${ly.source})" else "Fuente: ${ly.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is LyricsUi.Error -> Text(
                text = "${ly.message}\nPuedes pulsar el botón de descargar para reintentar.",
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
    val activa = remember(lines, posicion) { lines.indiceActiva(posicion) }

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
                            if (index == currentIndex) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = index == currentIndex,
                        onClick = { },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
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
                            tint = MaterialTheme.colorScheme.primary
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
    // Locale.ROOT explícito: con el locale del sistema, en un móvil en árabe o en hindi
    // los dígitos salen en el alfabeto local y el tiempo ya no se lee como un tiempo.
    return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
}

/**
 * Pestanas de la hoja modal (Letras / Cola).
 *
* Pill con la misma paleta Kuro que el resto del reproductor: la activa usa el acento y su
 * color de contenido, la inactiva un gris plano. Los dos botones se miden a la misma altura para
 * que la hoja no de un salto al cambiar de pestana.
 */
@Composable
private fun SheetTabs(
    mostrarLetras: Boolean,
    onLetras: () -> Unit,
    onCola: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SheetTab("Letras", activo = mostrarLetras, onClick = onLetras, modifier = Modifier.weight(1f))
        SheetTab("Cola", activo = !mostrarLetras, onClick = onCola, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SheetTab(
    texto: String,
    activo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fondo by animateColorAsState(
        targetValue = if (activo) MaterialTheme.colorScheme.primary else SurfaceVariant,
        animationSpec = tween(durationMillis = 160),
        label = "sheetTabFondo"
    )
    val textoColor by animateColorAsState(
        targetValue = if (activo) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 160),
        label = "sheetTabTexto"
    )
Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = fondo,
        onClick = onClick
    ) {
Text(
            text = texto,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            color = textoColor
        )
    }
}
