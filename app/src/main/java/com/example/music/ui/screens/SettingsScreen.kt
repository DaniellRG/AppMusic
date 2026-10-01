package com.example.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.music.settings.AppSettings

/**
 * Ajustes de la app.
 *
 * Se muestran sólo opciones que el usuario puede cambiar y ver el efecto: si un interruptor
 * no hace nada visible, sobra.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onAudioSettingsChanged: () -> Unit
) {
    val accent by settings.accentColor.collectAsStateWithLifecycle()
    val pureBlack by settings.pureBlack.collectAsStateWithLifecycle()
    val fade by settings.fade.collectAsStateWithLifecycle()
    val skipSilence by settings.skipSilence.collectAsStateWithLifecycle()
    val minDuration by settings.minDurationSec.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // --- APARIENCIA ---

            SectionTitle("Apariencia")

            Text("Color de acento", style = MaterialTheme.typography.bodyMedium)
            // Paleta en fila: se desliza con el dedo, como el selector de Tuneify.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(AppSettings.ACCENT_PRESETS.size) { i ->
                    val (name, colorValue) = AppSettings.ACCENT_PRESETS[i]
                    val color = Color(colorValue)
                    val selected = colorValue == accent
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(58.dp)
                            .clickable { settings.setAccentColor(colorValue) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onBackground else color.copy(alpha = 0.4f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            SettingSwitch(
                title = "Negro puro",
                subtitle = "Fondo totalmente negro. Ahorra batería en pantallas OLED.",
                checked = pureBlack,
                onCheckedChange = settings::setPureBlack
            )

            // --- REPRODUCCIÓN ---

            SectionTitle("Reproducción")

            SettingSwitch(
                title = "Fundido entre canciones",
                subtitle = "La canción entra y sale bajando el volumen. No es un crossfade real: " +
                    "Media3 no solapa pistas.",
                checked = fade,
                onCheckedChange = {
                    settings.setFade(it)
                    // Hay que avisar al reproductor: el fundido vive dentro de ExoPlayer y
                    // apagar el ajuste a mitad de canción dejaría el volumen donde estuviera.
                    onAudioSettingsChanged()
                }
            )

            SettingSwitch(
                title = "Saltar silencios",
                subtitle = "Recorta los fragmentos mudos al principio y al final de la pista.",
                checked = skipSilence,
                onCheckedChange = {
                    settings.setSkipSilence(it)
                    onAudioSettingsChanged()
                }
            )

            // --- BIBLIOTECA ---

            SectionTitle("Biblioteca")

            Text(
                "Al escanear se descartan los audios de menos de $minDuration s por ser clips o " +
                    "avisos, no canciones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MIN_DURATION_CHOICES.size) { i ->
                    val value = MIN_DURATION_CHOICES[i]
                    FilterChip(
                        selected = value == minDuration,
                        onClick = { settings.setMinDurationSec(value) },
                        label = {
                            Text(
                                if (value == 0) "Sin filtro" else "$value s",
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Text(
                "El cambio se aplica al siguiente escaneo: vuelve a buscar música desde el menú " +
                    "de inicio para que se noten las diferencias.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Duraciones ofrecidas al escáner. 0 desactiva el descarte. */
private val MIN_DURATION_CHOICES = listOf(0, 10, 20, 30, 60, 120)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}