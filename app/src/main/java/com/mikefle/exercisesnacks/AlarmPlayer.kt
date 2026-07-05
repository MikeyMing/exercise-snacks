package com.mikefle.exercisesnacks

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper

/**
 * Plays the user-selected alarm sound at the user-selected volume on the ALARM
 * audio stream. We play the sound ourselves (instead of letting the notification
 * channel do it) because notification channels can't be re-sounded after creation
 * and offer no per-app volume control — both of which the user wants configurable.
 */
object AlarmPlayer {

    /** Sentinel stored in prefs when the user explicitly picks "None" in the ringtone picker. */
    const val SILENT = "silent"

    private val handler = Handler(Looper.getMainLooper())
    private var looper: MediaPlayer? = null    // looping "stop" alarm (SnackActivity)
    private var preview: MediaPlayer? = null   // one-shot Settings preview (stoppable)

    private fun alarmAttrs() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Resolve a stored sound string (uri / [SILENT] / null=default) to a playable Uri, or null for silent. */
    fun resolveUriFrom(s: String?): Uri? {
        if (s == SILENT) return null
        if (s != null) return runCatching { Uri.parse(s) }.getOrNull()
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    fun resolveUri(ctx: Context): Uri? = resolveUriFrom(Prefs.soundUri(ctx))

    fun volumeScalar(ctx: Context): Float = Prefs.volumePct(ctx).coerceIn(0, 100) / 100f

    /**
     * One-shot playback. [onDone] is ALWAYS invoked exactly once — on completion, error,
     * timeout, or if playback can't start — so callers can safely balance a goAsync() result.
     * When [track] is true the player is stored in [preview] so it can be cancelled/replaced
     * (used by the Settings "Test" button).
     */
    private fun playOneShot(
        ctx: Context, uri: Uri?, vol: Float, track: Boolean, maxMs: Long, onDone: () -> Unit
    ) {
        if (vol <= 0f || uri == null) { onDone(); return }

        val player: MediaPlayer
        try {
            player = MediaPlayer()
            player.setAudioAttributes(alarmAttrs())
            player.setDataSource(ctx.applicationContext, uri)
            player.setVolume(vol, vol)
        } catch (e: Exception) {
            onDone(); return
        }

        if (track) { stopPreview(); preview = player }

        var finished = false
        var timeout: Runnable? = null
        val finish = {
            if (!finished) {
                finished = true
                timeout?.let { handler.removeCallbacks(it) }   // don't leave the cap runnable lingering
                if (track && preview === player) preview = null
                try { player.stop() } catch (_: Exception) {}
                try { player.release() } catch (_: Exception) {}
                onDone()
            }
        }
        val cap = Runnable { finish() }
        timeout = cap

        player.setOnPreparedListener { runCatching { it.start() } }
        player.setOnCompletionListener { finish() }
        player.setOnErrorListener { _, _, _ -> finish(); true }
        try {
            player.prepareAsync()
            handler.postDelayed(cap, maxMs)   // safety cap so goAsync always finishes
        } catch (e: Exception) {
            if (track && preview === player) preview = null
            try { player.release() } catch (_: Exception) {}
            onDone()
        }
    }

    /** Play the currently-saved sound/volume once (used by the reminder BroadcastReceiver). */
    fun playOnce(ctx: Context, maxMs: Long = 9000L, onDone: () -> Unit) =
        playOneShot(ctx, resolveUri(ctx), volumeScalar(ctx), track = false, maxMs = maxMs, onDone = onDone)

    /** Preview arbitrary (possibly unsaved) settings once — used by the Settings "Test" button. */
    fun preview(ctx: Context, soundUriStr: String?, volumePct: Int) =
        playOneShot(ctx, resolveUriFrom(soundUriStr), volumePct.coerceIn(0, 100) / 100f, track = true, maxMs = 9000L) {}

    /** Stop any in-progress preview (call when leaving Settings). */
    fun stopPreview() {
        val p = preview ?: return
        preview = null
        try { p.stop() } catch (_: Exception) {}
        try { p.release() } catch (_: Exception) {}
    }

    /** Looping playback (used for the "stop" alarm inside SnackActivity). Call [stopLooping] to end. */
    fun startLooping(ctx: Context) {
        stopLooping()
        val vol = volumeScalar(ctx)
        val uri = resolveUri(ctx)
        if (vol <= 0f || uri == null) return
        try {
            looper = MediaPlayer().apply {
                setAudioAttributes(alarmAttrs())
                setDataSource(ctx.applicationContext, uri)
                isLooping = true
                setVolume(vol, vol)
                setOnPreparedListener { runCatching { start() } }
                setOnErrorListener { _, _, _ -> stopLooping(); true }
                prepareAsync()
            }
        } catch (e: Exception) {
            looper = null
        }
    }

    fun stopLooping() {
        val p = looper ?: return
        looper = null
        try { p.stop() } catch (_: Exception) {}
        try { p.release() } catch (_: Exception) {}
    }
}
