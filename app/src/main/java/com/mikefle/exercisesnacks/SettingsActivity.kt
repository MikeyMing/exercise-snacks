package com.mikefle.exercisesnacks

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var etInterval: EditText
    private lateinit var etDuration: EditText
    private lateinit var gridHost: LinearLayout
    private lateinit var btnSound: Button
    private lateinit var sbVolume: SeekBar
    private lateinit var tvVolumeValue: TextView
    private lateinit var llExercises: LinearLayout
    private lateinit var etNewExercise: EditText

    private val exercises = ArrayList<String>()

    // Active-hours grid: index = dayIdx * 24 + hour (dayIdx 0=Mon..6=Sun).
    private val grid = BooleanArray(Prefs.GRID_DAYS * Prefs.GRID_HOURS)
    private val cells = arrayOfNulls<TextView>(Prefs.GRID_DAYS * Prefs.GRID_HOURS)
    private var activeColor = 0
    private val inactiveColor = Color.parseColor("#E4E4E4")
    private val labelColor = Color.parseColor("#555555")

    // Sound selection: null = system default, AlarmPlayer.SILENT, or a ringtone uri string.
    private var soundSel: String? = null

    private val soundPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK) {
                val picked: Uri? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        res.data?.getParcelableExtra(
                            RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                    }
                soundSel = if (picked == null) AlarmPlayer.SILENT else picked.toString()
                updateSoundLabel()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        activeColor = ContextCompat.getColor(this, R.color.green_700)

        etInterval = findViewById(R.id.etInterval)
        etDuration = findViewById(R.id.etDuration)
        gridHost = findViewById(R.id.gridHost)
        btnSound = findViewById(R.id.btnSound)
        sbVolume = findViewById(R.id.sbVolume)
        tvVolumeValue = findViewById(R.id.tvVolumeValue)
        llExercises = findViewById(R.id.llExercises)
        etNewExercise = findViewById(R.id.etNewExercise)

        etInterval.setText(Prefs.intervalMin(this).toString())
        etDuration.setText(Prefs.durationSec(this).toString())

        Prefs.getGrid(this).copyInto(grid)
        buildGrid()

        findViewById<Button>(R.id.btnGridAll).setOnClickListener { setAll(true) }
        findViewById<Button>(R.id.btnGridNone).setOnClickListener { setAll(false) }
        findViewById<Button>(R.id.btnGridWaking).setOnClickListener { applyWaking() }

        soundSel = Prefs.soundUri(this)
        updateSoundLabel()
        btnSound.setOnClickListener { pickSound() }
        findViewById<Button>(R.id.btnTestSound).setOnClickListener {
            AlarmPlayer.preview(this, soundSel, sbVolume.progress)
        }

        sbVolume.progress = Prefs.volumePct(this)
        tvVolumeValue.text = "${sbVolume.progress}%"
        sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                tvVolumeValue.text = "$p%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        exercises.addAll(Prefs.getExercises(this))
        renderExercises()

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

    override fun onStop() {
        super.onStop()
        AlarmPlayer.stopPreview()   // don't keep a preview ringing after leaving Settings
    }

    // ---------- active-hours grid ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildGrid() {
        gridHost.removeAllViews()
        val cellW = dp(40); val cellH = dp(30); val labW = dp(30); val m = dp(2)
        val dayNames = arrayOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

        val header = rowLayout()
        header.addView(labelCell("", labW, cellH, m, null))
        for (d in 0 until Prefs.GRID_DAYS) {
            header.addView(labelCell(dayNames[d], cellW, cellH, m) { toggleColumn(d) })
        }
        gridHost.addView(header)

        for (hour in 0 until Prefs.GRID_HOURS) {
            val row = rowLayout()
            row.addView(labelCell(String.format(Locale.US, "%02d", hour), labW, cellH, m) { toggleRow(hour) })
            for (d in 0 until Prefs.GRID_DAYS) {
                val idx = d * Prefs.GRID_HOURS + hour
                val cell = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(cellW, cellH).also { it.setMargins(m, m, m, m) }
                    isClickable = true
                    setOnClickListener { grid[idx] = !grid[idx]; styleCell(idx) }
                }
                cells[idx] = cell
                styleCell(idx)
                row.addView(cell)
            }
            gridHost.addView(row)
        }
    }

    private fun rowLayout() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun labelCell(
        text: String, w: Int, h: Int, m: Int, onClick: (() -> Unit)? = null
    ): TextView = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(w, h).also { it.setMargins(m, m, m, m) }
        gravity = Gravity.CENTER
        textSize = 11f
        setTextColor(labelColor)
        this.text = text
        if (onClick != null) { isClickable = true; setOnClickListener { onClick() } }
    }

    private fun styleCell(idx: Int) {
        val cell = cells[idx] ?: return
        cell.setBackgroundColor(if (grid[idx]) activeColor else inactiveColor)
    }

    private fun refreshAll() { for (i in grid.indices) styleCell(i) }

    private fun toggleColumn(day: Int) {
        val base = day * Prefs.GRID_HOURS
        val allOn = (0 until Prefs.GRID_HOURS).all { grid[base + it] }
        for (h in 0 until Prefs.GRID_HOURS) grid[base + h] = !allOn
        refreshAll()
    }

    private fun toggleRow(hour: Int) {
        val allOn = (0 until Prefs.GRID_DAYS).all { grid[it * Prefs.GRID_HOURS + hour] }
        for (d in 0 until Prefs.GRID_DAYS) grid[d * Prefs.GRID_HOURS + hour] = !allOn
        refreshAll()
    }

    private fun setAll(on: Boolean) {
        for (i in grid.indices) grid[i] = on
        refreshAll()
    }

    private fun applyWaking() {
        for (d in 0 until Prefs.GRID_DAYS) for (h in 0 until Prefs.GRID_HOURS) {
            grid[d * Prefs.GRID_HOURS + h] = h in 7..20   // 7:00 AM – 8:59 PM
        }
        refreshAll()
    }

    // ---------- sound ----------

    private fun currentSoundUri(): Uri? = when (val s = soundSel) {
        null, AlarmPlayer.SILENT -> null
        else -> runCatching { Uri.parse(s) }.getOrNull()
    }

    private fun updateSoundLabel() {
        btnSound.text = when (val s = soundSel) {
            null -> "Default alarm sound"
            AlarmPlayer.SILENT -> "Silent (no sound)"
            else -> runCatching {
                RingtoneManager.getRingtone(this, Uri.parse(s))?.getTitle(this)
            }.getOrNull() ?: "Custom sound"
        }
    }

    private fun pickSound() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose alarm sound")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentSoundUri())
        }
        try {
            soundPicker.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No ringtone picker available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- exercises ----------

    private fun renderExercises() {
        llExercises.removeAllViews()
        val pad = dp(8)
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
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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

    // ---------- save ----------

    private fun save() {
        val interval = etInterval.text.toString().toIntOrNull()
        val duration = etDuration.text.toString().toIntOrNull()
        if (interval == null || interval < 1) {
            Toast.makeText(this, "Interval must be at least 1 minute", Toast.LENGTH_SHORT).show(); return
        }
        if (duration == null || duration < 5) {
            Toast.makeText(this, "Snack length must be at least 5 seconds", Toast.LENGTH_SHORT).show(); return
        }
        if (exercises.isEmpty()) {
            Toast.makeText(this, "Add at least one exercise", Toast.LENGTH_SHORT).show(); return
        }
        Prefs.setIntervalMin(this, interval)
        Prefs.setDurationSec(this, duration)
        Prefs.setGrid(this, grid)
        Prefs.setSoundUri(this, soundSel)
        Prefs.setVolumePct(this, sbVolume.progress)
        Prefs.setExercises(this, exercises)
        if (Prefs.isEnabled(this)) AlarmScheduler.scheduleNext(this)

        if (grid.none { it }) {
            Toast.makeText(this, "Saved — note: no active hours selected, so no reminders will fire", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
