package app.steadmo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertPolicyTest {
    private val min = 60_000L

    private fun trend(rate: Double) = Trend(rate, TrendArrow.forRate(rate))

    @Test fun projectedLowComesFirst() {
        val alert = AlertPolicy().evaluate(Reading(0, 5.1), trend(-0.15))!!
        assertEquals(AlertKind.LOW_SOON, alert.kind)
        assertEquals("Low in about 8 min", alert.title)
        assertEquals(TrendArrow.DOWN, alert.arrow)
    }

    @Test fun fastFallWellAboveLow() {
        val alert = AlertPolicy().evaluate(Reading(0, 12.0), trend(-0.2))!!
        assertEquals(AlertKind.FALLING_FAST, alert.kind)
    }

    @Test fun fastRise() {
        val alert = AlertPolicy().evaluate(Reading(0, 12.0), trend(0.2))!!
        assertEquals(AlertKind.RISING_FAST, alert.kind)
    }

    @Test fun highSoonNeedsASingleArrowOrMore() {
        assertEquals(AlertKind.HIGH_SOON, AlertPolicy().evaluate(Reading(0, 8.9), trend(0.12))!!.kind)
        assertNull(AlertPolicy().evaluate(Reading(0, 9.5), trend(0.07)))
    }

    @Test fun steadyIsQuiet() {
        assertNull(AlertPolicy().evaluate(Reading(0, 6.0), trend(0.01)))
        assertNull(AlertPolicy().evaluate(Reading(0, 6.0), null))
    }

    @Test fun sameKindRepeatsOnlyAfterHalfAnHour() {
        val policy = AlertPolicy()
        policy.evaluate(Reading(0, 12.0), trend(0.2))!!
        assertNull(policy.evaluate(Reading(5 * min, 13.0), trend(0.2)))
        assertEquals(AlertKind.RISING_FAST, policy.evaluate(Reading(30 * min, 14.0), trend(0.2))!!.kind)
    }
}
