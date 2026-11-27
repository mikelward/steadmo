package app.steadmo.core

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MealReminderTest {
    private val zone = ZoneId.of("Europe/London")
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun `today if still ahead, else tomorrow`() {
        assertEquals(at(27, 18), MealReminder.nextAt(at(27, 9), MealReminder.DEFAULT_TIME, zone))
        assertEquals(at(28, 18), MealReminder.nextAt(at(27, 18), MealReminder.DEFAULT_TIME, zone))
        assertEquals(at(28, 18), MealReminder.nextAt(at(27, 20), MealReminder.DEFAULT_TIME, zone))
        assertEquals(at(27, 19, 30), MealReminder.nextAt(at(27, 18), LocalTime.of(19, 30), zone))
    }

    @Test fun `a reminder due but not yet shown isn't lost to a restart`() {
        // 18:01, today's set but not gone off yet: it stays due.
        assertEquals(at(27, 18), MealReminder.nextAt(at(27, 18, 1), MealReminder.DEFAULT_TIME, zone, pending = at(27, 18)))
        // Still ahead: kept.
        assertEquals(at(27, 18), MealReminder.nextAt(at(27, 17), MealReminder.DEFAULT_TIME, zone, pending = at(27, 18)))
        // Too late to bother: tomorrow.
        assertEquals(at(28, 18), MealReminder.nextAt(at(27, 21), MealReminder.DEFAULT_TIME, zone, pending = at(27, 18)))
    }

    @Test fun `a time in the spring-forward gap doesn't carry over to the next day`() {
        val gapDay = ZonedDateTime.of(2026, 3, 29, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 3, 30, 1, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, MealReminder.nextAt(gapDay, LocalTime.of(1, 30), zone))
    }

    @Test fun `follows clock changes`() {
        val zoned = ZoneId.of("Europe/London")
        // The night the clocks go back (25 Oct 2026): still 18:00 local the next day.
        val before = ZonedDateTime.of(2026, 10, 24, 20, 0, 0, 0, zoned).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 10, 25, 18, 0, 0, 0, zoned).toInstant().toEpochMilli()
        assertEquals(expected, MealReminder.nextAt(before, MealReminder.DEFAULT_TIME, zoned))
    }
}
