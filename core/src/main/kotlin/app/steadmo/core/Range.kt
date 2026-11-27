package app.steadmo.core

enum class RangeSide { LOW, IN_RANGE, HIGH }

/**
 * Half of 1 mg/dL in mmol/L. mg/dL readings are converted on the way in, so
 * the standard lines land a hair off: 70 mg/dL is 3.885, 54 is 2.997. Within
 * this of a line counts as on it, so each line means the same in both units.
 */
const val LINE_SLACK_MMOL = 0.5 / MGDL_PER_MMOL

/** Below [LOW_MMOL] (under 70 mg/dL). */
fun belowLow(mmol: Double): Boolean = mmol < LOW_MMOL - LINE_SLACK_MMOL

/** Above [HIGH_MMOL] (over 180 mg/dL). */
fun aboveHigh(mmol: Double): Boolean = mmol > HIGH_MMOL + LINE_SLACK_MMOL

fun Reading.side(): RangeSide = when {
    belowLow(mmol) || bound == Bound.BELOW -> RangeSide.LOW
    aboveHigh(mmol) || bound == Bound.ABOVE -> RangeSide.HIGH
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
