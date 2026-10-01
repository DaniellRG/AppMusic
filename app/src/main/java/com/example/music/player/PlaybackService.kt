package com.example.music.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.music.MainActivity

/**
 * Servicio que publica la reproducción en el sistema: notificación multimedia, controles de
 * la pantalla de bloqueo, botones de auriculares y Android Auto.
 *
 * Sin esto la app "suena" pero no se parece a una app de música: no hay notificación, al
 * bloquear el móvil no se puede pausar y al recibir una llamada no se corta.
 *
 * Envuelve el ExoPlayer del [MusicManager] en lugar de crear el suyo. Si creara otro, la
 * notificación controlaría un player distinto del que está sonando, que es el error clásico.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = MusicManager.getInstance(this).player

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(buildSessionActivityIntent())
            .setId("music_session")
            .build()

        // El provider por defecto es el que sabe montar la notificación con el estilo de
        // Android 13 (MediaStyle) y los botones de saltar. No hace falta escribirla a mano.
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build())

        // Imprescindible: Media3 sólo publica la notificación desde addSession. Si nos
        // limitamos a devolver la sesión en onGetSession, eso no ocurre hasta que se conecte
        // algún controlador (Android Auto, Assistant, Wear), así que arrancando la app y
        // poniendo música no aparece notificación ninguna. Se comprobó en el emulador.
        addSession(mediaSession!!)
    }

    /**
     * Al tocar la notificación se abre la app. Sin este PendingIntent el toque no hace nada,
     * que es justo el fallo que hace que una app de música parezca un juguete.
     */
    private fun buildSessionActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * El sistema pregunta aquí cuál es la sesión. Con que la devolvamos, Media3 levanta la
     * notificación y mantiene el servicio en primer plano mientras suene.
     */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * Si el usuario cierra la app del recent y no hay nada sonando, el servicio sobra. Si sí
     * suena, tiene que quedarse vivo para sostener la notificación.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = MusicManager.getInstance(this).player
        if (player.playbackState == Player.STATE_IDLE || !player.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Sólo se libera la sesión. El player es el singleton de MusicManager y lo sigue
        // usando la UI: liberarlo aquí mataría la reproducción al rotar la pantalla.
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}