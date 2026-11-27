package app.steadmo

import android.app.Application
import android.service.quicksettings.Tile
import androidx.test.core.app.ApplicationProvider
import app.steadmo.core.GlucoseUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TilesTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `the glucose tile shows value, unit, trend and age`() {
        val c = GlucoseTileContent.of(Samples.rising(), Samples.NOW + 3 * MINUTE)
        assertEquals("9.4 mmol/L", c.label)
        assertEquals("Rising · 3 min ago", c.subtitle)
        assertEquals(R.drawable.ic_trend_up, c.icon)
        assertEquals(Tile.STATE_ACTIVE, c.state)
        assertEquals("Rising · just now", GlucoseTileContent.of(Samples.rising(), Samples.NOW).subtitle)
        assertEquals("169 mg/dL", GlucoseTileContent.of(Samples.rising().copy(unit = GlucoseUnit.MGDL), Samples.NOW).label)
    }

    @Test fun `no value once stale, nor before the first reading`() {
        val stale = GlucoseTileContent.of(Samples.rising(), Samples.NOW + 11 * MINUTE)
        assertEquals("Glucose", stale.label)
        assertEquals("No reading for 11 min", stale.subtitle)
        assertEquals(Tile.STATE_UNAVAILABLE, stale.state)
        assertEquals("No reading yet", GlucoseTileContent.of(GlucoseState(), Samples.NOW).subtitle)
    }

    @Test fun `the services draw and open their screens`() {
        val glucose = Robolectric.setupService(GlucoseTileService::class.java)
        glucose.onStartListening()
        assertEquals("Glucose", glucose.qsTile.label)
        assertEquals(Tile.STATE_UNAVAILABLE, glucose.qsTile.state)
        val glucoseIntent = shadowOf(glucose.target()).savedIntent
        assertEquals(MainActivity::class.java.name, glucoseIntent.component!!.className)
        assertEquals(android.content.Intent.FLAG_ACTIVITY_NEW_TASK, glucoseIntent.flags and android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

        val meal = Robolectric.setupService(MealTileService::class.java)
        meal.onStartListening()
        assertEquals(Tile.STATE_INACTIVE, meal.qsTile.state)
        val mealIntent = shadowOf(meal.target()).savedIntent
        assertEquals("steadmo://widget/camaps/meal", mealIntent.dataString)
        assertEquals(android.content.Intent.FLAG_ACTIVITY_NEW_TASK, mealIntent.flags and android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
