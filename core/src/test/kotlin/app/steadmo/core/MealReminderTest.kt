package app.steadmo.core

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun `picks the soonest active rule matching its days of the week`() {
        // 2026-11-27 is a Friday.
        val fridayMorning = at(27, 9)
        val weekdayLunch = MealReminderRule(1, LocalTime.of(12, 30), setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val weekendBrunch = MealReminderRule(2, LocalTime.of(10, 0), setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        val disabled = MealReminderRule(3, LocalTime.of(9, 30), enabled = false)
        val noDays = MealReminderRule(4, LocalTime.of(9, 15), days = emptySet())
        val rules = listOf(weekdayLunch, weekendBrunch, disabled, noDays)

        assertEquals(at(27, 12, 30), MealReminder.nextAt(fridayMorning, rules, zone))
        // After Friday lunch, Saturday brunch is next.
        assertEquals(at(28, 10, 0), MealReminder.nextAt(at(27, 13, 0), rules, zone))
        // Same day next week when only one day is enabled and today's time passed.
        val fridayOnly = MealReminderRule(5, LocalTime.of(12, 0), setOf(DayOfWeek.FRIDAY))
        val nextFriday = ZonedDateTime.of(2026, 12, 4, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(nextFriday, MealReminder.nextAt(at(27, 13, 0), listOf(fridayOnly), zone))
        assertNull(MealReminder.nextAt(fridayMorning, listOf(disabled, noDays), zone))
        assertNull(MealReminder.nextAt(fridayMorning, emptyList<MealReminderRule>(), zone))
    }

    @Test fun `formats day sets concisely`() {
        assertEquals("Every day", MealReminder.formatDays(DayOfWeek.entries.toSet()))
        assertEquals(
            "Weekdays",
            MealReminder.formatDays(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)),
        )
        assertEquals("Weekends", MealReminder.formatDays(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)))
        assertEquals("No days", MealReminder.formatDays(emptySet()))
        assertEquals("Mon, Wed, Fri", MealReminder.formatDays(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
    }

    @Test fun `encodes and decodes rules, skipping corrupt lines`() {
        val rules = listOf(
            MealReminderRule(1, LocalTime.of(18, 0), DayOfWeek.entries.toSet(), enabled = true),
            MealReminderRule(2, LocalTime.of(8, 30), setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), enabled = false),
            MealReminderRule(3, LocalTime.of(12, 0), emptySet(), enabled = true),
        )
        assertEquals(rules, MealReminder.decode(MealReminder.encode(rules)))
        assertEquals(rules, MealReminder.decode(MealReminder.encode(rules) + "\n\nbad,line\n4,9999,1,1\n"))
        assertEquals(emptyList<MealReminderRule>(), MealReminder.decode(""))
    }
}
