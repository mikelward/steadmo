package app.steadmo.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReportTest {
    private val utc = ZoneId.of("UTC")
    private val min = 60_000L
    private val hour = 60 * min

    // Saturday 2026-11-28 11:00 UTC.
    private val sat11 = ZonedDateTime.of(2026, 11, 28, 11, 0, 0, 0, utc).toInstant().toEpochMilli()

    private fun at(year: Int, month: Int, day: Int, h: Int, m: Int = 0, zone: ZoneId = utc): Long =
        ZonedDateTime.of(year, month, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    private fun steadyWeek(
        endMillis: Long = sat11,
        mmol: Double = 6.5,
        stepMinutes: Long = 5,
        days: Int = 7,
    ): MutableList<Reading> {
        val start = endMillis - days * 24 * hour
        val out = mutableListOf<Reading>()
        var t = start + stepMinutes * min
        while (t <= endMillis) {
            out += Reading(t, mmol)
            t += stepMinutes * min
        }
        return out
    }

    // --- WeeklyReportSchedule ---

    @Test fun `defaults are Saturday at 11_00`() {
        assertEquals(DayOfWeek.SATURDAY, WeeklyReportSchedule.DEFAULT_DAY)
        assertEquals(LocalTime.of(11, 0), WeeklyReportSchedule.DEFAULT_TIME)
    }

    @Test fun `before the target time on the target day fires today`() {
        val sat10 = at(2026, 11, 28, 10, 0)
        assertEquals(sat11, WeeklyReportSchedule.nextAt(sat10, DayOfWeek.SATURDAY, LocalTime.of(11, 0), utc))
    }

    @Test fun `at or after the target time on the target day fires next week`() {
        val nextSat11 = at(2026, 12, 5, 11, 0)
        assertEquals(nextSat11, WeeklyReportSchedule.nextAt(sat11, DayOfWeek.SATURDAY, LocalTime.of(11, 0), utc))
        assertEquals(nextSat11, WeeklyReportSchedule.nextAt(sat11 + min, DayOfWeek.SATURDAY, LocalTime.of(11, 0), utc))
    }

    @Test fun `on another day fires on the upcoming target day`() {
        val wed = at(2026, 11, 25, 15, 0)
        assertEquals(sat11, WeeklyReportSchedule.nextAt(wed, DayOfWeek.SATURDAY, LocalTime.of(11, 0), utc))
    }

    @Test fun `overdue pending within late window is kept, older one moves to next week`() {
        val twoHoursLate = sat11 + 2 * hour
        assertEquals(
            sat11,
            WeeklyReportSchedule.nextAt(twoHoursLate, DayOfWeek.SATURDAY, LocalTime.of(11, 0), utc, pending = sat11),
        )
        val twoDaysLate = sat11 + 48 * hour
        assertEquals(
            at(2026, 12, 5, 11, 0),
            WeeklyReportSchedule.nextAt(twoDaysLate, DayOfWeek.SATURDAY, LocalTime.of(11, 0), utc, pending = sat11),
        )
    }

    // --- WeeklyReport.generate ---

    @Test fun `empty readings produce an empty summary`() {
        val s = WeeklyReport.generate(emptyList(), sat11, utc)
        assertEquals(0, s.readingCount)
        assertEquals(0, s.coveragePercent)
        assertNull(s.averageMmol)
        assertNull(s.cvPercent)
        assertNull(s.comparison)
        assertTrue(s.issues.isEmpty())
        assertEquals("Weekly report", WeeklyReport.notificationTitle(s))
        assertEquals("No readings in the last 7 days.", WeeklyReport.notificationBody(s, GlucoseUnit.MMOL))
        assertTrue(WeeklyReport.formatText(s, GlucoseUnit.MMOL, utc).contains("No readings in the last 7 days."))
    }

    @Test fun `single reading has valid stats and null cv`() {
        val s = WeeklyReport.generate(listOf(Reading(sat11 - 5 * min, 6.0)), sat11, utc)
        assertEquals(1, s.readingCount)
        assertEquals(100, s.inRangePercent)
        assertEquals(0, s.lowPercent)
        assertEquals(0, s.highPercent)
        assertEquals(6.0, s.averageMmol!!, 1e-6)
        assertNull(s.cvPercent)
    }

    @Test fun `steady in-range week has 100 percent in range and no issues`() {
        val readings = steadyWeek()
        val s = WeeklyReport.generate(readings, sat11, utc)
        assertEquals(100, s.inRangePercent)
        assertEquals(0, s.lowPercent)
        assertEquals(0, s.veryLowPercent)
        assertEquals(0, s.highPercent)
        assertEquals(100, s.coveragePercent)
        assertEquals(0, s.cvPercent)
        assertTrue(s.issues.isEmpty())
        assertEquals("Weekly report · 100% in range", WeeklyReport.notificationTitle(s))
        assertEquals("Avg 6.5 mmol/L · No recurring issues this week", WeeklyReport.notificationBody(s, GlucoseUnit.MMOL))
        val text = WeeklyReport.formatText(s, GlucoseUnit.MMOL, utc)
        assertTrue(text.contains("No recurring issues this week."))
        assertTrue(text.contains("Average: 6.5 mmol/L"))
    }

    @Test fun `overnight lows on 3 nights are flagged as high severity and same night is not double counted`() {
        val readings = steadyWeek()
        // Night 1 (Mon->Tue): both 23:30 Mon and 01:30 Tue should count as ONE night (Mon).
        readings.replaceAt(at(2026, 11, 23, 23, 30), 3.4)
        readings.replaceAt(at(2026, 11, 24, 1, 30), 3.2)
        // Night 2 (Wed->Thu): 02:00 Thu counts as Wed night.
        readings.replaceAt(at(2026, 11, 26, 2, 0), 3.5)
        // Night 3 (Thu->Fri): 04:00 Fri counts as Thu night.
        readings.replaceAt(at(2026, 11, 27, 4, 0), 3.3)

        val s = WeeklyReport.generate(readings, sat11, utc)
        val overnight = s.issues.firstOrNull { it.title == "Lows on 3 nights this week" }
        assertNotNull("Expected overnight lows issue in ${s.issues}", overnight)
        assertEquals(WeeklyReport.IssueSeverity.HIGH, overnight!!.severity)
        assertTrue(overnight.detail.contains("Mon, Wed and Thu"))
        assertTrue(overnight.detail.contains("lowest 3.2"))
        assertTrue(WeeklyReport.notificationBody(s, GlucoseUnit.MMOL).contains("Lows on 3 nights this week"))
    }

    @Test fun `overnight lows on 2 nights are moderate severity`() {
        val readings = steadyWeek()
        readings.replaceAt(at(2026, 11, 24, 2, 0), 3.5)
        readings.replaceAt(at(2026, 11, 26, 3, 0), 3.6)

        val s = WeeklyReport.generate(readings, sat11, utc)
        val issue = s.issues.first { it.title == "Lows on 2 nights this week" }
        assertEquals(WeeklyReport.IssueSeverity.MODERATE, issue.severity)
        assertTrue(issue.detail.contains("Mon and Wed"))
    }

    @Test fun `daytime lows and highs are detected by period`() {
        val readings = steadyWeek()
        // 4 mornings with lows -> HIGH severity.
        listOf(23, 24, 25, 26).forEach { day -> readings.replaceAt(at(2026, 11, day, 9, 0), 3.5) }
        // 2 afternoons with lows -> MODERATE.
        listOf(24, 26).forEach { day -> readings.replaceAt(at(2026, 11, day, 14, 0), 3.6) }
        // 3 evenings with highs -> MODERATE.
        listOf(23, 25, 27).forEach { day -> readings.replaceAt(at(2026, 11, day, 20, 0), 12.4) }

        val s = WeeklyReport.generate(readings, sat11, utc)
        val morningLows = s.issues.first { it.title == "Lows on 4 mornings this week" }
        assertEquals(WeeklyReport.IssueSeverity.HIGH, morningLows.severity)
        assertNotNull(s.issues.firstOrNull { it.title == "Lows on 2 afternoons this week" })
        val eveningHighs = s.issues.first { it.title == "Highs on 3 evenings this week" }
        assertEquals(WeeklyReport.IssueSeverity.MODERATE, eveningHighs.severity)
        assertTrue(eveningHighs.detail.contains("highest 12.4"))
    }

    @Test fun `urgent lows report episodes, days, and mgdl formatting`() {
        val readings = steadyWeek()
        // Two episodes on Tue (separated by 2 hours) and one below-sensor reading on Thu.
        readings.replaceAt(at(2026, 11, 24, 10, 0), 2.8)
        readings.replaceAt(at(2026, 11, 24, 10, 5), 2.7)
        readings.replaceAt(at(2026, 11, 24, 12, 0), 2.9)
        readings.replaceAt(at(2026, 11, 26, 15, 0), 2.2, Bound.BELOW)

        val s = WeeklyReport.generate(readings, sat11, utc, unit = GlucoseUnit.MGDL)
        val urgent = s.issues.first { it.title == "Urgent lows on 2 days this week" }
        assertEquals(WeeklyReport.IssueSeverity.HIGH, urgent.severity)
        assertTrue(urgent.detail.contains("3 times"))
        assertTrue(urgent.detail.contains("54 mg/dL"))
        assertTrue(urgent.detail.contains("<40"))
    }

    @Test fun `single urgent low uses singular wording`() {
        val readings = steadyWeek()
        readings.replaceAt(at(2026, 11, 25, 14, 0), 2.8)

        val s = WeeklyReport.generate(readings, sat11, utc)
        val urgent = s.issues.first { it.title == "Urgent low on 1 day this week" }
        assertTrue(urgent.detail.contains("once on Wed"))
    }

    @Test fun `wide glucose swings flag CV above 36 percent`() {
        val readings = steadyWeek()
        // Alternate 3.5 and 13.5 to produce high CV (~60%).
        readings.forEachIndexed { idx, r ->
            readings[idx] = r.copy(mmol = if (idx % 2 == 0) 4.0 else 12.0)
        }
        val s = WeeklyReport.generate(readings, sat11, utc)
        assertTrue(s.cvPercent!! > WeeklyReport.HIGH_CV_PERCENT)
        assertNotNull(s.issues.firstOrNull { it.title.startsWith("Wide glucose swings") })
    }

    @Test fun `week over week comparison flags 5 percent shift up or down`() {
        // Prior week: 60% in range (40% high); this week: 100% in range -> +40%.
        val priorStart = sat11 - 14 * 24 * hour
        val readings = mutableListOf<Reading>()
        var t = priorStart + 5 * min
        var i = 0
        while (t < sat11 - 7 * 24 * hour) {
            readings += Reading(t, if (i % 5 < 3) 6.5 else 12.0)
            t += 5 * min
            i++
        }
        readings += steadyWeek(sat11, mmol = 6.5)

        val up = WeeklyReport.generate(readings, sat11, utc)
        assertNotNull(up.comparison)
        assertEquals(40, up.comparison!!.inRangeDeltaPercent)
        assertNotNull(up.issues.firstOrNull { it.title == "Time in range up 40% from last week" })
        assertTrue(WeeklyReport.formatText(up, GlucoseUnit.MMOL, utc).contains("+40% vs last week"))

        // Now reverse: prior week 100%, this week 60% -> -40%.
        val downReadings = mutableListOf<Reading>()
        downReadings += steadyWeek(sat11 - 7 * 24 * hour - 5 * min, mmol = 6.5)
        var t2 = sat11 - 7 * 24 * hour + 5 * min
        var j = 0
        while (t2 <= sat11) {
            downReadings += Reading(t2, if (j % 5 < 3) 6.5 else 12.0)
            t2 += 5 * min
            j++
        }
        val down = WeeklyReport.generate(downReadings, sat11, utc)
        assertEquals(-40, down.comparison!!.inRangeDeltaPercent)
        assertNotNull(down.issues.firstOrNull { it.title == "Time in range down 40% from last week" })
        assertTrue(WeeklyReport.formatText(down, GlucoseUnit.MMOL, utc).contains("−40% vs last week"))
    }

    @Test fun `signal gaps and low coverage are flagged`() {
        // 3 gaps of 90 minutes in an otherwise full week (> 70% coverage).
        val readings = steadyWeek().filterNot { r ->
            r.timeMillis in (sat11 - 6 * 24 * hour)..(sat11 - 6 * 24 * hour + 90 * min) ||
                r.timeMillis in (sat11 - 4 * 24 * hour)..(sat11 - 4 * 24 * hour + 90 * min) ||
                r.timeMillis in (sat11 - 2 * 24 * hour)..(sat11 - 2 * 24 * hour + 90 * min)
        }
        val s = WeeklyReport.generate(readings, sat11, utc)
        val gapIssue = s.issues.first { it.title == "3 signal gaps over an hour" }
        assertTrue(gapIssue.detail.contains("longest 1 h 35 min") || gapIssue.detail.contains("longest 1 h 40 min"))

        // Low coverage (< 70%) with no internal gaps (only 2 days of readings).
        val shortWeek = steadyWeek(sat11, days = 2)
        val lowCov = WeeklyReport.generate(shortWeek, sat11, utc)
        assertTrue(lowCov.coveragePercent < 70)
        assertNotNull(lowCov.issues.firstOrNull { it.title.startsWith("Missing readings") })
    }

    @Test fun `percentages always sum to 100 and treatments are counted`() {
        // 1 low, 1 in range, 1 high -> 33.33% each -> must sum to 100.
        val readings = listOf(
            Reading(sat11 - 15 * min, 3.5),
            Reading(sat11 - 10 * min, 6.5),
            Reading(sat11 - 5 * min, 11.5),
        )
        val treatments = listOf(sat11 - 14 * min, sat11 - 10 * 24 * hour) // only 1 in this week
        val s = WeeklyReport.generate(readings, sat11, utc, treatments = treatments)
        assertEquals(100, s.lowPercent + s.inRangePercent + s.highPercent)
        assertEquals(1, s.treatmentCount)
        assertTrue(WeeklyReport.formatText(s, GlucoseUnit.MMOL, utc).contains("1 treated low"))

        val s2 = WeeklyReport.generate(readings, sat11, utc, treatments = listOf(sat11 - 14 * min, sat11 - 12 * min))
        assertEquals(2, s2.treatmentCount)
        assertTrue(WeeklyReport.formatText(s2, GlucoseUnit.MMOL, utc).contains("2 treated lows"))
    }

    @Test fun `two occurrences of the same weekday use calendar dates instead of repeating the day name`() {
        val readings = steadyWeek()
        // Both last Saturday (Nov 21 11:30) and this Saturday (Nov 28 10:30) fall in morning of the 7-day window.
        readings.replaceAt(at(2026, 11, 21, 11, 30), 3.5)
        readings.replaceAt(at(2026, 11, 28, 10, 30), 3.4)
        val s = WeeklyReport.generate(readings, sat11, utc)
        val issue = s.issues.first { it.title == "Lows on 2 mornings this week" }
        assertTrue(issue.detail, issue.detail.contains("Nov 21 and Nov 28"))
    }

    @Test fun `overnight lows across a DST fall-back night count as one night`() {
        val london = ZoneId.of("Europe/London")
        val end = at(2026, 10, 31, 11, 0, london)
        val readings = steadyWeek(endMillis = end)
        // Europe/London falls back on 2026-10-25 at 02:00 BST -> 01:00 GMT (a 25-hour day).
        // Lows at 00:30 BST and 06:30 GMT are the same night (Sat->Sun) and must not count as 2 nights.
        readings.replaceAt(at(2026, 10, 25, 0, 30, london), 3.4)
        readings.replaceAt(at(2026, 10, 25, 6, 30, london), 3.3)
        val s = WeeklyReport.generate(readings, end, london)
        assertTrue("Expected no recurring overnight lows issue, got ${s.issues}", s.issues.none { it.title.contains("nights") })
    }

    private fun MutableList<Reading>.replaceAt(time: Long, mmol: Double, bound: Bound? = null) {
        val idx = indexOfFirst { it.timeMillis == time }
        if (idx >= 0) this[idx] = Reading(time, mmol, bound) else add(Reading(time, mmol, bound))
    }
}
