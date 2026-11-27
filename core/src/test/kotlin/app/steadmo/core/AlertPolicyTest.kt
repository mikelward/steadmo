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

    @Test fun textFollowsTheDisplayUnit() {
        val low = AlertPolicy().evaluate(Reading(0, 5.1), trend(-0.15))!!
        assertEquals("5.1, −0.15/min. Heading below 3.9.", low.text)
        val lowMgdl = AlertPolicy().evaluate(Reading(0, 5.1), trend(-0.15), GlucoseUnit.MGDL)!!
        assertEquals("92, −2.7/min. Heading below 70.", lowMgdl.text)
        val rise = AlertPolicy().evaluate(Reading(0, 12.0), trend(0.2), GlucoseUnit.MGDL)!!
        assertEquals("Rising fast: 216", rise.title)
        assertEquals("+3.6/min, +108 mg/dL in 30 min at this rate.", rise.text)
        val high = AlertPolicy().evaluate(Reading(0, 8.9), trend(0.12), GlucoseUnit.MGDL)!!
        assertEquals("160, +2.2/min. Heading above 180.", high.text)
        val fall = AlertPolicy().applying(Reading(0, 12.0), trend(-0.2), GlucoseUnit.MGDL)!!
        assertEquals("−3.6/min, 108 mg/dL in 30 min at this rate.", fall.text)
    }

    @Test fun boundedReadingsDoNotAlert() {
        assertNull(AlertPolicy().evaluate(Reading(0, 2.2, Bound.BELOW), trend(-0.2)))
    }

    @Test fun steadyIsQuiet() {
        assertNull(AlertPolicy().evaluate(Reading(0, 6.0), trend(0.01)))
        assertNull(AlertPolicy().evaluate(Reading(0, 6.0), null))
    }

    @Test fun applyingIgnoresTheRepeatLimit() {
        val policy = AlertPolicy()
        policy.evaluate(Reading(0, 12.0), trend(0.2))!!
        assertNull(policy.evaluate(Reading(5 * min, 13.0), trend(0.2)))
        assertEquals(AlertKind.RISING_FAST, policy.applying(Reading(5 * min, 13.0), trend(0.2))!!.kind)
        assertNull(policy.applying(Reading(5 * min, 13.0), trend(0.0)))
    }

    @Test fun sameKindRepeatsOnlyAfterHalfAnHour() {
        val policy = AlertPolicy()
        policy.evaluate(Reading(0, 12.0), trend(0.2))!!
        assertNull(policy.evaluate(Reading(5 * min, 13.0), trend(0.2)))
        assertEquals(AlertKind.RISING_FAST, policy.evaluate(Reading(30 * min, 14.0), trend(0.2))!!.kind)
    }
}
