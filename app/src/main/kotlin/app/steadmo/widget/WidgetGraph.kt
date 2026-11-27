package app.steadmo.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import app.steadmo.core.HIGH_MMOL
import app.steadmo.core.LOW_MMOL
import app.steadmo.core.Reading

/** Glance has no canvas, so the widget's 3-hour graph is drawn to a bitmap. */
object WidgetGraph {
    private const val WINDOW_MILLIS = 3 * 60 * 60_000L

    data class Colors(val low: Int, val inRange: Int, val high: Int, val band: Int, val grid: Int, val label: Int)

    fun draw(readings: List<Reading>, now: Long, widthPx: Int, heightPx: Int, density: Float, colors: Colors): Bitmap {
        val bitmap = createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1))
        val canvas = Canvas(bitmap)
        val shown = readings.filter { now - it.timeMillis <= WINDOW_MILLIS }
        val top = maxOf(14.0, (shown.maxOfOrNull { it.mmol } ?: 0.0) + 1.0)
        val bottom = 2.0
        val labelPx = 10 * density
        val left = labelPx * 2
        val width = widthPx - left
        fun x(t: Long) = left + width * (1f - (now - t).toFloat() / WINDOW_MILLIS)
        // Room above the top line for its label.
        val pad = labelPx
        fun y(v: Double) = (pad + (heightPx - pad) * (1 - (v - bottom) / (top - bottom))).toFloat()

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = colors.band
        canvas.drawRect(left, y(HIGH_MMOL), widthPx.toFloat(), y(LOW_MMOL), fill)
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.grid
            strokeWidth = density
            pathEffect = DashPathEffect(floatArrayOf(4 * density, 4 * density), 0f)
        }
        listOf(LOW_MMOL, HIGH_MMOL).forEach { canvas.drawLine(left, y(it), widthPx.toFloat(), y(it), grid) }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.label; textSize = labelPx }
        listOf(4, 10, 14).forEach { canvas.drawText(it.toString(), 0f, y(it.toDouble()) + labelPx / 3, text) }
        shown.forEachIndexed { i, r ->
            fill.color = when {
                r.mmol < LOW_MMOL -> colors.low
                r.mmol > HIGH_MMOL -> colors.high
                else -> colors.inRange
            }
            canvas.drawCircle(x(r.timeMillis), y(r.mmol), (if (i == shown.lastIndex) 4f else 2.5f) * density, fill)
        }
        return bitmap
    }
}
