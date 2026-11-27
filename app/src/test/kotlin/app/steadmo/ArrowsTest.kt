package app.steadmo

import app.steadmo.core.TrendArrow
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrowsTest {
    @Test fun `each arrow has its own drawable`() {
        val drawables = (TrendArrow.entries.map { it.drawable() } + (null as TrendArrow?).drawable()).toSet()
        assertEquals(TrendArrow.entries.size + 1, drawables.size)
    }

    @Test fun `range colors switch at 3_9 and 10`() {
        assertEquals(LowColor, rangeColor(3.8))
        assertEquals(InRangeColor, rangeColor(3.9))
        assertEquals(InRangeColor, rangeColor(10.0))
        assertEquals(HighColor, rangeColor(10.1))
    }
}
