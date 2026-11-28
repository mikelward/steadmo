package app.steadmo.core

/**
 * Steadmo's low alert below [LOW_MMOL] (70 mg/dL) when not urgently low
 * (SPEC.md §4.8): sounds as a looping alarm on its own alarm-stream channel
 * until OK or Treated. OK snoozes Low for [SNOOZE_MILLIS] without snoozing
 * Urgent Low; Treated snoozes both for [SNOOZE_MILLIS] and stays quiet
 * afterwards while glucose is visibly rising.
 */
object LowAlert {
    const val SNOOZE_MILLIS = 15 * 60_000L

    enum class Decision { ALERT, KEEP, SNOOZED, RECOVERING, SUPERSEDED, CLEAR }

    fun decide(
        latest: Reading,
        trend: Trend?,
        acknowledgedAt: Long?,
        lastAlertedAt: Long?,
        treatedAt: Long?,
        now: Long = latest.timeMillis,
    ): Decision {
        if (latest.side() != RangeSide.LOW) return Decision.CLEAR
        if (UrgentLow.isUrgent(latest, trend)) return Decision.SUPERSEDED
        if (acknowledgedAt != null && now - acknowledgedAt in 0 until SNOOZE_MILLIS) return Decision.SNOOZED
        if (treatedAt != null && now - treatedAt in 0 until SNOOZE_MILLIS) return Decision.SNOOZED
        if (Treatment.isRecovering(trend, treatedAt, now)) return Decision.RECOVERING
        if (lastAlertedAt != null && now - lastAlertedAt in 0 until SNOOZE_MILLIS) return Decision.KEEP
        return Decision.ALERT
    }
}
