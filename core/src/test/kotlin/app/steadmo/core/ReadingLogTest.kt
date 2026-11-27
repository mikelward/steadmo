package app.steadmo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingLogTest {
    private val min = 60_000L

    @Test fun repostOfTheSameValueIsDropped() {
        val log = ReadingLog()
        assertTrue(log.add(Reading(0, 6.0)))
        assertFalse(log.add(Reading(1 * min, 6.0)))
        assertEquals(1, log.all.size)
    }

    @Test fun quickCorrectionReplacesTheLastReading() {
        val log = ReadingLog()
        log.add(Reading(0, 6.0))
        assertTrue(log.add(Reading(1 * min, 6.2)))
        assertEquals(listOf(Reading(1 * min, 6.2)), log.all)
    }

    @Test fun sameValueFiveMinutesLaterIsANewReading() {
        val log = ReadingLog()
        log.add(Reading(0, 6.0))
        assertTrue(log.add(Reading(5 * min, 6.0)))
        assertEquals(2, log.all.size)
    }

    @Test fun olderThanADayIsDropped() {
        val log = ReadingLog()
        log.add(Reading(0, 6.0))
        log.add(Reading(ReadingLog.RETENTION_MILLIS + 5 * min, 7.0))
        assertEquals(listOf(Reading(ReadingLog.RETENTION_MILLIS + 5 * min, 7.0)), log.all)
    }

    @Test fun outOfOrderIsIgnored() {
        val log = ReadingLog()
        log.add(Reading(10 * min, 6.0))
        assertFalse(log.add(Reading(0, 5.0)))
    }

    @Test fun encodeRoundTrips() {
        val log = ReadingLog()
        log.add(Reading(0, 6.0))
        log.add(Reading(5 * min, 6.4))
        assertEquals(log.all, ReadingLog.decode(log.encode() + "\ngarbage\n").all)
    }
}
