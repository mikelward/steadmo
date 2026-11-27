package app.steadmo.core

import java.util.Locale
import kotlin.math.abs

const val LOW_MMOL = 3.9
const val HIGH_MMOL = 10.0

enum class AlertKind { FALLING_FAST, RISING_FAST, LOW_SOON, HIGH_SOON }

/** A trend alert: what is happening, and when it will matter. */
data class TrendAlert(val kind: AlertKind, val arrow: TrendArrow, val title: String, val text: String)

/**
 * Decides when a trend is worth interrupting for (SPEC.md §4.2). A double
 * arrow alerts; so does a projected crossing of [LOW_MMOL] within 20 minutes
 * or of [HIGH_MMOL] within 15. Each kind repeats at most every [REPEAT_MILLIS].
 */
class AlertPolicy(private val lastAlerted: MutableMap<AlertKind, Long> = mutableMapOf()) {

    fun evaluate(latest: Reading, trend: Trend?): TrendAlert? {
        trend ?: return null
        val alert = candidate(latest, trend) ?: return null
        val last = lastAlerted[alert.kind]
        if (last != null && latest.timeMillis - last < REPEAT_MILLIS) return null
        lastAlerted[alert.kind] = latest.timeMillis
        return alert
    }

    private fun candidate(latest: Reading, trend: Trend): TrendAlert? {
        val rate = trend.mmolPerMinute
        val value = fmt(latest.mmol)
        val rateText = "${if (rate >= 0) "+" else "−"}${fmt2(abs(rate))}/min"

        if (rate < 0 && latest.mmol > LOW_MMOL) {
            TrendCalculator.minutesUntil(latest.mmol, LOW_MMOL, rate, horizonMinutes = 20)?.let { minutes ->
                return TrendAlert(AlertKind.LOW_SOON, trend.arrow, "Low in about $minutes min",
                    "$value, $rateText. Heading below ${fmt(LOW_MMOL)}.")
            }
        }
        if (trend.arrow == TrendArrow.DOUBLE_DOWN) {
            return TrendAlert(AlertKind.FALLING_FAST, trend.arrow, "Falling fast: $value",
                "$rateText, ${fmt(abs(rate) * 30)} mmol/L in 30 min at this rate.")
        }
        if (rate > 0 && latest.mmol < HIGH_MMOL) {
            TrendCalculator.minutesUntil(latest.mmol, HIGH_MMOL, rate, horizonMinutes = 15)?.let { minutes ->
                if (trend.arrow == TrendArrow.UP || trend.arrow == TrendArrow.DOUBLE_UP) {
                    return TrendAlert(AlertKind.HIGH_SOON, trend.arrow, "High in about $minutes min",
                        "$value, $rateText. Heading above ${fmt(HIGH_MMOL)}.")
                }
            }
        }
        if (trend.arrow == TrendArrow.DOUBLE_UP) {
            return TrendAlert(AlertKind.RISING_FAST, trend.arrow, "Rising fast: $value",
                "$rateText, +${fmt(rate * 30)} mmol/L in 30 min at this rate.")
        }
        return null
    }

    companion object {
        const val REPEAT_MILLIS = 30 * 60_000L

        fun fmt(mmol: Double): String = String.format(Locale.US, "%.1f", mmol)
        fun fmt2(mmol: Double): String = String.format(Locale.US, "%.2f", mmol)
    }
}
