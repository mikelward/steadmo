package app.steadmo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RangeTest {
    private val min = 60_000L

    @Test fun sides() {
        assertEquals(RangeSide.LOW, Reading(0, 3.8).side())
        assertEquals(RangeSide.IN_RANGE, Reading(0, 3.9).side())
        assertEquals(RangeSide.IN_RANGE, Reading(0, 10.0).side())
        assertEquals(RangeSide.HIGH, Reading(0, 10.1).side())
        assertEquals(RangeSide.LOW, Reading(0, 2.2, Bound.BELOW).side())
        assertEquals(RangeSide.HIGH, Reading(0, 22.2, Bound.ABOVE).side())
    }

    @Test fun runStartsAtTheFirstReadingOnTheSameSide() {
        val readings = listOf(Reading(0, 3.5), Reading(5 * min, 4.5), Reading(10 * min, 3.8), Reading(15 * min, 3.6))
        assertEquals(10 * min, outOfRangeSince(readings))
    }

    @Test fun switchingSidesStartsANewRun() {
        assertEquals(5 * min, outOfRangeSince(listOf(Reading(0, 12.0), Reading(5 * min, 3.0))))
    }

    @Test fun inRangeHasNoRun() {
        assertNull(outOfRangeSince(listOf(Reading(0, 3.0), Reading(5 * min, 6.0))))
        assertNull(outOfRangeSince(emptyList()))
    }
}
