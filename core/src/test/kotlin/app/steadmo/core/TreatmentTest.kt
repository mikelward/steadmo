package app.steadmo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TreatmentTest {
    private val min = 60_000L
    private fun trend(rate: Double) = Trend(rate, TrendArrow.forRate(rate))

    @Test fun `a treatment explains rises for 45 minutes, not falls`() {
        assertTrue(Treatment.explains(AlertKind.RISING_FAST, 0, 44 * min))
        assertTrue(Treatment.explains(AlertKind.HIGH_SOON, 0, 10 * min))
        assertFalse(Treatment.explains(AlertKind.RISING_FAST, 0, 45 * min))
        assertFalse(Treatment.explains(AlertKind.FALLING_FAST, 0, 10 * min))
        assertFalse(Treatment.explains(AlertKind.RISING_FAST, null, 10 * min))
        assertFalse(Treatment.explains(AlertKind.RISING_FAST, 20 * min, 10 * min))
    }

    @Test fun `a treated low holds off the rise alert after it`() {
        assertNull(AlertPolicy().evaluate(Reading(30 * min, 12.0), trend(0.2), treatedAt = 0))
        assertEquals(AlertKind.RISING_FAST, AlertPolicy().evaluate(Reading(50 * min, 12.0), trend(0.2), treatedAt = 0)!!.kind)
    }

    @Test fun `lows are offered Treated`() {
        assertTrue(AlertKind.LOW_SOON.isLow)
        assertTrue(AlertKind.FALLING_FAST.isLow)
        assertFalse(AlertKind.RISING_FAST.isLow)
        assertFalse(AlertKind.HIGH_SOON.isLow)
    }
}
