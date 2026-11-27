package app.steadmo.core

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The daily meal reminder (SPEC.md §4.13): at a set time, "Bolus for dinner?",
 * with Bolus and reminders in 30 or 60 minutes.
 */
object MealReminder {
    val DEFAULT_TIME: LocalTime = LocalTime.of(18, 0)
    val SNOOZES_MINUTES = listOf(30, 60)

    /** A set reminder that came due this long ago without going off is still shown (e.g. after a restart). */
    const val LATE_MILLIS = 2 * 60 * 60_000L

    /**
     * When the reminder should next go off: [pending], the one already set, if
     * it came due under [LATE_MILLIS] ago without going off; else today's [time]
     * if it's still ahead, else tomorrow's. Each day's time is resolved from
     * that day's date, so a clock change only moves the day it happens on.
     */
    fun nextAt(now: Long, time: LocalTime, zone: ZoneId, pending: Long? = null): Long {
        if (pending != null && pending <= now && now - pending < LATE_MILLIS) return pending
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val today = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
        return if (today > now) today else ZonedDateTime.of(date.plusDays(1), time, zone).toInstant().toEpochMilli()
    }
}
