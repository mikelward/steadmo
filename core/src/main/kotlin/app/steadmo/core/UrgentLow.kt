package app.steadmo.core

/** Below this, a low is urgent (the usual "urgent low" line, 54 mg/dL). */
const val URGENT_LOW_MMOL = 3.0

/** Below [URGENT_LOW_MMOL], or under the sensor's lowest reading. */
fun Reading.urgentLow(): Boolean = bound == Bound.BELOW || mmol < URGENT_LOW_MMOL - LINE_SLACK_MMOL

/**
 * Steadmo's urgent low alert (SPEC.md §4.8): sounds as a looping alarm on every
 * new reading while glucose is below [URGENT_LOW_MMOL], under the sensor's
 * range, or at or below [LOW_MMOL] and projected below [URGENT_LOW_MMOL] within
 * [SOON_HORIZON_MINUTES]. OK or Treated quiets it for [SNOOZE_MILLIS]. It adds
 * to CamAPS FX's alarms; it doesn't replace them.
 */
object UrgentLow {
    const val SNOOZE_MILLIS = 15 * 60_000L

    /** Horizon for escalating a low reading that is falling toward [URGENT_LOW_MMOL]. */
    const val SOON_HORIZON_MINUTES = 20

    /** Ignored this long (no OK or Treated), the alarm takes over the screen. */
    const val ESCALATE_MILLIS = 5 * 60_000L

    /**
     * Minutes until a reading at or below [LOW_MMOL] reaches [URGENT_LOW_MMOL]
     * within [SOON_HORIZON_MINUTES], or null if it isn't heading there soon (or
     * is already below [URGENT_LOW_MMOL]).
     */
    fun minutesUntil(latest: Reading, trend: Trend?): Int? {
        if (latest.bound != null || latest.urgentLow()) return null
        if (latest.mmol > LOW_MMOL + LINE_SLACK_MMOL) return null
        val rate = trend?.mmolPerMinute ?: return null
        if (rate >= 0) return null
        return TrendCalculator.minutesUntil(latest.mmol, URGENT_LOW_MMOL, rate, horizonMinutes = SOON_HORIZON_MINUTES)
            ?.coerceAtLeast(1)
    }

    /** Whether [latest] and [trend] call for the urgent low alarm. */
    fun isUrgent(latest: Reading, trend: Trend? = null): Boolean =
        latest.urgentLow() || minutesUntil(latest, trend) != null

    /** Whether an alarm sounding since [alertingSince] has been ignored long enough by [now]. */
    fun escalates(alertingSince: Long?, now: Long): Boolean =
        alertingSince != null && now - alertingSince >= ESCALATE_MILLIS

    enum class Decision { ALERT, SNOOZED, CLEAR }

    fun decide(
        latest: Reading,
        acknowledgedAt: Long?,
        trend: Trend? = null,
        treatedAt: Long? = null,
        now: Long = latest.timeMillis,
    ): Decision = when {
        !isUrgent(latest, trend) -> Decision.CLEAR
        acknowledgedAt != null && now - acknowledgedAt in 0 until SNOOZE_MILLIS -> Decision.SNOOZED
        treatedAt != null && now - treatedAt in 0 until SNOOZE_MILLIS -> Decision.SNOOZED
        else -> Decision.ALERT
    }
}
