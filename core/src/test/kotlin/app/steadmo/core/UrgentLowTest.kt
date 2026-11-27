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
}
