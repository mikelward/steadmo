package app.steadmo.core

import kotlin.math.abs

/** mg/dL per mmol/L. */
const val MGDL_PER_MMOL = 18.0182

/** A sensor limit CamAPS FX shows in place of a value, e.g. "<2.2". */
enum class Bound(val symbol: String) { BELOW("<"), ABOVE(">") }

/**
 * One CGM value. [mmol] is in mmol/L whatever unit the source shows. With a
 * [bound], [mmol] is the sensor's limit, not the glucose: it is shown with
 * its sign and left out of the trend (SPEC.md §3).
 */
data class Reading(val timeMillis: Long, val mmol: Double, val bound: Bound? = null) {
    /** e.g. "8.9" or "<2.2". */
    val display: String get() = (bound?.symbol ?: "") + String.format(java.util.Locale.US, "%.1f", mmol)
}

/**
 * The seven CGM trend arrows, using the thresholds Dexcom and most pumps use
 * (SPEC.md §4): flat below 1 mg/dL/min, slight (45°) below 2, single below 3,
 * double from 3. In mmol/L that is 0.056, 0.11 and 0.17 per minute, or a
 * 30-minute change of 1.7, 3.3 and 5.0.
 */
enum class TrendArrow(val label: String) {
    DOUBLE_DOWN("Falling fast"),
    DOWN("Falling"),
    DOWN_SLIGHT("Falling slowly"),
    FLAT("Steady"),
    UP_SLIGHT("Rising slowly"),
    UP("Rising"),
    DOUBLE_UP("Rising fast"),
    ;

    companion object {
        fun forRate(mmolPerMinute: Double): TrendArrow {
            val mgdl = mmolPerMinute * MGDL_PER_MMOL
            val magnitude = abs(mgdl)
            val rising = mgdl > 0
            return when {
                magnitude < 1.0 -> FLAT
                magnitude < 2.0 -> if (rising) UP_SLIGHT else DOWN_SLIGHT
                magnitude < 3.0 -> if (rising) UP else DOWN
                else -> if (rising) DOUBLE_UP else DOUBLE_DOWN
            }
        }
    }
}

data class Trend(val mmolPerMinute: Double, val arrow: TrendArrow)

object TrendCalculator {
    /** How far back from the latest reading the fit looks. */
    const val WINDOW_MILLIS = 20 * 60_000L

    /** The fit needs readings spanning at least this long to mean anything. */
    const val MIN_SPAN_MILLIS = 5 * 60_000L

    /**
     * The least-squares slope of the readings in the [WINDOW_MILLIS] before the
     * latest one, or null when there are too few to say. A bounded latest
     * reading has no trend: its real value is unknown.
     */
    fun trend(readings: List<Reading>): Trend? {
        val latest = readings.maxByOrNull { it.timeMillis } ?: return null
        if (latest.bound != null) return null
        val window = readings.filter { it.bound == null && latest.timeMillis - it.timeMillis in 0..WINDOW_MILLIS }
        if (window.size < 2) return null
        val start = window.minOf { it.timeMillis }
        if (latest.timeMillis - start < MIN_SPAN_MILLIS) return null

        val xs = window.map { (it.timeMillis - start) / 60_000.0 }
        val ys = window.map { it.mmol }
        val meanX = xs.average()
        val meanY = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - meanX) * (ys[i] - meanY)
            den += (xs[i] - meanX) * (xs[i] - meanX)
        }
        if (den == 0.0) return null
        val slope = num / den
        return Trend(slope, TrendArrow.forRate(slope))
    }

    /**
     * Minutes until [from] reaches [threshold] at [mmolPerMinute], or null if
     * it is moving away from it or will take longer than [horizonMinutes].
     */
    fun minutesUntil(from: Double, threshold: Double, mmolPerMinute: Double, horizonMinutes: Int = 30): Int? {
        if (mmolPerMinute == 0.0) return null
        val minutes = (threshold - from) / mmolPerMinute
        if (minutes < 0 || minutes > horizonMinutes) return null
        return kotlin.math.round(minutes).toInt()
    }
}
