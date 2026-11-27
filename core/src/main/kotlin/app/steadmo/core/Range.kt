package app.steadmo.core

enum class RangeSide { LOW, IN_RANGE, HIGH }

fun Reading.side(): RangeSide = when {
    mmol < LOW_MMOL || bound == Bound.BELOW -> RangeSide.LOW
    mmol > HIGH_MMOL || bound == Bound.ABOVE -> RangeSide.HIGH
    else -> RangeSide.IN_RANGE
}

/**
 * When the current run out of range began: the first of the latest readings
 * on the same side of the range, or null when the latest is in range.
 */
fun outOfRangeSince(readings: List<Reading>): Long? {
    val latest = readings.lastOrNull() ?: return null
    val side = latest.side()
    if (side == RangeSide.IN_RANGE) return null
    return readings.takeLastWhile { it.side() == side }.first().timeMillis
}
