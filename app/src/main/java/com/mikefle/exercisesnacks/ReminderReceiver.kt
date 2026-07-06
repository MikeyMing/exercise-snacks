package com.mikefle.exercisesnacks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // Always line up the next alarm first so the chain never breaks.
        AlarmScheduler.scheduleNext(context)

        if (!Prefs.isEnabled(context)) return
        if (!Prefs.isActiveNow(context)) return

        // Preferred path: a foreground service so the alarm can ring for the full configured
        // length (beyond the ~10s a BroadcastReceiver is allowed to run). Allowed to start from
        // the background here because the app holds USE_EXACT_ALARM (alarm-triggered exemption).
        val startedService = try {
            ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java))
            true
        } catch (e: Exception) {
            false
        }
        if (startedService) return

        // Fallback (FGS not permitted on this device/state): post the notification and ring
        // briefly via goAsync (capped at 10s to stay within the receiver's lifetime).
        ensureChannel(context)
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, buildReminderNotification(context))
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted yet; the alarm sound below still plays.
        }
        val lenMs = Prefs.alarmLenSec(context).coerceAtMost(10) * 1000L
        val result = goAsync()
        AlarmPlayer.playRinging(context, lenMs) { result.finish() }
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

        /** The reminder notification, shared by the foreground service and the fallback path. */
        fun buildReminderNotification(ctx: Context): Notification {
            ensureChannel(ctx)

            val openIntent = Intent(ctx, SnackActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val contentPending = PendingIntent.getActivity(
                ctx, 1001, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // "Skip" action with an inline optional excuse (RemoteInput needs a MUTABLE PendingIntent).
            val remoteInput = RemoteInput.Builder(SnackActionReceiver.KEY_EXCUSE)
                .setLabel("Reason (optional)")
                .build()
            val skipIntent = Intent(ctx, SnackActionReceiver::class.java)
                .setAction(SnackActionReceiver.ACTION_SKIP)
            val skipFlags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                else PendingIntent.FLAG_UPDATE_CURRENT
            val skipPending = PendingIntent.getBroadcast(ctx, 1002, skipIntent, skipFlags)
            val skipAction = NotificationCompat.Action.Builder(0, "Skip", skipPending)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(false)
                .build()

            val durSec = Prefs.durationSec(ctx)
            return NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_snack)
                .setContentTitle("Time for an exercise snack!")
                .setContentText("Tap to start your ${formatDur(durSec)} snack")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setFullScreenIntent(contentPending, true)
                .setContentIntent(contentPending)
                .addAction(skipAction)
                .build()
        }

        private fun formatDur(sec: Int): String {
            val m = sec / 60
            val s = sec % 60
            return if (m > 0 && s == 0) "${m}-min" else if (m > 0) "${m}m ${s}s" else "${s}s"
        }
    }
}
