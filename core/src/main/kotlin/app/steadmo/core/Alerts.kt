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

    fun evaluate(latest: Reading, trend: Trend?, unit: GlucoseUnit = GlucoseUnit.MMOL): TrendAlert? {
        val alert = applying(latest, trend, unit) ?: return null
        val last = lastAlerted[alert.kind]
        if (last != null && latest.timeMillis - last < REPEAT_MILLIS) return null
        lastAlerted[alert.kind] = latest.timeMillis
        return alert
    }

    /**
     * The alert [latest] and [trend] call for, ignoring repeat limits: what a
     * shown alert must still match to stay up.
     */
    fun applying(latest: Reading, trend: Trend?, unit: GlucoseUnit = GlucoseUnit.MMOL): TrendAlert? {
        trend ?: return null
        if (latest.bound != null) return null
        return candidate(latest, trend, unit)
    }

    private fun candidate(latest: Reading, trend: Trend, unit: GlucoseUnit): TrendAlert? {
        val rate = trend.mmolPerMinute
        val value = unit.reading(latest)
        val rateText = "${unit.rate(rate)}/min"

        if (rate < 0 && latest.mmol > LOW_MMOL) {
            TrendCalculator.minutesUntil(latest.mmol, LOW_MMOL, rate, horizonMinutes = 20)?.let { minutes ->
                return TrendAlert(AlertKind.LOW_SOON, trend.arrow, "Low in about $minutes min",
                    "$value, $rateText. Heading below ${unit.value(LOW_MMOL)}.")
            }
        }
        if (trend.arrow == TrendArrow.DOUBLE_DOWN) {
            return TrendAlert(AlertKind.FALLING_FAST, trend.arrow, "Falling fast: $value",
                "$rateText, ${unit.value(abs(rate) * 30)} ${unit.label} in 30 min at this rate.")
        }
        if (rate > 0 && latest.mmol < HIGH_MMOL) {
            TrendCalculator.minutesUntil(latest.mmol, HIGH_MMOL, rate, horizonMinutes = 15)?.let { minutes ->
                if (trend.arrow == TrendArrow.UP || trend.arrow == TrendArrow.DOUBLE_UP) {
                    return TrendAlert(AlertKind.HIGH_SOON, trend.arrow, "High in about $minutes min",
                        "$value, $rateText. Heading above ${unit.value(HIGH_MMOL)}.")
                }
            }
        }
        if (trend.arrow == TrendArrow.DOUBLE_UP) {
            return TrendAlert(AlertKind.RISING_FAST, trend.arrow, "Rising fast: $value",
                "$rateText, +${unit.value(rate * 30)} ${unit.label} in 30 min at this rate.")
        }
        return null
    }

    companion object {
        const val REPEAT_MILLIS = 30 * 60_000L

        fun fmt(mmol: Double): String = String.format(Locale.US, "%.1f", mmol)
        fun fmt2(mmol: Double): String = String.format(Locale.US, "%.2f", mmol)
    }
}
