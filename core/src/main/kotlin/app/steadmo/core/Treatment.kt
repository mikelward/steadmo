package app.steadmo.core

/**
 * A hypo treatment recorded in Steadmo with "Treated" (SPEC.md §4.10). It is
 * Steadmo's own note, not logged in CamAPS FX.
 */
object Treatment {
    /** Recheck reminder after treating. */
    const val RECHECK_MILLIS = 15 * 60_000L

    /** Rise alerts after a treatment are expected, so they hold off this long. */
    const val HOLD_MILLIS = 45 * 60_000L

    /** Kept for 14 days, like readings, so the weekly report can count treated lows. */
    const val RETENTION_MILLIS = 14 * 24 * 60 * 60_000L

    /** Whether [kind] is a rise that a treatment at [treatedAt] explains, at [now]. */
    fun explains(kind: AlertKind, treatedAt: Long?, now: Long): Boolean =
        treatedAt != null &&
            (kind == AlertKind.RISING_FAST || kind == AlertKind.HIGH_SOON) &&
            now - treatedAt in 0 until HOLD_MILLIS

    /**
     * Whether [trend] shows glucose visibly rising after a treatment at
     * [treatedAt], so a calm low alert doesn't cry wolf while carbs are working.
     */
    fun isRecovering(trend: Trend?, treatedAt: Long?, now: Long): Boolean =
        treatedAt != null &&
            trend?.arrow?.isRising == true &&
            now - treatedAt in 0 until HOLD_MILLIS

    enum class RecheckOutcome { URGENT_LOW, LOW, RECOVERING, RECOVERED, STALE }

    /**
     * What should happen 15 minutes after "Treated" (SPEC.md §4.10): stale or
     * missing readings ask for a manual check, while fresh ones re-alert only if
     * still urgently low or low without turning upward.
     */
    fun recheck(
        latest: Reading?,
        trend: Trend?,
        now: Long,
        treatedAt: Long?,
        staleMillis: Long,
    ): RecheckOutcome {
        if (latest == null || now - latest.timeMillis >= staleMillis) return RecheckOutcome.STALE
        if (UrgentLow.isUrgent(latest, trend)) return RecheckOutcome.URGENT_LOW
        if (latest.side() != RangeSide.LOW) return RecheckOutcome.RECOVERED
        if (isRecovering(trend, treatedAt, now)) return RecheckOutcome.RECOVERING
        return RecheckOutcome.LOW
    }
}

/** Alerts that a low treatment answers: offered "Treated". */
val AlertKind.isLow: Boolean get() = this == AlertKind.LOW_SOON || this == AlertKind.FALLING_FAST
