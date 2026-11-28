package app.steadmo.core

import app.steadmo.core.LowAlert.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class LowAlertTest {
    private val min = 60_000L

    private fun trend(rate: Double) = Trend(rate, TrendArrow.forRate(rate))

    @Test fun `alerts below 3_9 when not urgent, and clears at or above 3_9`() {
        assertEquals(Decision.ALERT, LowAlert.decide(Reading(0, 3.4), trend(0.0), null, null, null))
        assertEquals(Decision.ALERT, LowAlert.decide(Reading(0, 3.4), null, null, null, null))
        assertEquals(Decision.CLEAR, LowAlert.decide(Reading(0, 3.9), trend(0.0), null, null, null))
        assertEquals(Decision.CLEAR, LowAlert.decide(Reading(0, 5.0), trend(0.0), null, null, null))
    }

    @Test fun `is superseded when UrgentLow applies`() {
        assertEquals(Decision.SUPERSEDED, LowAlert.decide(Reading(0, 2.8), trend(0.0), null, null, null))
        assertEquals(Decision.SUPERSEDED, LowAlert.decide(Reading(0, 2.2, Bound.BELOW), null, null, null, null))
        assertEquals(Decision.SUPERSEDED, LowAlert.decide(Reading(0, 3.5), trend(-0.06), null, null, null))
    }

    @Test fun `keeps quiet for 15 minutes after alerting, then alerts again if ignored`() {
        assertEquals(Decision.KEEP, LowAlert.decide(Reading(5 * min, 3.4), trend(0.0), null, lastAlertedAt = 0, treatedAt = null))
        assertEquals(Decision.ALERT, LowAlert.decide(Reading(15 * min, 3.4), trend(0.0), null, lastAlertedAt = 0, treatedAt = null))
    }

    @Test fun `OK or Treated snoozes Low for 15 minutes`() {
        assertEquals(Decision.SNOOZED, LowAlert.decide(Reading(10 * min, 3.4), trend(0.0), acknowledgedAt = 0, lastAlertedAt = null, treatedAt = null))
        assertEquals(Decision.ALERT, LowAlert.decide(Reading(15 * min, 3.4), trend(0.0), acknowledgedAt = 0, lastAlertedAt = null, treatedAt = null))
        assertEquals(Decision.SNOOZED, LowAlert.decide(Reading(10 * min, 3.4), trend(0.0), acknowledgedAt = null, lastAlertedAt = null, treatedAt = 0))
        assertEquals(Decision.ALERT, LowAlert.decide(Reading(15 * min, 3.4), trend(0.0), acknowledgedAt = null, lastAlertedAt = null, treatedAt = 0))
    }

    @Test fun `stays quiet after Treated while visibly rising, but alerts if untreated or stalled`() {
        assertEquals(Decision.RECOVERING, LowAlert.decide(Reading(15 * min, 3.5), trend(0.07), null, null, treatedAt = 0))
        assertEquals(Decision.RECOVERING, LowAlert.decide(Reading(30 * min, 3.6), trend(0.12), null, null, treatedAt = 0))
        // Untreated rising low still alerts.
        assertEquals(Decision.ALERT, LowAlert.decide(Reading(15 * min, 3.5), trend(0.07), null, null, treatedAt = null))
        // Stalled flat after 15 min alerts again.
        assertEquals(Decision.ALERT, LowAlert.decide(Reading(20 * min, 3.5), trend(0.0), null, null, treatedAt = 0))
        // After the 45-minute hold window, even a rising low alerts again.
        assertEquals(Decision.ALERT, LowAlert.decide(Reading(45 * min, 3.5), trend(0.07), null, null, treatedAt = 0))
    }
}
