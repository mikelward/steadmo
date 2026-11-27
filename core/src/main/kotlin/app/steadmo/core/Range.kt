package app.steadmo.core

enum class RangeSide { LOW, IN_RANGE, HIGH }

fun Reading.side(): RangeSide = when {
    mmol < LOW_MMOL || bound == Bound.BELOW -> RangeSide.LOW
    mmol > HIGH_MMOL || bound == Bound.ABOVE -> RangeSide.HIGH
    else -> RangeSide.IN_RANGE
}

/** Readings further apart than this leave glucose unknown between them. */
const val MAX_READING_GAP_MILLIS = 15 * 60_000L

/**
 * When the current run out of range began: the first of the latest readings
 * on the same side of the range, or null when the latest is in range. A gap
 * longer than [MAX_READING_GAP_MILLIS] ends the run, since glucose wasn't
 * observed across it.
 */
fun outOfRangeSince(readings: List<Reading>): Long? {
    val latest = readings.lastOrNull() ?: return null
    val side = latest.side()
    if (side == RangeSide.IN_RANGE) return null
    var start = latest
    for (i in readings.lastIndex - 1 downTo 0) {
        val earlier = readings[i]
        if (earlier.side() != side || start.timeMillis - earlier.timeMillis > MAX_READING_GAP_MILLIS) break
        start = earlier
    }
    return start.timeMillis
}
