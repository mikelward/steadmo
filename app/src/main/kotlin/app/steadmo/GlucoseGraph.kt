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
import app.steadmo.core.GlucoseUnit
import app.steadmo.core.value
import app.steadmo.core.Reading

private const val WINDOW_MILLIS = 3 * 60 * 60_000L

/** The last three hours, with the 3.9–10 target band. */
@Composable
fun GlucoseGraph(
    readings: List<Reading>,
    now: Long,
    modifier: Modifier = Modifier,
    unit: GlucoseUnit = GlucoseUnit.MMOL,
    treatments: List<Long> = emptyList(),
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelPx = with(LocalDensity.current) { 11.sp.toPx() }
    val shown = readings.filter { now - it.timeMillis <= WINDOW_MILLIS }
    val top = maxOf(14.0, (shown.maxOfOrNull { it.mmol } ?: 0.0) + 1.0)
    val bottom = 2.0

    Canvas(modifier.semantics { contentDescription = "Glucose over the last 3 hours" }) {
        val left = labelPx * unit.gutter()
        val width = size.width - left
        fun x(t: Long) = left + width * (1f - (now - t).toFloat() / WINDOW_MILLIS)
        // Room above the top line for its label, even at large font sizes.
        val pad = labelPx
        fun y(v: Double) = (pad + (size.height - pad) * (1 - (v - bottom) / (top - bottom))).toFloat()

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
                canvas.nativeCanvas.drawText(unit.axis(v), 0f, y(v.toDouble()) + labelPx / 3, paint)
            }
        }
        // "Treated": a low-coloured line up from the bottom (SPEC.md §4.10).
        treatments.filter { now - it in 0..WINDOW_MILLIS }.forEach { t ->
            drawLine(LowColor, Offset(x(t), size.height), Offset(x(t), size.height * 0.8f), strokeWidth = 6f)
        }
        shown.forEachIndexed { i, r ->
            val last = i == shown.lastIndex
            drawCircle(rangeColor(r.mmol), radius = if (last) 7f else 4.5f, center = Offset(x(r.timeMillis), y(r.mmol)))
        }
    }
}

/** An axis label at [mmol] (4, 10 or 14): "4" in mmol/L, "72" in mg/dL. */
internal fun GlucoseUnit.axis(mmol: Int): String = value(mmol.toDouble()).removeSuffix(".0")

/** Label width, in text heights, left of a graph: wider for three-digit mg/dL. */
internal fun GlucoseUnit.gutter(): Float = if (this == GlucoseUnit.MGDL) 2.6f else 2f
