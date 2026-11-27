package app.steadmo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendTest {
    private fun perMin(mgdl: Double) = mgdl / MGDL_PER_MMOL

    @Test fun arrowsFollowDexcomThresholds() {
        assertEquals(TrendArrow.FLAT, TrendArrow.forRate(perMin(0.9)))
        assertEquals(TrendArrow.FLAT, TrendArrow.forRate(perMin(-0.9)))
        assertEquals(TrendArrow.UP_SLIGHT, TrendArrow.forRate(perMin(1.1)))
        assertEquals(TrendArrow.DOWN_SLIGHT, TrendArrow.forRate(perMin(-1.9)))
        assertEquals(TrendArrow.UP, TrendArrow.forRate(perMin(2.5)))
        assertEquals(TrendArrow.DOWN, TrendArrow.forRate(perMin(-2.1)))
        assertEquals(TrendArrow.DOUBLE_UP, TrendArrow.forRate(perMin(3.0)))
        assertEquals(TrendArrow.DOUBLE_DOWN, TrendArrow.forRate(perMin(-4.0)))
    }

    @Test fun thirtyMinuteChangesFromTheSpec() {
        // 2.5 mmol/L in 30 min is "rising slightly"; 4 is single up; 6 is double up.
        assertEquals(TrendArrow.UP_SLIGHT, TrendArrow.forRate(2.5 / 30))
        assertEquals(TrendArrow.UP, TrendArrow.forRate(4.0 / 30))
        assertEquals(TrendArrow.DOUBLE_UP, TrendArrow.forRate(6.0 / 30))
        assertEquals(TrendArrow.FLAT, TrendArrow.forRate(1.0 / 30))
    }

    private fun series(vararg values: Double) =
        values.mapIndexed { i, v -> Reading(i * 5 * 60_000L, v) }

    @Test fun slopeOfASteadyRise() {
        val trend = TrendCalculator.trend(series(6.0, 6.5, 7.0, 7.5))!!
        assertEquals(0.1, trend.mmolPerMinute, 1e-9)
        assertEquals(TrendArrow.UP_SLIGHT, trend.arrow)
    }

    @Test fun onlyTheLastTwentyMinutesCount() {
        // An old fall followed by flat readings is flat now.
        val trend = TrendCalculator.trend(series(12.0, 10.0, 8.0, 6.0, 6.0, 6.0, 6.0, 6.0, 6.0))!!
        assertEquals(TrendArrow.FLAT, trend.arrow)
    }

    @Test fun tooFewReadingsGiveNoTrend() {
        assertNull(TrendCalculator.trend(emptyList()))
        assertNull(TrendCalculator.trend(series(6.0)))
        assertNull(TrendCalculator.trend(listOf(Reading(0, 6.0), Reading(60_000, 6.5))))
    }

    @Test fun twoReadingsFiveMinutesApartAreEnough() {
        val trend = TrendCalculator.trend(series(6.0, 5.0))!!
        assertEquals(TrendArrow.DOUBLE_DOWN, trend.arrow)
    }

    @Test fun boundedReadingsAreLeftOutOfTheTrend() {
        val readings = series(6.0, 6.5, 7.0).toMutableList()
        readings += Reading(15 * 60_000L, 2.2, Bound.BELOW)
        assertNull(TrendCalculator.trend(readings))
        val earlierBound = listOf(Reading(0, 22.2, Bound.ABOVE), Reading(5 * 60_000L, 6.0), Reading(10 * 60_000L, 6.0))
        assertEquals(TrendArrow.FLAT, TrendCalculator.trend(earlierBound)!!.arrow)
    }

    @Test fun displayShowsTheBound() {
        assertEquals("8.9", Reading(0, 8.9).display)
        assertEquals("<2.2", Reading(0, 2.2, Bound.BELOW).display)
        assertEquals(">22.2", Reading(0, 22.2, Bound.ABOVE).display)
    }

    @Test fun flatReadingsHaveNoSlopeProblem() {
        assertNull(TrendCalculator.trend(listOf(Reading(0, 6.0), Reading(0, 6.1))))
    }

    @Test fun minutesUntilAThreshold() {
        assertEquals(10, TrendCalculator.minutesUntil(5.0, 3.9, -0.11))
        assertNull(TrendCalculator.minutesUntil(5.0, 3.9, 0.1))
        assertNull(TrendCalculator.minutesUntil(9.0, 3.9, -0.1))
        assertNull(TrendCalculator.minutesUntil(9.0, 3.9, 0.0))
    }
}
