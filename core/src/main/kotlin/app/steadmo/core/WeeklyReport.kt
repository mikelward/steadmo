package app.steadmo.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Scheduling for the weekly report (SPEC.md §4.16): by default Saturday at 11:00
 * local time, adjustable in Settings.
 */
object WeeklyReportSchedule {
    val DEFAULT_DAY: DayOfWeek = DayOfWeek.SATURDAY
    val DEFAULT_TIME: LocalTime = LocalTime.of(11, 0)

    /** A report that came due this long ago without firing is still posted (e.g. after a reboot). */
    const val LATE_MILLIS = 24 * 60 * 60_000L

    /**
     * When the weekly report should next fire: [pending], the one already set,
     * if it came due under [LATE_MILLIS] ago without firing; else the next
     * occurrence of [day] at [time] in [zone] strictly after [now].
     */
    fun nextAt(
        now: Long,
        day: DayOfWeek,
        time: LocalTime,
        zone: ZoneId,
        pending: Long? = null,
    ): Long {
        if (pending != null && pending <= now && now - pending < LATE_MILLIS) return pending
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val candidateDate = today.with(TemporalAdjusters.nextOrSame(day))
        val candidate = ZonedDateTime.of(candidateDate, time, zone).toInstant().toEpochMilli()
        return if (candidate > now) {
            candidate
        } else {
            ZonedDateTime.of(candidateDate.plusWeeks(1), time, zone).toInstant().toEpochMilli()
        }
    }
}

/**
 * Weekly glucose trends and recurring issues (SPEC.md §4.16), computed on the
 * device from the last 14 days of readings. Factual observations only: never
 * dosing advice.
 */
object WeeklyReport {
    const val WINDOW_MILLIS = 7 * 24 * 60 * 60_000L
    const val SIGNAL_GAP_MILLIS = 60 * 60_000L

    /** At least about a day of 5-minute readings in the prior week before comparing. */
    const val MIN_COMPARISON_READINGS = 288

    /** Standard CGM consensus ceiling for coefficient of variation (percent). */
    const val HIGH_CV_PERCENT = 36

    private const val FIRST_READING_CREDIT_MILLIS = 5 * 60_000L
    private const val URGENT_EPISODE_GAP_MILLIS = 30 * 60_000L

    enum class IssueSeverity { HIGH, MODERATE, INFO }

    data class ReportIssue(
        val severity: IssueSeverity,
        val title: String,
        val detail: String,
    )

    data class WeekComparison(
        val priorInRangePercent: Int,
        val inRangeDeltaPercent: Int,
        val averageDeltaMmol: Double,
        val lowDeltaPercent: Int,
        val highDeltaPercent: Int,
    )

    data class Summary(
        val periodStart: Long,
        val periodEnd: Long,
        val readingCount: Int,
        val coveragePercent: Int,
        val averageMmol: Double?,
        val lowPercent: Int,
        val veryLowPercent: Int,
        val inRangePercent: Int,
        val highPercent: Int,
        val cvPercent: Int?,
        val treatmentCount: Int,
        val comparison: WeekComparison?,
        val issues: List<ReportIssue>,
    )

    private enum class DayPeriod(val plural: String, val rangeLabel: String) {
        OVERNIGHT("nights", "between 23:00 and 07:00"),
        MORNING("mornings", "between 07:00 and 12:00"),
        AFTERNOON("afternoons", "between 12:00 and 17:00"),
        EVENING("evenings", "between 17:00 and 23:00"),
    }

    private data class BucketStats(
        val lowPercent: Int,
        val veryLowPercent: Int,
        val inRangePercent: Int,
        val highPercent: Int,
        val averageMmol: Double,
    )

    fun generate(
        readings: List<Reading>,
        now: Long,
        zone: ZoneId,
        unit: GlucoseUnit = GlucoseUnit.MMOL,
        treatments: List<Long> = emptyList(),
    ): Summary {
        val periodStart = now - WINDOW_MILLIS
        val priorStart = periodStart - WINDOW_MILLIS
        val week = readings.filter { it.timeMillis in periodStart..now }.sortedBy { it.timeMillis }
        val prior = readings.filter { it.timeMillis in priorStart until periodStart }
        val weekTreatments = treatments.count { it in periodStart..now }

        if (week.isEmpty()) {
            return Summary(
                periodStart = periodStart,
                periodEnd = now,
                readingCount = 0,
                coveragePercent = 0,
                averageMmol = null,
                lowPercent = 0,
                veryLowPercent = 0,
                inRangePercent = 0,
                highPercent = 0,
                cvPercent = null,
                treatmentCount = weekTreatments,
                comparison = null,
                issues = emptyList(),
            )
        }

        val stats = computeStats(week)
        val coverage = computeCoverage(week)
        val cv = computeCv(week, stats.averageMmol)
        val comparison = if (prior.size >= MIN_COMPARISON_READINGS) {
            val prev = computeStats(prior)
            WeekComparison(
                priorInRangePercent = prev.inRangePercent,
                inRangeDeltaPercent = stats.inRangePercent - prev.inRangePercent,
                averageDeltaMmol = stats.averageMmol - prev.averageMmol,
                lowDeltaPercent = stats.lowPercent - prev.lowPercent,
                highDeltaPercent = stats.highPercent - prev.highPercent,
            )
        } else {
            null
        }

        val issues = detectIssues(week, zone, unit, stats, coverage, cv, comparison)

        return Summary(
            periodStart = periodStart,
            periodEnd = now,
            readingCount = week.size,
            coveragePercent = coverage,
            averageMmol = stats.averageMmol,
            lowPercent = stats.lowPercent,
            veryLowPercent = stats.veryLowPercent,
            inRangePercent = stats.inRangePercent,
            highPercent = stats.highPercent,
            cvPercent = cv,
            treatmentCount = weekTreatments,
            comparison = comparison,
            issues = issues,
        )
    }

    fun notificationTitle(summary: Summary): String =
        if (summary.readingCount == 0) "Weekly report" else "Weekly report · ${summary.inRangePercent}% in range"

    fun notificationBody(summary: Summary, unit: GlucoseUnit): String {
        if (summary.readingCount == 0) return "No readings in the last 7 days."
        if (summary.issues.isNotEmpty()) {
            return summary.issues.take(2).joinToString(" · ") { it.title }
        }
        val avg = summary.averageMmol?.let { "Avg ${unit.value(it)} ${unit.label}" }
        return listOfNotNull(avg, "No recurring issues this week").joinToString(" · ")
    }

    fun formatText(summary: Summary, unit: GlucoseUnit, zone: ZoneId): String {
        val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        val startStr = Instant.ofEpochMilli(summary.periodStart).atZone(zone).format(fmt)
        val endStr = Instant.ofEpochMilli(summary.periodEnd).atZone(zone).format(fmt)
        val sb = StringBuilder()
        sb.appendLine("Steadmo weekly report ($startStr – $endStr)")
        if (summary.readingCount == 0) {
            sb.appendLine("No readings in the last 7 days.")
            return sb.toString().trimEnd()
        }
        val delta = summary.comparison?.let { c ->
            val sign = if (c.inRangeDeltaPercent >= 0) "+" else "−"
            " ($sign${abs(c.inRangeDeltaPercent)}% vs last week)"
        } ?: ""
        sb.appendLine(
            "In range (${unit.value(LOW_MMOL)}–${unit.value(HIGH_MMOL)} ${unit.label}): " +
                "${summary.inRangePercent}%$delta",
        )
        sb.appendLine(
            "Low (<${unit.value(LOW_MMOL)}): ${summary.lowPercent}% · " +
                "Very low (<${unit.value(URGENT_LOW_MMOL)}): ${summary.veryLowPercent}% · " +
                "High (>${unit.value(HIGH_MMOL)}): ${summary.highPercent}%",
        )
        val avgPart = summary.averageMmol?.let { "Average: ${unit.value(it)} ${unit.label}" }
        val cvPart = summary.cvPercent?.let { "Variability (CV): $it%" }
        val covPart = "Coverage: ${summary.coveragePercent}%"
        sb.appendLine(listOfNotNull(avgPart, cvPart, covPart).joinToString(" · "))
        if (summary.treatmentCount > 0) {
            val word = if (summary.treatmentCount == 1) "treated low" else "treated lows"
            sb.appendLine("Recorded: ${summary.treatmentCount} $word")
        }
        sb.appendLine()
        if (summary.issues.isEmpty()) {
            sb.appendLine("No recurring issues this week.")
        } else {
            sb.appendLine("Trends and issues:")
            summary.issues.forEach { issue ->
                sb.appendLine("- ${issue.title}: ${issue.detail}")
            }
        }
        return sb.toString().trimEnd()
    }

    private fun computeStats(readings: List<Reading>): BucketStats {
        val total = readings.size
        var low = 0
        var veryLow = 0
        var inRange = 0
        var high = 0
        var sum = 0.0
        for (r in readings) {
            sum += r.mmol
            if (r.urgentLow()) veryLow++
            when (r.side()) {
                RangeSide.LOW -> low++
                RangeSide.IN_RANGE -> inRange++
                RangeSide.HIGH -> high++
            }
        }
        val (lowPct, inRangePct, highPct) = roundedPercents(low, inRange, high, total)
        val veryLowPct = (veryLow * 100.0 / total).roundToInt().coerceAtMost(lowPct)
        return BucketStats(
            lowPercent = lowPct,
            veryLowPercent = veryLowPct,
            inRangePercent = inRangePct,
            highPercent = highPct,
            averageMmol = sum / total,
        )
    }

    /** Largest-remainder rounding so low + inRange + high always equals 100. */
    private fun roundedPercents(a: Int, b: Int, c: Int, total: Int): Triple<Int, Int, Int> {
        val exact = doubleArrayOf(a * 100.0 / total, b * 100.0 / total, c * 100.0 / total)
        val floors = IntArray(3) { exact[it].toInt() }
        var remainder = 100 - floors.sum()
        val order = (0..2).sortedByDescending { exact[it] - floors[it] }
        for (i in 0 until remainder) {
            floors[order[i]]++
        }
        return Triple(floors[0], floors[1], floors[2])
    }

    private fun computeCoverage(sorted: List<Reading>): Int {
        var covered = FIRST_READING_CREDIT_MILLIS
        for (i in 1 until sorted.size) {
            val dt = sorted[i].timeMillis - sorted[i - 1].timeMillis
            covered += dt.coerceIn(0L, MAX_READING_GAP_MILLIS)
        }
        return ((covered.toDouble() / WINDOW_MILLIS) * 100).roundToInt().coerceIn(0, 100)
    }

    private fun computeCv(readings: List<Reading>, mean: Double): Int? {
        if (readings.size < 2 || mean <= 0.0) return null
        val variance = readings.sumOf { val d = it.mmol - mean; d * d } / readings.size
        return (sqrt(variance) / mean * 100.0).roundToInt()
    }

    private fun detectIssues(
        week: List<Reading>,
        zone: ZoneId,
        unit: GlucoseUnit,
        stats: BucketStats,
        coverage: Int,
        cv: Int?,
        comparison: WeekComparison?,
    ): List<ReportIssue> {
        val issues = mutableListOf<ReportIssue>()

        // 1. Urgent lows (< 3.0 mmol/L or below sensor range).
        val urgentReadings = week.filter { it.urgentLow() }
        if (urgentReadings.isNotEmpty()) {
            val days = urgentReadings.map { Instant.ofEpochMilli(it.timeMillis).atZone(zone).toLocalDate() }.distinct().sorted()
            var episodes = 1
            for (i in 1 until urgentReadings.size) {
                if (urgentReadings[i].timeMillis - urgentReadings[i - 1].timeMillis > URGENT_EPISODE_GAP_MILLIS) {
                    episodes++
                }
            }
            val lowest = urgentReadings.minBy { it.mmol }
            val title = if (days.size == 1) "Urgent low on 1 day this week" else "Urgent lows on ${days.size} days this week"
            val timesWord = if (episodes == 1) "once" else "$episodes times"
            issues += ReportIssue(
                severity = IssueSeverity.HIGH,
                title = title,
                detail = "Below ${unit.value(URGENT_LOW_MMOL)} ${unit.label} $timesWord on ${formatDays(days)} (lowest ${unit.reading(lowest)}).",
            )
        }

        // 2. Recurring lows by time of day (overnight first, then daytime periods).
        val lowsByPeriod = DayPeriod.entries.associateWith { mutableMapOf<LocalDate, MutableList<Reading>>() }
        val highsByPeriod = DayPeriod.entries.associateWith { mutableMapOf<LocalDate, MutableList<Reading>>() }
        for (r in week) {
            val side = r.side()
            if (side == RangeSide.IN_RANGE) continue
            val zdt = Instant.ofEpochMilli(r.timeMillis).atZone(zone)
            val period = periodOf(zdt.hour)
            // Overnight (23:00–06:59) belongs to the evening's date so 23:30 and 01:30 count as one night.
            val keyDate = if (period == DayPeriod.OVERNIGHT) zdt.minusHours(7).toLocalDate() else zdt.toLocalDate()
            val target = if (side == RangeSide.LOW) lowsByPeriod.getValue(period) else highsByPeriod.getValue(period)
            target.getOrPut(keyDate) { mutableListOf() }.add(r)
        }

        for (period in DayPeriod.entries) {
            val byDate = lowsByPeriod.getValue(period)
            if (byDate.size >= 2) {
                val dates = byDate.keys.sorted()
                val lowest = byDate.values.flatten().minBy { it.mmol }
                val severity = when {
                    period == DayPeriod.OVERNIGHT && dates.size >= 3 -> IssueSeverity.HIGH
                    dates.size >= 4 -> IssueSeverity.HIGH
                    else -> IssueSeverity.MODERATE
                }
                issues += ReportIssue(
                    severity = severity,
                    title = "Lows on ${dates.size} ${period.plural} this week",
                    detail = "Below ${unit.value(LOW_MMOL)} ${unit.label} ${period.rangeLabel} on ${formatDays(dates)} (lowest ${unit.reading(lowest)}).",
                )
            }
        }

        // 3. Recurring highs by time of day (3+ days in a period).
        for (period in DayPeriod.entries) {
            val byDate = highsByPeriod.getValue(period)
            if (byDate.size >= 3) {
                val dates = byDate.keys.sorted()
                val highest = byDate.values.flatten().maxBy { it.mmol }
                issues += ReportIssue(
                    severity = IssueSeverity.MODERATE,
                    title = "Highs on ${dates.size} ${period.plural} this week",
                    detail = "Above ${unit.value(HIGH_MMOL)} ${unit.label} ${period.rangeLabel} on ${formatDays(dates)} (highest ${unit.reading(highest)}).",
                )
            }
        }

        // 4. High variability (CV > 36%).
        if (cv != null && cv > HIGH_CV_PERCENT) {
            issues += ReportIssue(
                severity = IssueSeverity.MODERATE,
                title = "Wide glucose swings (CV $cv%)",
                detail = "Variability above $HIGH_CV_PERCENT% usually means sharp rises or drops.",
            )
        }

        // 5. Week-over-week time in range shift (>= 5 percentage points).
        if (comparison != null && abs(comparison.inRangeDeltaPercent) >= 5) {
            val up = comparison.inRangeDeltaPercent > 0
            val delta = abs(comparison.inRangeDeltaPercent)
            issues += ReportIssue(
                severity = IssueSeverity.INFO,
                title = if (up) "Time in range up $delta% from last week" else "Time in range down $delta% from last week",
                detail = "${stats.inRangePercent}% this week, compared with ${comparison.priorInRangePercent}% last week.",
            )
        }

        // 6. Signal gaps (3+ gaps over an hour, or coverage under 70%).
        var longGaps = 0
        var longestGap = 0L
        for (i in 1 until week.size) {
            val dt = week[i].timeMillis - week[i - 1].timeMillis
            if (dt > SIGNAL_GAP_MILLIS) {
                longGaps++
                if (dt > longestGap) longestGap = dt
            }
        }
        if (longGaps >= 3 || coverage < 70) {
            val title = if (coverage < 70) {
                "Missing readings ($coverage% coverage)"
            } else {
                "$longGaps signal gaps over an hour"
            }
            val detail = if (longGaps > 0) {
                val gapWord = if (longGaps == 1) "1 gap" else "$longGaps gaps"
                "$gapWord over an hour (longest ${formatDuration(longestGap / 60_000)}). Coverage was $coverage%."
            } else {
                "Readings covered $coverage% of the last 7 days."
            }
            issues += ReportIssue(severity = IssueSeverity.INFO, title = title, detail = detail)
        }

        return issues.sortedBy { it.severity.ordinal }
    }

    private fun periodOf(hour: Int): DayPeriod = when (hour) {
        in 7..11 -> DayPeriod.MORNING
        in 12..16 -> DayPeriod.AFTERNOON
        in 17..22 -> DayPeriod.EVENING
        else -> DayPeriod.OVERNIGHT
    }

    private fun formatDays(dates: List<LocalDate>): String {
        val duplicateWeekday = dates.map { it.dayOfWeek }.distinct().size < dates.size
        val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        val names = if (duplicateWeekday) {
            dates.map { it.format(fmt) }
        } else {
            dates.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US) }
        }
        return when (names.size) {
            1 -> names[0]
            2 -> "${names[0]} and ${names[1]}"
            else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
        }
    }

    private fun formatDuration(minutes: Long): String {
        val hours = minutes / 60
        val rem = minutes % 60
        return if (rem == 0L) "$hours h" else "$hours h $rem min"
    }
}
