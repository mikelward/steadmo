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

    @Test fun `isRecovering is true only for a visible rise within 45 minutes of treatment`() {
        assertTrue(Treatment.isRecovering(trend(0.07), treatedAt = 0, now = 15 * min))
        assertTrue(Treatment.isRecovering(trend(0.12), treatedAt = 0, now = 15 * min))
        assertTrue(Treatment.isRecovering(trend(0.20), treatedAt = 0, now = 15 * min))
        assertFalse(Treatment.isRecovering(trend(0.02), treatedAt = 0, now = 15 * min))
        assertFalse(Treatment.isRecovering(trend(-0.07), treatedAt = 0, now = 15 * min))
        assertFalse(Treatment.isRecovering(null, treatedAt = 0, now = 15 * min))
        assertFalse(Treatment.isRecovering(trend(0.07), treatedAt = null, now = 15 * min))
        assertFalse(Treatment.isRecovering(trend(0.07), treatedAt = 0, now = 45 * min))
        assertFalse(Treatment.isRecovering(trend(0.07), treatedAt = 20 * min, now = 15 * min))
    }

    @Test fun `recheck picks urgent low, low, recovering, recovered or stale`() {
        val stale = 15 * min
        val now = 15 * min
        assertEquals(Treatment.RecheckOutcome.STALE, Treatment.recheck(null, null, now, treatedAt = 0, staleMillis = stale))
        assertEquals(Treatment.RecheckOutcome.STALE, Treatment.recheck(Reading(0, 3.5), trend(0.0), now, treatedAt = 0, staleMillis = stale))
        assertEquals(Treatment.RecheckOutcome.URGENT_LOW, Treatment.recheck(Reading(13 * min, 2.8), trend(0.0), now, treatedAt = 0, staleMillis = stale))
        assertEquals(Treatment.RecheckOutcome.URGENT_LOW, Treatment.recheck(Reading(13 * min, 3.5), trend(-0.06), now, treatedAt = 0, staleMillis = stale))
        assertEquals(Treatment.RecheckOutcome.RECOVERED, Treatment.recheck(Reading(13 * min, 4.3), trend(0.0), now, treatedAt = 0, staleMillis = stale))
        assertEquals(Treatment.RecheckOutcome.RECOVERING, Treatment.recheck(Reading(13 * min, 3.5), trend(0.07), now, treatedAt = 0, staleMillis = stale))
        assertEquals(Treatment.RecheckOutcome.LOW, Treatment.recheck(Reading(13 * min, 3.5), trend(0.0), now, treatedAt = 0, staleMillis = stale))
        assertEquals(Treatment.RecheckOutcome.LOW, Treatment.recheck(Reading(13 * min, 3.5), null, now, treatedAt = 0, staleMillis = stale))
    }
}
