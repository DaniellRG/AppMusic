package com.example.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.music.ui.components.ShimmerBox
import com.example.music.model.Song
import com.example.music.ui.viewmodel.MusicViewModel
import androidx.compose.material3.MaterialTheme
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
                    // Ajustes vive aquí. Estaba en el FAB, pero el FAB quedó como columna de
                    // acciones de biblioteca (escanear/importar) y Ajustes se quedó sin entrada
                    // a la pantalla: unreachable desde el menú.
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
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
                        containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        if (menuExpanded) Icons.Default.Close else Icons.Default.MoreVert,
                        contentDescription = if (menuExpanded) "Cerrar menú" else "Acciones",
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


            Spacer(modifier = Modifier.height(8.dp))

            // --- SECCIÓN: Favoritos ---
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
                                color = MaterialTheme.colorScheme.primary
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

            Spacer(modifier = Modifier.height(8.dp))

            // --- SECCIÓN: Toda la música ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Toda tu música",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onFoldersClick) {
                    Text(
                        text = "Ver carpetas",
                        style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Chips de orden. El estado vive aquí y no en el ViewModel porque es una
            // preferencia de esta pantalla, no de la biblioteca: al salir y volver se
            // pierde, que es lo razonable.
            val ordenState = remember { mutableStateOf(OrdenBiblioteca.TITULO) }
            val orden by ordenState
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OrdenBiblioteca.entries.forEach { opcion ->
                    FilterChip(
                        selected = orden == opcion,
                        onClick = { ordenState.value = opcion },
                        label = { Text(opcion.etiqueta, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(ordenarBiblioteca(songs, orden)) { song ->
                    SongItemCard(
                        song = song,
                        onClick = { onSongClick(song) }
                    )
                }
            }
        }
    }
}

/** Criterios de orden de la biblioteca en Inicio. */
enum class OrdenBiblioteca(val etiqueta: String) {
    TITULO("Título"),
    ARTISTA("Artista"),
    ALBUM("Álbum"),
    DURACION("Duración"),
    RECIENTE("Recientes")
}

/**
 * Aplica el criterio de orden a una copia, nunca al `List` recibido.
 *
 * El desempate es siempre por título y sin distinguir mayúsculas: si no, dos canciones
 * con el mismo artista/album se reordenan solas entre recargas, y el usuario ve como las
 * filas le "saltan". `sortedBy` es estable, así que añadir el título al final ya da un
 * resultado determinista.
 */
internal fun ordenarBiblioteca(songs: List<Song>, orden: OrdenBiblioteca): List<Song> =
    when (orden) {
        OrdenBiblioteca.TITULO -> songs.sortedBy { it.title.lowercase() }
        OrdenBiblioteca.ARTISTA -> songs.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
        OrdenBiblioteca.ALBUM -> songs.sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() }))
        OrdenBiblioteca.DURACION -> songs.sortedBy { it.durationMs }
        OrdenBiblioteca.RECIENTE ->
            // El id lo asigna SQLite y crece con cada inserción, así que es el más nuevo
            // que se ha añadido a la biblioteca. No hay columna de fecha de añadido.
            songs.sortedByDescending { it.id }
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
        shape = RoundedCornerShape(16.dp),
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
                    SongArtwork(
                        song = song,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        iconSize = 22.dp
                    )
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

/**
 * Portada de una canción, con la nota musical como respaldo POR DEBAJO.
 *
 * Antes esto estaba copiado tres veces (hero, favoritos y fila de la lista) y las tres
 * usaban `if (coverUri != null) imagen else nota`. El fallo de ese patrón: una canción
 * que sí tiene `coverUri` pero cuya imagen tarda en cargar, o cuyo fichero se borró del
 * móvil, se quedaba en blanco hasta saber qué hacer. Aquí la nota está siempre debajo,
 * así que el hueco nunca está vacío y la imagen la tapa en cuanto llega.
 * @param iconSize alto de la nota de respaldo. El tamaño se pasa porque las tres
 *        copias originales lo tenían al 50% del lado, y con un valor fijo se ve
 *        diminuta en la fila de 48dp y enorme en el hero de 80dp.
 */
@Composable
fun SongArtwork(
    song: Song,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    iconSize: Dp = 24.dp,
    contentDescription: String? = null
) {
    val fallbackColor = getGenreColor(song.genre)
    Box(
        modifier = modifier
            .clip(shape)
            .background(fallbackColor.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = fallbackColor.copy(alpha = 0.6f)
        )
        if (song.coverUri != null) {
            SubcomposeAsyncImage(
                model = song.coverUri,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    // Shimmer mientras Coil descarga la carátula. Antes solo se veía el color
                    // plano del género y la lista parecía muerta durante el primer scroll.
                    ShimmerBox(modifier = Modifier.fillMaxSize(), shape = shape)
                }
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
        shape = RoundedCornerShape(16.dp),
        color = SurfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini artwork
            SongArtwork(
                song = song,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                iconSize = 24.dp
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
                contentDescription = "Más",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
