package app.steadmo.core

/**
 * The readings seen so far, oldest first, kept for [RETENTION_MILLIS].
 *
 * CamAPS re-posts its notification for reasons other than a new CGM value (a
 * mode change, a re-sort), so a post within [MIN_INTERVAL_MILLIS] of the last
 * reading is the same reading: dropped if the value is unchanged, or replacing
 * it if CamAPS corrected it.
 */
class ReadingLog(initial: List<Reading> = emptyList()) {
    private val readings = initial.sortedBy { it.timeMillis }.toMutableList()

    val all: List<Reading> get() = readings.toList()
    val latest: Reading? get() = readings.lastOrNull()

    /** Returns true if [reading] changed the log. */
    fun add(reading: Reading): Boolean {
        val last = readings.lastOrNull()
        val changed = when {
            last == null -> { readings += reading; true }
            reading.timeMillis < last.timeMillis -> false
            reading.timeMillis - last.timeMillis < MIN_INTERVAL_MILLIS ->
                if (reading.mmol == last.mmol && reading.bound == last.bound) {
                    false
                } else {
                    readings[readings.lastIndex] = reading; true
                }
            else -> { readings += reading; true }
        }
        val cutoff = reading.timeMillis - RETENTION_MILLIS
        readings.removeAll { it.timeMillis < cutoff }
        return changed
    }

    /** One `time,mmol[,bound]` line per reading, for the on-device store. */
    fun encode(): String = readings.joinToString("\n") { r ->
        listOfNotNull(r.timeMillis, r.mmol, r.bound?.name).joinToString(",")
    }

    companion object {
        const val MIN_INTERVAL_MILLIS = 4 * 60_000L
        const val RETENTION_MILLIS = 24 * 60 * 60_000L

        /** Reads [encode]'s output, skipping lines it can't parse. */
        fun decode(text: String): ReadingLog = ReadingLog(
            text.lineSequence().mapNotNull { line ->
                val parts = line.split(',')
                val time = parts.getOrNull(0)?.toLongOrNull()
                val mmol = parts.getOrNull(1)?.toDoubleOrNull()
                val bound = parts.getOrNull(2)?.let { name -> Bound.entries.firstOrNull { it.name == name } }
                if (time != null && mmol != null) Reading(time, mmol, bound) else null
            }.toList(),
        )
    }
}
