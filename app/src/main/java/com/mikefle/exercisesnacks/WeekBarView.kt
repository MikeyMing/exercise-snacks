package com.mikefle.exercisesnacks

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

/**
 * A tiny self-contained bar chart of exercise minutes for the last 7 days.
 * No external chart library — just Canvas drawing. Feed it via [setData].
 */
class WeekBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var valuesSec = IntArray(7)
    private var labels = Array(7) { "" }

    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E7D32") }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E4E4E4") }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555"); textAlign = Paint.Align.CENTER; textSize = 11f * density
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32"); textAlign = Paint.Align.CENTER
        textSize = 11f * density; isFakeBoldText = true
    }

    /** [values] = seconds per day (index 0 = 6 days ago … 6 = today); [labels] = short day names. */
    fun setData(values: IntArray, labels: Array<String>) {
        if (values.size == 7) valuesSec = values
        if (labels.size == 7) this.labels = labels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = valuesSec.size
        if (n == 0 || width == 0 || height == 0) return

        val labelBand = 18f * density        // space at the bottom for day labels
        val valueBand = 16f * density        // space at the top for the minute figure
        val radius = 5f * density
        val w = width.toFloat()
        val chartTop = paddingTop + valueBand
        val chartBottom = height - paddingBottom - labelBand
        val chartH = (chartBottom - chartTop).coerceAtLeast(1f)

        val slot = w / n
        val barW = slot * 0.5f
        val maxSec = (valuesSec.maxOrNull() ?: 0).coerceAtLeast(1)

        for (i in 0 until n) {
            val cx = slot * i + slot / 2
            val left = cx - barW / 2
            val right = cx + barW / 2

            // faint full-height track
            canvas.drawRoundRect(left, chartTop, right, chartBottom, radius, radius, trackPaint)

            val sec = valuesSec[i]
            if (sec > 0) {
                val barH = chartH * (sec.toFloat() / maxSec)
                canvas.drawRoundRect(left, chartBottom - barH, right, chartBottom, radius, radius, barPaint)
                val mins = (sec / 60f).roundToInt()
                if (mins > 0) {
                    canvas.drawText(mins.toString(), cx, chartTop - 4f * density, valuePaint)
                }
            }

            // day label under the bar
            canvas.drawText(labels[i], cx, height - paddingBottom - 3f * density, labelPaint)
        }
    }
}
