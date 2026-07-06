package com.mikefle.exercisesnacks

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat

/**
 * Foreground service that rings the reminder alarm for the full configured length
 * (which can exceed the ~10s a BroadcastReceiver may run). Stops early when the user
 * opens the snack or skips it. Started from [ReminderReceiver]; degrades gracefully
 * if the OS refuses to let it go foreground.
 */
class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must go foreground promptly. Build the reminder notification (fall back to a minimal one).
        ReminderReceiver.ensureChannel(this)
        val notif = runCatching { ReminderReceiver.buildReminderNotification(this) }.getOrElse { minimalNotif() }

        try {
            ServiceCompat.startForeground(
                this, ReminderReceiver.NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } catch (e: Exception) {
            // Couldn't go foreground — degrade to a plain notification + short ring, then stop.
            runCatching { NotificationManagerCompat.from(this).notify(ReminderReceiver.NOTIF_ID, notif) }
            AlarmPlayer.playRinging(this, 9000L) {}
            stopSelf()
            return START_NOT_STICKY
        }

        val lenMs = Prefs.alarmLenSec(this) * 1000L
        AlarmPlayer.startLooping(this, lenMs)     // rings, auto-stops after lenMs
        val r = Runnable { stopCleanly() }        // on natural end, keep the notification (DETACH)
        stopRunnable = r
        handler.postDelayed(r, lenMs)
        return START_NOT_STICKY
    }

    private fun minimalNotif(): Notification =
        NotificationCompat.Builder(this, ReminderReceiver.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_snack)
            .setContentTitle("Time for an exercise snack!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

    // Natural end of the ring: keep the notification (DETACH) so the user can still act on it.
    // The user-action stop path goes through AlarmService.stop() -> stopService() -> onDestroy(),
    // where the framework removes the (still-attached) notification.
    private fun stopCleanly() {
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null
        AlarmPlayer.stopLooping()
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH) }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null
        AlarmPlayer.stopLooping()
    }

    companion object {
        /** Stop the ringing service if it's running (user opened the snack or skipped). No-op otherwise. */
        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, AlarmService::class.java)) }
        }
    }
}
