package app.steadmo.core

import app.steadmo.core.UrgentLow.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrgentLowTest {
    private val min = 60_000L

    @Test fun `urgent below 3_0 or under the sensor's range`() {
        assertTrue(Reading(0, 2.9).urgentLow())
        assertFalse(Reading(0, 3.0).urgentLow())
        assertTrue(Reading(0, 2.2, Bound.BELOW).urgentLow())
        assertFalse(Reading(0, 22.2, Bound.ABOVE).urgentLow())
    }

    @Test fun `alerts on every urgent reading until acknowledged`() {
        assertEquals(Decision.ALERT, UrgentLow.decide(Reading(0, 2.8), null))
        assertEquals(Decision.ALERT, UrgentLow.decide(Reading(5 * min, 2.7), null))
    }

    @Test fun `OK quiets it for 15 minutes`() {
        assertEquals(Decision.SNOOZED, UrgentLow.decide(Reading(10 * min, 2.7), acknowledgedAt = 1 * min))
        assertEquals(Decision.ALERT, UrgentLow.decide(Reading(16 * min, 2.7), acknowledgedAt = 1 * min))
        // An OK from the future (clock change) doesn't count.
        assertEquals(Decision.ALERT, UrgentLow.decide(Reading(0, 2.7), acknowledgedAt = 5 * min))
    }

    @Test fun `takes over the screen after 5 ignored minutes`() {
        assertFalse(UrgentLow.escalates(null, 10 * min))
        assertFalse(UrgentLow.escalates(0, 4 * min))
        assertTrue(UrgentLow.escalates(0, 5 * min))
    }

    @Test fun `clears once glucose is out of the urgent range`() {
        assertEquals(Decision.CLEAR, UrgentLow.decide(Reading(0, 3.4), acknowledgedAt = null))
    }

    @Test fun `escalates when at or below 3_9 and projected below 3_0 within 20 minutes`() {
        val fallingSlow = Trend(-0.06, TrendArrow.DOWN_SLIGHT)
        assertEquals(8, UrgentLow.minutesUntil(Reading(0, 3.5), fallingSlow))
        assertTrue(UrgentLow.isUrgent(Reading(0, 3.5), fallingSlow))
        assertEquals(15, UrgentLow.minutesUntil(Reading(0, 3.9), fallingSlow))
        assertTrue(UrgentLow.isUrgent(Reading(0, 3.9), fallingSlow))
        // 70 mg/dL is on the 3.9 line too.
        assertEquals(15, UrgentLow.minutesUntil(Reading(0, 70 / MGDL_PER_MMOL), fallingSlow))
        assertEquals(Decision.ALERT, UrgentLow.decide(Reading(0, 3.5), acknowledgedAt = null, trend = fallingSlow))

        // Above 3.9, flat, rising, or already below 3.0 do not produce a minutes-until prediction.
        assertEquals(null, UrgentLow.minutesUntil(Reading(0, 4.2), Trend(-0.15, TrendArrow.DOWN)))
        assertFalse(UrgentLow.isUrgent(Reading(0, 4.2), Trend(-0.15, TrendArrow.DOWN)))
        assertEquals(null, UrgentLow.minutesUntil(Reading(0, 3.5), Trend(-0.02, TrendArrow.FLAT)))
        assertEquals(null, UrgentLow.minutesUntil(Reading(0, 3.5), Trend(0.06, TrendArrow.UP_SLIGHT)))
        assertEquals(null, UrgentLow.minutesUntil(Reading(0, 3.5), null))
        assertEquals(null, UrgentLow.minutesUntil(Reading(0, 2.8), fallingSlow))
        assertEquals(null, UrgentLow.minutesUntil(Reading(0, 2.2, Bound.BELOW), fallingSlow))
    }

    @Test fun `Treated quiets urgent low for 15 minutes even through a lag dip`() {
        assertEquals(Decision.SNOOZED, UrgentLow.decide(Reading(10 * min, 2.8), acknowledgedAt = null, treatedAt = 0))
        assertEquals(Decision.ALERT, UrgentLow.decide(Reading(15 * min, 2.8), acknowledgedAt = null, treatedAt = 0))
        // A treatment timestamp in the future doesn't suppress alerts.
        assertEquals(Decision.ALERT, UrgentLow.decide(Reading(0, 2.8), acknowledgedAt = null, treatedAt = 5 * min))
    }
}
