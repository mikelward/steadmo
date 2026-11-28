package app.steadmo.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/** One scheduled meal reminder: at [time] on [days], when [enabled]. */
data class MealReminderRule(
    val id: Long,
    val time: LocalTime,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val enabled: Boolean = true,
)

/**
 * Scheduled meal reminders (SPEC.md §4.13): at each rule's time on its chosen
 * days of the week, "Time to bolus for your meal?", with Bolus and reminders
 * in 30 or 60 minutes.
 */
object MealReminder {
    val DEFAULT_TIME: LocalTime = LocalTime.of(18, 0)
    val SNOOZES_MINUTES = listOf(30, 60)

    /** A set reminder that came due this long ago without going off is still shown (e.g. after a restart). */
    const val LATE_MILLIS = 2 * 60 * 60_000L

    private val WEEKDAYS = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    private val WEEKENDS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    fun defaultRule(on: Boolean = true, time: LocalTime = DEFAULT_TIME): MealReminderRule =
        MealReminderRule(id = 1L, time = time, days = DayOfWeek.entries.toSet(), enabled = on)

    /**
     * When a single daily reminder should next go off: [pending], if it came
     * due under [LATE_MILLIS] ago without going off; else today's [time] if
     * still ahead, else tomorrow's.
     */
    fun nextAt(now: Long, time: LocalTime, zone: ZoneId, pending: Long? = null): Long =
        nextAt(now, listOf(defaultRule(time = time)), zone, pending)!!

    /** When [rule] should next go off after [now], or null if off or no days are selected. */
    fun nextAt(now: Long, rule: MealReminderRule, zone: ZoneId): Long? {
        if (!rule.enabled || rule.days.isEmpty()) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        // Up to 7 days ahead covers same weekday next week and DST transitions.
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            if (date.dayOfWeek !in rule.days) continue
            val at = ZonedDateTime.of(date, rule.time, zone).toInstant().toEpochMilli()
            if (at > now) return at
        }
        return null
    }

    /**
     * When the soonest of [rules] should next go off, or null if none are
     * active. [pending], if it came due under [LATE_MILLIS] ago without going
     * off, stays due so a restart doesn't lose it.
     */
    fun nextAt(now: Long, rules: List<MealReminderRule>, zone: ZoneId, pending: Long? = null): Long? {
        val active = rules.filter { it.enabled && it.days.isNotEmpty() }
        if (active.isEmpty()) return null
        if (pending != null && pending <= now && now - pending < LATE_MILLIS) return pending
        return active.mapNotNull { nextAt(now, it, zone) }.minOrNull()
    }

    /** Short human-readable summary of [days], e.g. "Every day", "Weekdays", "Mon, Wed, Fri". */
    fun formatDays(days: Set<DayOfWeek>): String = when {
        days.isEmpty() -> "No days"
        days.size == DayOfWeek.entries.size -> "Every day"
        days == WEEKDAYS -> "Weekdays"
        days == WEEKENDS -> "Weekends"
        else -> DayOfWeek.entries.filter { it in days }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.US) }
    }

    /** One `id,minuteOfDay,days,enabled` line per rule, for on-device storage. */
    fun encode(rules: List<MealReminderRule>): String = rules.joinToString("\n") { r ->
        val days = DayOfWeek.entries.filter { it in r.days }.joinToString(";") { it.value.toString() }
        "${r.id},${r.time.toSecondOfDay() / 60},$days,${if (r.enabled) 1 else 0}"
    }

    /** Reads [encode]'s output, skipping lines it can't parse. */
    fun decode(text: String): List<MealReminderRule> = text.lineSequence().mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val parts = line.split(',')
        val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val minuteOfDay = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0 until 24 * 60 } ?: return@mapNotNull null
        val daysText = parts.getOrNull(2).orEmpty()
        val days = if (daysText.isEmpty()) {
            emptySet()
        } else {
            daysText.split(';').mapNotNull { it.toIntOrNull()?.takeIf { d -> d in 1..7 }?.let(DayOfWeek::of) }.toSet()
        }
        val enabled = (parts.getOrNull(3)?.toIntOrNull() ?: 1) != 0
        MealReminderRule(id, LocalTime.ofSecondOfDay(minuteOfDay * 60L), days, enabled)
    }.toList()
}
