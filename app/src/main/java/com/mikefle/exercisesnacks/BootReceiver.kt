package com.mikefle.exercisesnacks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arm the alarm after a reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Prefs.isEnabled(context)) {
            AlarmScheduler.scheduleNext(context)
        }
    }
}
