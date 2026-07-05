package com.mikefle.exercisesnacks

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A persistent ("ongoing") status-bar notification that shows when the next snack is due.
 * Uses the notification's built-in count-down chronometer, so the shade ticks down on its
 * own with no foreground service. Kept in sync by AlarmScheduler (update on schedule,
 * clear on cancel).
 */
object StatusNotification {

    private const val CHANNEL_ID = "exercise_snack_status"
    private const val NOTIF_ID = 43

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID, "Next-snack status", NotificationManager.IMPORTANCE_LOW
        )
        ch.description = "A quiet ongoing notification showing when your next snack is due"
        ch.setShowBadge(false)
        ch.setSound(null, null)
        ch.enableVibration(false)
        nm.createNotificationChannel(ch)
    }

    /** Show/refresh the ongoing notification, or clear it if it shouldn't be shown. */
    fun update(ctx: Context) {
        if (!Prefs.isEnabled(ctx) || !Prefs.showStatus(ctx)) { clear(ctx); return }
        ensureChannel(ctx)

        val open = PendingIntent.getActivity(
            ctx, 2002,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val b = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_snack)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open)

        val next = AlarmScheduler.nextTriggerTime(ctx)
        if (next > 0L) {
            val df = SimpleDateFormat("EEE h:mm a", Locale.getDefault())
            b.setContentTitle("Next snack at ${df.format(Date(next))}")
            b.setContentText("Snacks done today: ${Prefs.doneToday(ctx)}")
            b.setShowWhen(true)
            b.setWhen(next)
            b.setUsesChronometer(true)
            b.setChronometerCountDown(true)   // shade counts down to `next` on its own
        } else {
            b.setContentTitle("Exercise Snacks")
            b.setContentText("Reminders on, but no active hours are set")
            b.setShowWhen(false)
        }

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, b.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted yet; it'll appear once permission is granted.
        }
    }

    fun clear(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
    }
}
