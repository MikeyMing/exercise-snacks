package com.mikefle.exercisesnacks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput

/**
 * Handles the "Skip" action on the reminder notification, including the optional
 * typed excuse (delivered via RemoteInput). Logs the snack as skipped and silences
 * the alarm.
 */
class SnackActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != ACTION_SKIP) return

        val excuse = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_EXCUSE)?.toString()?.trim().orEmpty()

        AlarmPlayer.stopRinging()
        Prefs.addLog(context, "(skipped)", done = false, durationSec = 0, reps = 0, note = excuse)
        NotificationManagerCompat.from(context).cancel(ReminderReceiver.NOTIF_ID)

        val msg = if (excuse.isBlank()) "Snack skipped" else "Snack skipped: $excuse"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_SKIP = "com.mikefle.exercisesnacks.SKIP"
        const val KEY_EXCUSE = "excuse"
    }
}
