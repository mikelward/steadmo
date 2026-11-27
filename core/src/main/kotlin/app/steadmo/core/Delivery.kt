package app.steadmo.core

/**
 * Food delivery apps whose notifications suggest a pre-bolus (SPEC.md §4.12).
 * Steadmo reads their notifications on the device only, for the order's
 * progress, and never stores what they say.
 */
enum class DeliveryApp(val label: String, private val prefixes: List<String>) {
    DELIVEROO("Deliveroo", listOf("com.deliveroo.")),
    UBER_EATS("Uber Eats", listOf("com.ubercab.eats")),
    JUST_EAT("Just Eat", listOf("com.justeat.", "com.menulog.")),
    DOORDASH("DoorDash", listOf("com.dd.doordash")),
    ;

    companion object {
        fun of(packageName: String): DeliveryApp? =
            entries.firstOrNull { app -> app.prefixes.any { packageName.startsWith(it) } }
    }
}

object DeliveryPrompt {
    /** One prompt per app in this long: an order posts several updates. */
    const val QUIET_MILLIS = 60 * 60_000L

    /** A delivery app's post older than this is history (e.g. replayed on reconnect), not news. */
    const val FRESH_MILLIS = 15 * 60_000L

    fun fresh(postTime: Long, now: Long): Boolean = now - postTime in 0..FRESH_MILLIS

    /**
     * The rider is nearby: the moment to pre-bolus (the maintainer's call), not
     * when the order merely sets off. A time counts only under 5 minutes.
     */
    private val nearby = Regex(
        """\b(nearby|near by|nearly there|almost there|getting close|is close|around the corner|approaching|""" +
            """arriving (now|soon|shortly)|arriving in [1-4]\s*min(ute)?s?|about to arrive)\b|""" +
            """\b[1-4]\s*min(ute)?s?\s+away\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Too late, or not coming: the food is already here, or the order is off. */
    private val arrived = Regex(
        """delivered|has arrived|is here|outside|enjoy your|cancel+ed|refund|unavailable|\bclosed\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Whether [texts] (a notification's title and text) say the rider is nearby. */
    fun nearby(texts: List<String>): Boolean =
        texts.none { arrived.containsMatchIn(it) } && texts.any { nearby.containsMatchIn(it) }

    /** Whether to prompt at [now], given when this app last prompted. */
    fun due(lastPrompt: Long?, now: Long): Boolean =
        lastPrompt == null || now < lastPrompt || now - lastPrompt >= QUIET_MILLIS
}
