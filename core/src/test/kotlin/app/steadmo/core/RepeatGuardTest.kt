package app.steadmo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatGuardTest {
    @Test fun `blocks the same shortcut for a minute`() {
        assertTrue(RepeatGuard.allows(null, 0))
        assertFalse(RepeatGuard.allows(1_000, 1_000))
        assertFalse(RepeatGuard.allows(1_000, 60_999))
        assertTrue(RepeatGuard.allows(1_000, 61_000))
        assertTrue(RepeatGuard.allows(1_000, 500))
    }

    @Test fun `every meal is one shortcut, Boost and Ease-off are their own`() {
        assertEquals(RepeatGuard.key(CamapsGoal.Meal(20)), RepeatGuard.key(CamapsGoal.Meal(45)))
        assertEquals("boost", RepeatGuard.key(CamapsGoal.BOOST))
        assertEquals("ease-off", RepeatGuard.key(CamapsGoal.EASE_OFF))
    }
}
