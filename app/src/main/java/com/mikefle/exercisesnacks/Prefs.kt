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
    private const val K_GRID = "active_grid_v1"
    private const val K_SOUND = "sound_uri"
    private const val K_VOLUME = "volume_pct"
    private const val K_SHOW_STATUS = "show_status_notif"

    /** Active-hours grid dimensions. Day index 0=Mon .. 6=Sun; hour 0..23. */
    const val GRID_DAYS = 7
    const val GRID_HOURS = 24

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

    // ---- alarm sound & volume ----
    /** Ringtone URI string, [AlarmPlayer.SILENT], or null for the system default. */
    fun soundUri(ctx: Context): String? = sp(ctx).getString(K_SOUND, null)
    fun setSoundUri(ctx: Context, uri: String?) {
        val e = sp(ctx).edit()
        if (uri == null) e.remove(K_SOUND) else e.putString(K_SOUND, uri)
        e.apply()
    }

    fun volumePct(ctx: Context) = sp(ctx).getInt(K_VOLUME, 100).coerceIn(0, 100)
    fun setVolumePct(ctx: Context, v: Int) =
        sp(ctx).edit().putInt(K_VOLUME, v.coerceIn(0, 100)).apply()

    /** Whether to show the ongoing status-bar notification with the next-snack countdown. */
    fun showStatus(ctx: Context) = sp(ctx).getBoolean(K_SHOW_STATUS, true)
    fun setShowStatus(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_SHOW_STATUS, v).apply()

    // ---- active-hours grid (7 days x 24 hours) ----
    /**
     * Returns the 168-cell active grid (index = dayIdx * 24 + hour, dayIdx 0=Mon..6=Sun).
     * If none is stored yet, migrates from the legacy start/end window (same window every day).
     */
    fun getGrid(ctx: Context): BooleanArray {
        val raw = sp(ctx).getString(K_GRID, null)
        if (raw != null && raw.length == GRID_DAYS * GRID_HOURS) {
            return BooleanArray(raw.length) { raw[it] == '1' }
        }
        val startH = (startMin(ctx) / 60).coerceIn(0, 23)
        val endH = ((endMin(ctx) - 1) / 60).coerceIn(0, 23)
        val g = BooleanArray(GRID_DAYS * GRID_HOURS)
        for (d in 0 until GRID_DAYS) for (h in startH..endH) g[d * GRID_HOURS + h] = true
        setGrid(ctx, g)
        return g
    }

    fun setGrid(ctx: Context, grid: BooleanArray) {
        val sb = StringBuilder(grid.size)
        for (b in grid) sb.append(if (b) '1' else '0')
        sp(ctx).edit().putString(K_GRID, sb.toString()).apply()
    }

    /** Is the reminder currently allowed to fire, per the active-hours grid? */
    fun isActiveNow(ctx: Context): Boolean = isActiveAt(getGrid(ctx), System.currentTimeMillis())

    fun isActiveAt(grid: BooleanArray, timeMillis: Long): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val dayIdx = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7   // Mon=0 .. Sun=6
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val i = dayIdx * GRID_HOURS + hour
        return i in grid.indices && grid[i]
    }

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
