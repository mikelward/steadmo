package app.steadmo.core

import app.steadmo.core.Heartbeat.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class HeartbeatTest {
    private val min = 60_000L

    @Test fun `nothing to miss before the first reading`() {
        assertEquals(Decision.Idle, Heartbeat.check(null, 100 * min, null))
    }

    @Test fun `quiet for under 15 minutes checks again at 15`() {
        assertEquals(Decision.CheckAt(15 * min), Heartbeat.check(0, 14 * min, null))
    }

    @Test fun `warns at 15 minutes, then every 30 while it stays quiet`() {
        assertEquals(Decision.Warn(15, 45 * min), Heartbeat.check(0, 15 * min, null))
        assertEquals(Decision.CheckAt(45 * min), Heartbeat.check(0, 20 * min, lastWarned = 15 * min))
        assertEquals(Decision.Warn(45, 75 * min), Heartbeat.check(0, 45 * min, lastWarned = 15 * min))
    }

    @Test fun `stops repeating after two hours`() {
        assertEquals(Decision.Warn(120, 150 * min), Heartbeat.check(0, 120 * min, lastWarned = 90 * min))
        assertEquals(Decision.Idle, Heartbeat.check(0, 121 * min, lastWarned = 120 * min))
    }

    @Test fun `a warning before the latest reading doesn't count`() {
        assertEquals(Decision.Warn(16, 66 * min), Heartbeat.check(20 * min, 36 * min, lastWarned = 10 * min))
    }
}
