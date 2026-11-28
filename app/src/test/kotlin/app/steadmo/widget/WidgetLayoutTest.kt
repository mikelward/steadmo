package app.steadmo.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {
    @Test fun `small shows only the reading`() {
        val l = WidgetLayout.of(DpSize(250.dp, 90.dp))
        assertFalse(l.buttons)
        assertNull(l.graph)
    }

    @Test fun `medium adds the graph`() {
        assertEquals(78.dp, WidgetLayout.of(DpSize(320.dp, 200.dp)).graph)
    }

    @Test fun `the graph gives way to the buttons at the narrow boundary`() {
        // 250 x 320: three button rows leave no room for a graph.
        val l = WidgetLayout.of(DpSize(250.dp, 320.dp))
        assertTrue(l.buttons)
        assertEquals(2, l.mealRows)
        assertNull(l.graph)
    }

    @Test fun `full size has room for everything`() {
        val l = WidgetLayout.of(DpSize(360.dp, 480.dp))
        assertEquals(1, l.mealRows)
        assertEquals(120.dp, l.graph)
    }

    @Test fun `large font scale splits meal buttons into two rows`() {
        val l = WidgetLayout.of(DpSize(360.dp, 480.dp), fontScale = 1.5f)
        assertEquals(2, l.mealRows)
    }
}
