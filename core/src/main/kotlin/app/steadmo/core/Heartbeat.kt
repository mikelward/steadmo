package app.steadmo.core

/**
 * Says so when CamAPS FX stops sending glucose (SPEC.md §4.7): sensor or
 * Bluetooth signal loss, CamAPS FX stopped, or Steadmo cut off from its
 * notification. CamAPS FX posts about every 5 minutes, so 15 minutes of
 * silence is three missed readings.
 */
object Heartbeat {
    const val QUIET_MILLIS = 15 * 60_000L
    const val REPEAT_MILLIS = 30 * 60_000L

    /** After this long, stop repeating: CamAPS FX is likely off on purpose, and the app still says so. */
    const val GIVE_UP_MILLIS = 2 * 60 * 60_000L

    sealed interface Decision {
        /** Warn now that nothing has come for [quietMinutes]; check again at [nextCheck]. */
        data class Warn(val quietMinutes: Long, val nextCheck: Long) : Decision

        /** All well, or already warned: check again at [at]. */
        data class CheckAt(val at: Long) : Decision

        /** No reading ever: nothing to miss yet. */
        data object Idle : Decision
    }

    /**
     * What to do at [now], given the [latest] reading's time and when Steadmo
     * [lastWarned] (null if never, or not since the latest reading).
     */
    fun check(latest: Long?, now: Long, lastWarned: Long?): Decision {
        latest ?: return Decision.Idle
        val quiet = now - latest
        if (quiet < QUIET_MILLIS) return Decision.CheckAt(latest + QUIET_MILLIS)
        if (quiet > GIVE_UP_MILLIS) return Decision.Idle
        val warned = lastWarned?.takeIf { it >= latest }
        if (warned != null && now - warned in 0 until REPEAT_MILLIS) return Decision.CheckAt(warned + REPEAT_MILLIS)
        return Decision.Warn(quiet / 60_000, now + REPEAT_MILLIS)
    }
}
