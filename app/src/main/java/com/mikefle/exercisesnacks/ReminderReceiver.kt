package com.mikefle.exercisesnacks

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // Always line up the next alarm first so the chain never breaks.
        AlarmScheduler.scheduleNext(context)

        if (!Prefs.isEnabled(context)) return
        if (!Prefs.isActiveNow(context)) return

        ensureChannel(context)

        val openIntent = Intent(context, SnackActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context, 1001, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val durSec = Prefs.durationSec(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_snack)
            .setContentTitle("Time for an exercise snack!")
            .setContentText("Tap to start your ${formatDur(durSec)} snack")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted yet; the alarm sound below still plays.
        }

        // Play the user's chosen sound at the chosen volume. goAsync() keeps this
        // receiver alive until playback finishes (AlarmPlayer always calls back).
        val result = goAsync()
        AlarmPlayer.playOnce(context) { result.finish() }
    }

    private fun formatDur(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return if (m > 0 && s == 0) "${m}-min" else if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    companion object {
        // Sound is intentionally handled by AlarmPlayer (for volume control), so this
        // channel is SILENT. The "_v2" id supersedes the older noisy channel; a channel's
        // sound can't be changed after creation, so we retire the old id and delete it.
        const val CHANNEL_ID = "exercise_snack_reminders_v2"
        private const val LEGACY_CHANNEL_ID = "exercise_snack_reminders"
        const val NOTIF_ID = 42

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { nm.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID, "Exercise snack reminders", NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Nudges you to do a quick exercise snack"
            channel.setSound(null, null)           // silent: AlarmPlayer produces the sound
            channel.enableVibration(true)
            channel.vibrationPattern = longArrayOf(0, 400, 200, 400)
            nm.createNotificationChannel(channel)
        }
    }
}
