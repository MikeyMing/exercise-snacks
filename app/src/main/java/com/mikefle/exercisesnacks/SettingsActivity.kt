package com.mikefle.exercisesnacks

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var etInterval: EditText
    private lateinit var etDuration: EditText
    private lateinit var btnStartTime: Button
    private lateinit var btnEndTime: Button
    private lateinit var llExercises: LinearLayout
    private lateinit var etNewExercise: EditText

    private var startMin = 7 * 60
    private var endMin = 21 * 60
    private val exercises = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etInterval = findViewById(R.id.etInterval)
        etDuration = findViewById(R.id.etDuration)
        btnStartTime = findViewById(R.id.btnStartTime)
        btnEndTime = findViewById(R.id.btnEndTime)
        llExercises = findViewById(R.id.llExercises)
        etNewExercise = findViewById(R.id.etNewExercise)

        etInterval.setText(Prefs.intervalMin(this).toString())
        etDuration.setText(Prefs.durationSec(this).toString())
        startMin = Prefs.startMin(this)
        endMin = Prefs.endMin(this)
        updateTimeLabels()

        exercises.addAll(Prefs.getExercises(this))
        renderExercises()

        btnStartTime.setOnClickListener { pickTime(true) }
        btnEndTime.setOnClickListener { pickTime(false) }

        findViewById<Button>(R.id.btnAddExercise).setOnClickListener {
            val name = etNewExercise.text.toString().trim()
            if (name.isNotEmpty()) {
                exercises.add(name)
                etNewExercise.setText("")
                renderExercises()
            }
        }

        findViewById<Button>(R.id.btnSaveSettings).setOnClickListener { save() }
    }

    private fun pickTime(isStart: Boolean) {
        val cur = if (isStart) startMin else endMin
        TimePickerDialog(this, { _, h, m ->
            if (isStart) startMin = h * 60 + m else endMin = h * 60 + m
            updateTimeLabels()
        }, cur / 60, cur % 60, false).show()
    }

    private fun updateTimeLabels() {
        btnStartTime.text = "Start: ${fmt(startMin)}"
        btnEndTime.text = "End: ${fmt(endMin)}"
    }

    private fun fmt(minOfDay: Int): String {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, minOfDay / 60)
        c.set(Calendar.MINUTE, minOfDay % 60)
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(c.time)
    }

    private fun renderExercises() {
        llExercises.removeAllViews()
        val pad = (8 * resources.displayMetrics.density).toInt()
        for ((idx, name) in exercises.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val label = TextView(this).apply {
                text = name
                textSize = 16f
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val remove = Button(this).apply {
                text = "Remove"
                setOnClickListener {
                    if (idx < exercises.size) { exercises.removeAt(idx); renderExercises() }
                }
            }
            row.addView(label)
            row.addView(remove)
            llExercises.addView(row)
        }
    }

    private fun save() {
        val interval = etInterval.text.toString().toIntOrNull()
        val duration = etDuration.text.toString().toIntOrNull()
        if (interval == null || interval < 1) {
            Toast.makeText(this, "Interval must be at least 1 minute", Toast.LENGTH_SHORT).show(); return
        }
        if (duration == null || duration < 5) {
            Toast.makeText(this, "Snack length must be at least 5 seconds", Toast.LENGTH_SHORT).show(); return
        }
        if (startMin >= endMin) {
            Toast.makeText(this, "Start time must be before end time", Toast.LENGTH_SHORT).show(); return
        }
        if (exercises.isEmpty()) {
            Toast.makeText(this, "Add at least one exercise", Toast.LENGTH_SHORT).show(); return
        }
        Prefs.setIntervalMin(this, interval)
        Prefs.setDurationSec(this, duration)
        Prefs.setStartMin(this, startMin)
        Prefs.setEndMin(this, endMin)
        Prefs.setExercises(this, exercises)
        if (Prefs.isEnabled(this)) AlarmScheduler.scheduleNext(this)
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
