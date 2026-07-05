package com.mikefle.exercisesnacks

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvWarning: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var tvCountdownCaption: TextView
    private lateinit var switchEnabled: SwitchMaterial

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateCountdown()
            handler.postDelayed(this, 1000L)
        }
    }

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ReminderReceiver.ensureChannel(this)

        tvStatus = findViewById(R.id.tvStatus)
        tvWarning = findViewById(R.id.tvWarning)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvCountdownCaption = findViewById(R.id.tvCountdownCaption)
        switchEnabled = findViewById(R.id.switchEnabled)

        switchEnabled.isChecked = Prefs.isEnabled(this)
        switchEnabled.setOnCheckedChangeListener { _, checked ->
            Prefs.setEnabled(this, checked)
            if (checked) AlarmScheduler.scheduleNext(this) else AlarmScheduler.cancel(this)
            updateStatus()
        }

        findViewById<Button>(R.id.btnStartNow).setOnClickListener {
            startActivity(Intent(this, SnackActivity::class.java))
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.btnReliability).setOnClickListener { openReliabilitySettings() }

        maybeRequestNotifications()
        if (Prefs.isEnabled(this)) AlarmScheduler.scheduleNext(this)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        handler.post(ticker)          // live countdown ticks while the screen is visible
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun updateStatus() {
        val df = SimpleDateFormat("EEE h:mm a", Locale.getDefault())
        if (Prefs.isEnabled(this)) {
            val next = AlarmScheduler.nextTriggerTime(this)
            val nextStr = if (next > 0L) df.format(Date(next)) else "— (no active hours set)"
            tvStatus.text = "Next snack: $nextStr\n" +
                "Every ${Prefs.intervalMin(this)} min during your active hours\n" +
                "Snack length: ${Prefs.durationSec(this) / 60}m ${Prefs.durationSec(this) % 60}s\n\n" +
                "Snacks done today: ${Prefs.doneToday(this)}"
        } else {
            tvStatus.text = "Reminders are OFF.\n\nSnacks done today: ${Prefs.doneToday(this)}"
        }

        val warnings = ArrayList<String>()
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            warnings.add("• Notifications are disabled — you won't hear reminders. Turn them on in system settings.")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                warnings.add("• Exact alarms are blocked — reminders may be late. Tap \"Improve reliability\".")
            }
        }
        if (warnings.isEmpty()) {
            tvWarning.visibility = TextView.GONE
        } else {
            tvWarning.visibility = TextView.VISIBLE
            tvWarning.text = warnings.joinToString("\n")
        }
    }

    private fun updateCountdown() {
        if (!Prefs.isEnabled(this)) {
            tvCountdownCaption.text = "Reminders are off"
            tvCountdown.text = "—"
            return
        }
        val next = AlarmScheduler.nextTriggerTime(this)
        if (next <= 0L) {
            tvCountdownCaption.text = "No active hours set"
            tvCountdown.text = "—"
            return
        }
        val remMs = next - System.currentTimeMillis()
        if (remMs <= 0L) {
            tvCountdownCaption.text = "Next snack"
            tvCountdown.text = "now"
        } else {
            tvCountdownCaption.text = "Next snack in"
            tvCountdown.text = fmtRemaining(remMs)
        }
    }

    private fun fmtRemaining(ms: Long): String {
        val totalSec = (ms + 999) / 1000            // round up so it never shows 0:00 while >0
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        else String.format(Locale.getDefault(), "%d:%02d", m, s)
    }

    private fun openReliabilitySettings() {
        // First, exact-alarm access if needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    return
                } catch (_: Exception) { /* fall through */ }
            }
        }
        // Otherwise, offer to ignore battery optimisation.
        try {
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            i.data = Uri.parse("package:$packageName")
            startActivity(i)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
        }
    }
}
