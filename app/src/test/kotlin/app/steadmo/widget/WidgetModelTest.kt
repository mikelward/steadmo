package app.steadmo.widget

import app.steadmo.GlucoseState
import app.steadmo.MINUTE
import app.steadmo.R
import app.steadmo.Samples
import app.steadmo.core.RangeSide
import java.text.SimpleDateFormat
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetModelTest {
    private val fmt = SimpleDateFormat("HH:mm").apply { timeZone = TimeZone.getTimeZone("UTC") }

    @Test fun `nothing is shown until the disclaimer is accepted`() {
        assertEquals(WidgetModel.NeedsDisclaimer, WidgetModel.of(Samples.rising(), false, Samples.NOW, fmt))
    }

    @Test fun `waiting before the first reading`() {
        assertEquals(WidgetModel.Waiting, WidgetModel.of(GlucoseState(), true, Samples.NOW, fmt))
    }

    @Test fun `a reading with its arrow, time, rate and mode`() {
        val m = WidgetModel.of(Samples.rising(), true, Samples.NOW, fmt) as WidgetModel.Showing
        assertEquals("9.4", m.value)
        assertEquals(R.drawable.ic_trend_up, m.arrow)
        assertEquals("Rising", m.arrowLabel)
        assertEquals(RangeSide.IN_RANGE, m.side)
        assertFalse(m.stale)
        assertEquals(fmt.format(Samples.NOW), m.time)
        assertEquals("+0.12/min", m.rate)
        assertEquals("On", m.autoMode)
    }

    @Test fun `old readings are stale, and a single one has no trend`() {
        val m = WidgetModel.of(Samples.single(), true, Samples.NOW + 11 * MINUTE, fmt) as WidgetModel.Showing
        assertTrue(m.stale)
        assertNull(m.rate)
        assertEquals("No trend yet", m.arrowLabel)
        assertEquals(R.drawable.ic_trend_unknown, m.arrow)
    }

    @Test fun `falling fast keeps its sign`() {
        val m = WidgetModel.of(Samples.fallingFast(), true, Samples.NOW, fmt) as WidgetModel.Showing
        assertTrue(m.rate!!, m.rate!!.startsWith("−"))
    }
}
