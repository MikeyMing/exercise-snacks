package com.mikefle.exercisesnacks

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // Always line up the next alarm first so the chain never breaks.
        AlarmScheduler.scheduleNext(context)

        if (!Prefs.isEnabled(context)) return
        if (!withinWindow(context)) return

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
            // POST_NOTIFICATIONS not granted yet; nothing else to do.
        }
    }

    private fun withinWindow(ctx: Context): Boolean {
        val now = Calendar.getInstance()
        val minOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return minOfDay >= Prefs.startMin(ctx) && minOfDay <= Prefs.endMin(ctx)
    }

    private fun formatDur(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return if (m > 0 && s == 0) "${m}-min" else if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    companion object {
        const val CHANNEL_ID = "exercise_snack_reminders"
        const val NOTIF_ID = 42

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID, "Exercise snack reminders", NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Nudges you to do a quick exercise snack"
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            channel.setSound(sound, attrs)
            channel.enableVibration(true)
            channel.vibrationPattern = longArrayOf(0, 400, 200, 400)
            nm.createNotificationChannel(channel)
        }
    }
}
