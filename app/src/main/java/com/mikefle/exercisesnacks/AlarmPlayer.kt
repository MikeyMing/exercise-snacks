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
    private var loopTimeout: Runnable? = null
    private var preview: MediaPlayer? = null   // one-shot Settings preview (stoppable)
    private var ringing: MediaPlayer? = null   // reminder ring (looped for a fixed length)
    private var ringFinish: (() -> Unit)? = null

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

    // ---------- one-shot preview (Settings "Test") ----------

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
        } catch (e: Exception) { onDone(); return }

        if (track) { stopPreview(); preview = player }
        var finished = false
        var timeout: Runnable? = null
        val finish = {
            if (!finished) {
                finished = true
                timeout?.let { handler.removeCallbacks(it) }
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
            handler.postDelayed(cap, maxMs)
        } catch (e: Exception) {
            if (track && preview === player) preview = null
            try { player.release() } catch (_: Exception) {}
            onDone()
        }
    }

    /** Preview arbitrary (possibly unsaved) settings once — used by the Settings "Test" button. */
    fun preview(ctx: Context, soundUriStr: String?, volumePct: Int) =
        playOneShot(ctx, resolveUriFrom(soundUriStr), volumePct.coerceIn(0, 100) / 100f, track = true, maxMs = 9000L) {}

    fun stopPreview() {
        val p = preview ?: return
        preview = null
        try { p.stop() } catch (_: Exception) {}
        try { p.release() } catch (_: Exception) {}
    }

    // ---------- reminder ring (looped for a fixed length; used from the BroadcastReceiver) ----------

    /**
     * Loop the alarm sound for [maxMs] ms, then stop. [onDone] is ALWAYS invoked exactly once
     * (at the end, on error, or if it can't start) so a goAsync() result can be balanced.
     * [stopRinging] ends it early (e.g. the user skips or opens the snack).
     */
    fun playRinging(ctx: Context, maxMs: Long, onDone: () -> Unit) {
        stopRinging()
        val vol = volumeScalar(ctx)
        val uri = resolveUri(ctx)
        if (vol <= 0f || uri == null) { onDone(); return }
        val player: MediaPlayer
        try {
            player = MediaPlayer()
            player.setAudioAttributes(alarmAttrs())
            player.setDataSource(ctx.applicationContext, uri)
            player.isLooping = true
            player.setVolume(vol, vol)
        } catch (e: Exception) { onDone(); return }

        ringing = player
        var finished = false
        var timeout: Runnable? = null
        val finish = {
            if (!finished) {
                finished = true
                timeout?.let { handler.removeCallbacks(it) }
                if (ringing === player) ringing = null
                ringFinish = null
                try { player.stop() } catch (_: Exception) {}
                try { player.release() } catch (_: Exception) {}
                onDone()
            }
        }
        ringFinish = finish
        val cap = Runnable { finish() }
        timeout = cap
        player.setOnPreparedListener { runCatching { it.start() } }
        player.setOnErrorListener { _, _, _ -> finish(); true }
        try {
            player.prepareAsync()
            handler.postDelayed(cap, maxMs)
        } catch (e: Exception) {
            if (ringing === player) ringing = null
            ringFinish = null
            try { player.release() } catch (_: Exception) {}
            onDone()
        }
    }

    fun stopRinging() {
        ringFinish?.invoke()
    }

    // ---------- looping "stop" alarm (SnackActivity) ----------

    /** Loop until [stopLooping]; if [maxMs] > 0, auto-stops after that many ms. */
    fun startLooping(ctx: Context, maxMs: Long = 0L) {
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
            return
        }
        if (maxMs > 0L) {
            val t = Runnable { stopLooping() }
            loopTimeout = t
            handler.postDelayed(t, maxMs)
        }
    }

    fun stopLooping() {
        loopTimeout?.let { handler.removeCallbacks(it) }
        loopTimeout = null
        val p = looper ?: return
        looper = null
        try { p.stop() } catch (_: Exception) {}
        try { p.release() } catch (_: Exception) {}
    }
}
