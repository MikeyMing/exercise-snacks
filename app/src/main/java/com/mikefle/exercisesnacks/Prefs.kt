package com.mikefle.exercisesnacks

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * All persisted state: settings, the (editable) exercise list, and the log history.
 * Stored in SharedPreferences so we don't need a database dependency.
 */
object Prefs {
    private const val FILE = "exercise_snacks_prefs"

    private const val K_ENABLED = "enabled"
    private const val K_INTERVAL = "interval_min"
    private const val K_START = "start_min"
    private const val K_END = "end_min"
    private const val K_DURATION = "duration_sec"
    private const val K_EXERCISES = "exercises_json"
    private const val K_LOGS = "logs_json"

    private val DEFAULT_EXERCISES = listOf(
        "Squats", "Push-ups", "Lunges", "Jumping jacks", "Calf raises", "Plank"
    )

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- simple settings ----
    fun isEnabled(ctx: Context) = sp(ctx).getBoolean(K_ENABLED, true)
    fun setEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_ENABLED, v).apply()

    fun intervalMin(ctx: Context) = sp(ctx).getInt(K_INTERVAL, 30)
    fun setIntervalMin(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_INTERVAL, v).apply()

    fun startMin(ctx: Context) = sp(ctx).getInt(K_START, 7 * 60)      // 07:00
    fun setStartMin(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_START, v).apply()

    fun endMin(ctx: Context) = sp(ctx).getInt(K_END, 21 * 60)         // 21:00
    fun setEndMin(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_END, v).apply()

    fun durationSec(ctx: Context) = sp(ctx).getInt(K_DURATION, 120)   // 2 min
    fun setDurationSec(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_DURATION, v).apply()

    // ---- exercise list ----
    fun getExercises(ctx: Context): MutableList<String> {
        val raw = sp(ctx).getString(K_EXERCISES, null)
            ?: return DEFAULT_EXERCISES.toMutableList().also { setExercises(ctx, it) }
        val arr = JSONArray(raw)
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        return out
    }

    fun setExercises(ctx: Context, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        sp(ctx).edit().putString(K_EXERCISES, arr.toString()).apply()
    }

    // ---- log history ----
    fun addLog(ctx: Context, exercise: String, done: Boolean) {
        val arr = rawLogs(ctx)
        val obj = JSONObject()
        obj.put("ts", System.currentTimeMillis())
        obj.put("exercise", exercise)
        obj.put("done", done)
        arr.put(obj)
        sp(ctx).edit().putString(K_LOGS, arr.toString()).apply()
    }

    fun getLogs(ctx: Context): List<LogEntry> {
        val arr = rawLogs(ctx)
        val out = ArrayList<LogEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(LogEntry(o.getLong("ts"), o.optString("exercise", ""), o.optBoolean("done", true)))
        }
        out.sortByDescending { it.ts }
        return out
    }

    fun clearLogs(ctx: Context) = sp(ctx).edit().remove(K_LOGS).apply()

    fun doneToday(ctx: Context): Int {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return getLogs(ctx).count { it.done && it.ts >= startOfDay }
    }

    private fun rawLogs(ctx: Context): JSONArray {
        val raw = sp(ctx).getString(K_LOGS, null) ?: return JSONArray()
        return JSONArray(raw)
    }
}

data class LogEntry(val ts: Long, val exercise: String, val done: Boolean)
