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

    /** Next time (epoch millis) the reminder should fire, honouring the active window. */
    fun nextTriggerTime(ctx: Context): Long {
        val interval = Prefs.intervalMin(ctx).coerceAtLeast(1)
        val startMin = Prefs.startMin(ctx)
        val endMin = Prefs.endMin(ctx)
        val now = Calendar.getInstance()

        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startMin / 60)
            set(Calendar.MINUTE, startMin % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // advance to the first slot strictly after "now"
        while (c.timeInMillis <= now.timeInMillis) {
            c.add(Calendar.MINUTE, interval)
        }
        val slotMinOfDay = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        if (slotMinOfDay > endMin || c.timeInMillis <= now.timeInMillis) {
            // past the window today -> jump to start time tomorrow
            c.timeInMillis = now.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
            c.set(Calendar.HOUR_OF_DAY, startMin / 60)
            c.set(Calendar.MINUTE, startMin % 60)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }

    fun scheduleNext(ctx: Context) {
        if (!Prefs.isEnabled(ctx)) { cancel(ctx); return }
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTriggerTime(ctx)
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

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
    }
}
