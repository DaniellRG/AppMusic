package com.example.music.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ajustes de la app, backed por [SharedPreferences] y expuestos como [StateFlow] para que
 * Compose los recoja sin recargar la pantalla a mano.
 *
 * Se mantiene un singleton con(applicationContext) para que el `PlaybackService` y la UI
 * lean exactamente los mismos valores.
 */
class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("music_settings", Context.MODE_PRIVATE)

    private val _accentColor = MutableStateFlow(prefs.getInt(KEY_ACCENT, DEFAULT_ACCENT))
    val accentColor: StateFlow<Int> = _accentColor.asStateFlow()

    /**
     * Acento dinámico derivado de la portada que se reproduce (color vibrante via
     * [com.example.music.ui.components.VinylArtwork.rememberArtworkAccent]). Es transitorio:
     * no se persiste y vuelve a null al pausar o al no haber portada. [MusicTheme] lo prefiere
     * sobre [accentColor] para el efecto "acento por portada"; si es null usa el acento
     * elegido en Ajustes.
     */
    private val _coverAccent = MutableStateFlow<Color?>(null)
    val coverAccent: StateFlow<Color?> = _coverAccent.asStateFlow()

    /** Negro puro: en pantallas OLED ahorra batería y además es el fondo que usa el reproductor. */
    private val _pureBlack = MutableStateFlow(prefs.getBoolean(KEY_PURE_BLACK, true))
    val pureBlack: StateFlow<Boolean> = _pureBlack.asStateFlow()

    /**
     * Fundido al cambiar de pista.
     *
     * OJO: esto NO es un crossfade real. Media3 no solapa pistas ni tiene crossfade (no existe
     * el método, se comprobó en el 1.4.0), así que aquí sólo se hace una rampa de volumen: la
     * pista nueva entra de 0 a 1 y la vieja sale de 1 a 0 antes de terminar.
     */
    private val _fade = MutableStateFlow(prefs.getBoolean(KEY_FADE, true))
    val fade: StateFlow<Boolean> = _fade.asStateFlow()

    /** Oculta las canciones menores de N segundos al escanear. 0 desactiva el filtro. */
    private val _minDurationSec = MutableStateFlow(prefs.getInt(KEY_MIN_DURATION, 30))
    val minDurationSec: StateFlow<Int> = _minDurationSec.asStateFlow()

    private val _skipSilence = MutableStateFlow(prefs.getBoolean(KEY_SKIP_SILENCE, false))
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    /** Segundos que quedan de temporizador de sueño; 0 = apagado. */
    private val _sleepTimer = MutableStateFlow(0)
    val sleepTimer: StateFlow<Int> = _sleepTimer.asStateFlow()

    fun setAccentColor(color: Int) {
        prefs.edit().putInt(KEY_ACCENT, color).apply()
        _accentColor.value = color
    }

    /** Actualiza el acento dinámico por portada (llamado desde el Player al cargar una carátula). */
    fun setCoverAccent(color: Color?) {
        _coverAccent.value = color
    }

    fun setPureBlack(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PURE_BLACK, enabled).apply()
        _pureBlack.value = enabled
    }

    fun setFade(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FADE, enabled).apply()
        _fade.value = enabled
    }

    fun setMinDurationSec(seconds: Int) {
        prefs.edit().putInt(KEY_MIN_DURATION, seconds).apply()
        _minDurationSec.value = seconds
    }

    fun setSkipSilence(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SKIP_SILENCE, enabled).apply()
        _skipSilence.value = enabled
    }

    fun setSleepTimer(seconds: Int) {
        _sleepTimer.value = seconds
    }

    companion object {
        private const val KEY_ACCENT = "accent_color"
        private const val KEY_PURE_BLACK = "pure_black"
        private const val KEY_FADE = "fade"
        private const val KEY_MIN_DURATION = "min_duration_sec"
        private const val KEY_SKIP_SILENCE = "skip_silence"

        /**
         * Rosa del reproductor de m-inan (rgb 227,42,118). Es el acento por defecto porque
         * destaca sobre fondos oscuros y sobre la mayoría de carátulas.
         */
        const val DEFAULT_ACCENT = 0xFFE32A76.toInt()

        /**
         * Paleta de acentos para el selector de Ajustes. Los nombres están en español porque
         * se muestran tal cual en la interfaz.
         */
        val ACCENT_PRESETS: List<Pair<String, Int>> = listOf(
            "Rosa" to 0xFFE32A76.toInt(),
            "Cian" to 0xFF67D5FE.toInt(),
            "Phoenix" to 0xFF028AC4.toInt(),
            "Esmeralda" to 0xFF1DB954.toInt(),
            "Naranja" to 0xFFFF8214.toInt(),
            "Violeta" to 0xFFB388FF.toInt(),
            "Rojo" to 0xFFE53935.toInt(),
            "Amarillo" to 0xFFEFE939.toInt(),
            "Lima" to 0xFF9CCC65.toInt(),
            "Coral" to 0xFFF08080.toInt()
        )

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
    }
}
