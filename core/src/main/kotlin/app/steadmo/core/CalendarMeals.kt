package app.steadmo.core

/**
 * Pre-bolus reminders for calendar events named for a meal or a restaurant
 * booking (SPEC.md §4.14), e.g. "Dinner with Sam" or "Reservation at Dishoom":
 * when they start.
 */
object CalendarMeals {
    /** A reminder that arrives this long after the meal starts is still shown; later, it's no use. */
    const val LATE_MILLIS = 30 * 60_000L

    /** Whether a reminder for a meal starting at [start] is still worth showing at [now]. */
    fun current(start: Long, now: Long): Boolean = now < start + LATE_MILLIS

    /** How far ahead Steadmo looks; it looks again well before this runs out. */
    const val LOOKAHEAD_MILLIS = 24 * 60 * 60_000L

    data class Event(val id: Long, val title: String, val start: Long, val allDay: Boolean) {
        /** Which instance this is, to remember that its reminder went off. */
        val key: String get() = "$id:$start"
    }

    /** Meals by name, and restaurant bookings, which Gmail adds as events ("Reservation at …"). */
    private val meal = Regex(
        """\b(breakfast|brunch|lunch|luncheon|dinner|supper|meal|reservation|restaurant|table for \d+)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun isMeal(title: String): Boolean = meal.containsMatchIn(title)

    /**
     * The next event to remind about, and when (its start): the soonest timed
     * meal event still [current] whose reminder hasn't gone off ([reminded]
     * keys). A start already past (a delayed alarm, or an event added or moved
     * late) is returned as is, so it goes off at once.
     */
    fun next(events: List<Event>, now: Long, reminded: Set<String> = emptySet()): Pair<Event, Long>? = events
        .filter { !it.allDay && isMeal(it.title) && current(it.start, now) && it.start - now <= LOOKAHEAD_MILLIS && it.key !in reminded }
        .minByOrNull { it.start }
        ?.let { it to it.start }
}
