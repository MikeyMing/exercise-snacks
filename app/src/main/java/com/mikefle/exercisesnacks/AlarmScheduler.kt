package com.mikefle.exercisesnacks

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Because exact repeating alarms are not allowed under Doze, we schedule ONE exact
 * alarm at a time. ReminderReceiver reschedules the next one each time it fires.
 */
object AlarmScheduler {
    private const val REQ = 2001

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction("com.mikefle.exercisesnacks.FIRE")
        return PendingIntent.getBroadcast(
            ctx, REQ, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Next time (epoch millis) the reminder should fire, or -1 if no active hours are set.
     *
     * Slots are anchored to midnight and spaced every `interval` minutes (e.g. 00:00, 00:30,
     * 01:00 …). A slot only fires if its (day-of-week, hour) cell is active in the grid, so
     * we walk forward slot-by-slot until we land in an active cell (searching up to 8 days).
     */
    fun nextTriggerTime(ctx: Context): Long {
        val interval = Prefs.intervalMin(ctx).coerceAtLeast(1)
        val grid = Prefs.getGrid(ctx)
        if (grid.none { it }) return -1L

        val now = System.currentTimeMillis()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val intervalMs = interval * 60_000L
        // first slot strictly after "now"
        var slot = midnight + ((now - midnight) / intervalMs + 1) * intervalMs
        val cap = now + 8L * 24 * 60 * 60 * 1000   // don't search forever if grid is sparse
        while (slot <= cap) {
            if (Prefs.isActiveAt(grid, slot)) return slot
            slot += intervalMs
        }
        return -1L
    }

    fun scheduleNext(ctx: Context) {
        if (!Prefs.isEnabled(ctx)) { cancel(ctx); return }
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTriggerTime(ctx)
        if (triggerAt <= 0L) {
            am.cancel(pending(ctx))   // no active hours -> drop any pending alarm
        } else {
            val pi = pending(ctx)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
        StatusNotification.update(ctx)   // keep the ongoing status notification in sync
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
        StatusNotification.clear(ctx)
    }
}
