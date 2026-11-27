package app.steadmo.core

/** Below this, a low is urgent (the usual "urgent low" line, 54 mg/dL). */
const val URGENT_LOW_MMOL = 3.0

/** Below [URGENT_LOW_MMOL], or under the sensor's lowest reading. */
fun Reading.urgentLow(): Boolean = bound == Bound.BELOW || mmol < URGENT_LOW_MMOL

/**
 * Steadmo's urgent low alert (SPEC.md §4.8): it sounds as an alarm on every
 * new reading while glucose stays urgently low, until the user taps OK, which
 * quiets it for [SNOOZE_MILLIS]. It adds to CamAPS FX's alarms; it doesn't
 * replace them.
 */
object UrgentLow {
    const val SNOOZE_MILLIS = 15 * 60_000L

    /** Ignored this long (no OK or Treated), the alarm takes over the screen. */
    const val ESCALATE_MILLIS = 5 * 60_000L

    /** Whether an alarm sounding since [alertingSince] has been ignored long enough by [now]. */
    fun escalates(alertingSince: Long?, now: Long): Boolean =
        alertingSince != null && now - alertingSince >= ESCALATE_MILLIS

    enum class Decision { ALERT, SNOOZED, CLEAR }

    fun decide(latest: Reading, acknowledgedAt: Long?): Decision = when {
        !latest.urgentLow() -> Decision.CLEAR
        acknowledgedAt != null && latest.timeMillis - acknowledgedAt in 0 until SNOOZE_MILLIS -> Decision.SNOOZED
        else -> Decision.ALERT
    }
}
