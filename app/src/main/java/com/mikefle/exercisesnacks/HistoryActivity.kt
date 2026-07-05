package com.mikefle.exercisesnacks

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        container = findViewById(R.id.llHistory)
        empty = findViewById(R.id.tvHistoryEmpty)

        findViewById<Button>(R.id.btnClearHistory).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear history?")
                .setMessage("This deletes all logged snacks.")
                .setPositiveButton("Clear") { _, _ -> Prefs.clearLogs(this); render() }
                .setNegativeButton("Cancel", null)
                .show()
        }
        render()
    }

    private fun render() {
        container.removeAllViews()
        val logs = Prefs.getLogs(this)
        empty.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        val df = SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault())
        val pad = (16 * resources.displayMetrics.density).toInt()
        for (e in logs) {
            val row = TextView(this).apply {
                text = buildString {
                    append(if (e.done) "✅" else "❌")
                    append("  ").append(df.format(Date(e.ts)))
                    append("  —  ").append(e.exercise)
                    if (e.reps > 0) append("  ×").append(e.reps)
                    if (e.done && e.durationSec > 0) {
                        append("  ·  ").append(e.durationSec / 60).append("m ").append(e.durationSec % 60).append("s")
                    }
                }
                textSize = 16f
                setPadding(pad, pad / 2, pad, pad / 2)
                gravity = Gravity.START
            }
            container.addView(row)
        }
    }
}
