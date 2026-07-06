package com.mikefle.exercisesnacks

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class SnackActivity : AppCompatActivity() {

    private lateinit var tvCountdown: TextView
    private lateinit var tvSnackTitle: TextView
    private lateinit var layoutCountdown: LinearLayout
    private lateinit var layoutLog: LinearLayout
    private lateinit var chipGroup: ChipGroup
    private lateinit var etReps: EditText

    private var timer: CountDownTimer? = null
    private var otherChipId: Int = View.NO_ID
    private var otherText: String? = null
    private var totalSec: Int = 120
    private var snackStartMs: Long = 0L      // elapsedRealtime when the snack countdown began
    private var elapsedSec: Int = 0          // actual seconds exercised, captured at stop

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make sure the screen turns on / shows over the lock screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_snack)

        // Clear the reminder notification that opened us, and silence its ring immediately.
        NotificationManagerCompat.from(this).cancel(ReminderReceiver.NOTIF_ID)
        AlarmPlayer.stopLooping()   // silence the foreground-service ring now (not async)
        AlarmPlayer.stopRinging()   // silence the fallback ring (no-op if inactive)
        AlarmService.stop(this)

        tvCountdown = findViewById(R.id.tvCountdown)
        tvSnackTitle = findViewById(R.id.tvSnackTitle)
        layoutCountdown = findViewById(R.id.layoutCountdown)
        layoutLog = findViewById(R.id.layoutLog)
        chipGroup = findViewById(R.id.chipGroupExercises)
        etReps = findViewById(R.id.etReps)

        findViewById<Button>(R.id.btnStopEarly).setOnClickListener { finishSnack() }
        findViewById<Button>(R.id.btnLogDone).setOnClickListener { save(true) }
        findViewById<Button>(R.id.btnLogSkip).setOnClickListener { promptSkipReason() }

        totalSec = Prefs.durationSec(this).coerceAtLeast(5)
        buildChips()
        startCountdown()
    }

    /**
     * A new reminder can arrive while this screen is still on top (singleTop). Restart the snack
     * from scratch so per-snack state (start time, elapsed, selection) never carries over.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        timer?.cancel()
        timer = null
        stopAlarm()
        AlarmPlayer.stopRinging()
        AlarmService.stop(this)
        NotificationManagerCompat.from(this).cancel(ReminderReceiver.NOTIF_ID)
        elapsedSec = 0
        otherText = null
        etReps.setText("")
        totalSec = Prefs.durationSec(this).coerceAtLeast(5)
        chipGroup.removeAllViews()
        buildChips()
        startCountdown()
    }

    private fun startCountdown() {
        layoutCountdown.visibility = View.VISIBLE
        layoutLog.visibility = View.GONE
        tvSnackTitle.text = "Exercise snack — go!"
        snackStartMs = SystemClock.elapsedRealtime()
        timer = object : CountDownTimer(totalSec * 1000L, 250L) {
            override fun onTick(msLeft: Long) {
                val s = ((msLeft + 500) / 1000).toInt()
                tvCountdown.text = String.format("%d:%02d", s / 60, s % 60)
            }
            override fun onFinish() {
                tvCountdown.text = "0:00"
                finishSnack()
            }
        }.start()
    }

    /** Time's up (or stopped early): record elapsed time, sound the stop alarm, show the log prompt. */
    private fun finishSnack() {
        timer?.cancel()
        timer = null
        elapsedSec = if (snackStartMs > 0L) {
            ((SystemClock.elapsedRealtime() - snackStartMs) / 1000L).toInt().coerceIn(0, totalSec)
        } else totalSec
        playStopAlarm()
        vibrate()
        layoutCountdown.visibility = View.GONE
        layoutLog.visibility = View.VISIBLE
    }

    private fun buildChips() {
        chipGroup.isSingleSelection = true
        for (ex in Prefs.getExercises(this)) {
            val chip = Chip(this).apply {
                text = ex
                isCheckable = true
                id = View.generateViewId()
            }
            chipGroup.addView(chip)
        }
        val other = Chip(this).apply {
            text = "Other…"
            isCheckable = true
            id = View.generateViewId()
        }
        otherChipId = other.id
        chipGroup.addView(other)

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.contains(otherChipId)) promptOther()
        }
    }

    private fun promptOther() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(otherText ?: "")
        }
        AlertDialog.Builder(this)
            .setTitle("What did you do?")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                otherText = input.text.toString().trim()
                val chip = findViewById<Chip>(otherChipId)
                chip?.text = if (otherText.isNullOrBlank()) "Other…" else otherText
            }
            .setNegativeButton("Cancel") { _, _ ->
                chipGroup.clearCheck()
            }
            .show()
    }

    private fun promptSkipReason() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = "Reason (optional)"
        }
        AlertDialog.Builder(this)
            .setTitle("Skip this snack?")
            .setView(input)
            .setPositiveButton("Skip") { _, _ -> save(false, input.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun save(done: Boolean, note: String = "") {
        val id = chipGroup.checkedChipId
        val exercise = when {
            id == View.NO_ID -> "(unspecified)"
            id == otherChipId -> otherText?.takeIf { it.isNotBlank() } ?: "Other"
            else -> findViewById<Chip>(id)?.text?.toString() ?: "(unspecified)"
        }
        val reps = etReps.text.toString().trim().toIntOrNull()?.takeIf { it > 0 } ?: 0
        Prefs.addLog(this, exercise, done, if (done) elapsedSec else 0, if (done) reps else 0, note)
        stopAlarm()
        Toast.makeText(
            this,
            if (done) "Logged: $exercise ✅" else "Marked as skipped",
            Toast.LENGTH_SHORT
        ).show()
        finish()
    }

    private fun playStopAlarm() {
        // Ring the user's chosen sound/volume for the configured length (or until they log / leave).
        AlarmPlayer.startLooping(this, Prefs.alarmLenSec(this) * 1000L)
    }

    private fun stopAlarm() {
        AlarmPlayer.stopLooping()
    }

    private fun vibrate() {
        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 500, 250, 500)
            vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) { }
    }

    override fun onStop() {
        super.onStop()
        // Don't keep ringing in the background if the user navigates away without logging.
        stopAlarm()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        stopAlarm()
    }
}
