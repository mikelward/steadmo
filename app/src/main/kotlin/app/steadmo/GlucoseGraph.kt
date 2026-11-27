package app.steadmo

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import app.steadmo.core.HIGH_MMOL
import app.steadmo.core.LOW_MMOL
import app.steadmo.core.Reading

private const val WINDOW_MILLIS = 3 * 60 * 60_000L

/** The last three hours, with the 3.9–10 target band. */
@Composable
fun GlucoseGraph(readings: List<Reading>, now: Long, modifier: Modifier = Modifier) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelPx = with(LocalDensity.current) { 11.sp.toPx() }
    val shown = readings.filter { now - it.timeMillis <= WINDOW_MILLIS }
    val top = maxOf(14.0, (shown.maxOfOrNull { it.mmol } ?: 0.0) + 1.0)
    val bottom = 2.0

    Canvas(modifier.semantics { contentDescription = "Glucose over the last 3 hours" }) {
        val left = labelPx * 2
        val width = size.width - left
        fun x(t: Long) = left + width * (1f - (now - t).toFloat() / WINDOW_MILLIS)
        fun y(v: Double) = (size.height * (1 - (v - bottom) / (top - bottom))).toFloat()

        drawRect(
            color = InRangeColor.copy(alpha = 0.12f),
            topLeft = Offset(left, y(HIGH_MMOL)),
            size = Size(width, y(LOW_MMOL) - y(HIGH_MMOL)),
        )
        val paint = Paint().apply { color = labelColor; textSize = labelPx; isAntiAlias = true }
        listOf(LOW_MMOL, HIGH_MMOL).forEach { v ->
            drawLine(gridColor, Offset(left, y(v)), Offset(size.width, y(v)), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        drawIntoCanvas { canvas ->
            listOf(4, 10, 14).forEach { v ->
                canvas.nativeCanvas.drawText(v.toString(), 0f, y(v.toDouble()) + labelPx / 3, paint)
            }
        }
        shown.forEachIndexed { i, r ->
            val last = i == shown.lastIndex
            drawCircle(rangeColor(r.mmol), radius = if (last) 7f else 4.5f, center = Offset(x(r.timeMillis), y(r.mmol)))
        }
    }
}
