package com.mikefle.exercisesnacks

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
    private lateinit var switchEnabled: SwitchMaterial

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ReminderReceiver.ensureChannel(this)

        tvStatus = findViewById(R.id.tvStatus)
        tvWarning = findViewById(R.id.tvWarning)
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
            tvStatus.text = "Next snack: ${df.format(Date(next))}\n" +
                "Every ${Prefs.intervalMin(this)} min, ${fmtTime(Prefs.startMin(this))}–${fmtTime(Prefs.endMin(this))}\n" +
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

    private fun fmtTime(minOfDay: Int): String {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.HOUR_OF_DAY, minOfDay / 60)
        c.set(java.util.Calendar.MINUTE, minOfDay % 60)
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(c.time)
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
