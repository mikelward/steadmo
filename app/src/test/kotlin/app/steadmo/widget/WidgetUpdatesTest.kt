package app.steadmo.widget

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.steadmo.Samples
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WidgetUpdatesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))

    @Test fun `with no widget placed, a new state does nothing`() = runBlocking {
        WidgetUpdates.onState(context, Samples.rising(), Samples.NOW)
    }

    @Test fun `the stale redraw uses a non-idle alarm and cancels once stale`() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        WidgetUpdates.scheduleStale(context, Samples.rising(), Samples.NOW)
        val alarm = alarms.scheduledAlarms.single {
            shadowOf(it.operation).savedIntent.component?.className == WidgetStaleReceiver::class.java.name
        }
        assertEquals(Samples.NOW + 10 * 60_000L + 5_000, alarm.triggerAtTime)
        org.junit.Assert.assertFalse(alarm.isAllowWhileIdle)

        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        try {
            WidgetUpdates.scheduleStale(context, Samples.rising(), Samples.NOW)
            val exact = alarms.scheduledAlarms.last {
                shadowOf(it.operation).savedIntent.component?.className == WidgetStaleReceiver::class.java.name
            }
            assertEquals(org.robolectric.shadows.ShadowAlarmManager.WINDOW_EXACT, exact.windowLengthMs)
            org.junit.Assert.assertFalse(exact.isAllowWhileIdle)
        } finally {
            org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        }

        WidgetUpdates.scheduleStale(context, Samples.rising(), Samples.NOW + 60 * 60_000)
        assertTrue(
            alarms.scheduledAlarms.none {
                shadowOf(it.operation).savedIntent.component?.className == WidgetStaleReceiver::class.java.name
            },
        )
        WidgetUpdates.scheduleStale(context, app.steadmo.GlucoseState(), Samples.NOW)
    }

    @Test fun `the next redraw is whichever of reading and mode expires first`() {
        val stale = 10 * 60_000L + 5_000
        val reading = Samples.rising()
        // Mode reported 2 min after the reading: the reading expires first, then the mode.
        val withLaterMode = reading.copy(statusTime = Samples.NOW + 2 * 60_000)
        assertEquals(Samples.NOW + stale, WidgetUpdates.nextStaleRedraw(withLaterMode, Samples.NOW))
        assertEquals(
            Samples.NOW + 2 * 60_000 + stale,
            WidgetUpdates.nextStaleRedraw(withLaterMode, Samples.NOW + stale),
        )
        assertEquals(null, WidgetUpdates.nextStaleRedraw(withLaterMode, Samples.NOW + 2 * 60_000 + stale))
        assertEquals(null, WidgetUpdates.nextStaleRedraw(app.steadmo.GlucoseState(), Samples.NOW))
    }

    @Test fun `the stale redraw receiver and helper succeed`() = runBlocking {
        WidgetUpdates.redrawStale(context)
        WidgetStaleReceiver().onReceive(context, Intent())
    }

    @Test fun `the graph is drawn at the asked size`() {
        val colors = WidgetGraph.Colors(Color.RED, Color.GREEN, Color.YELLOW, Color.LTGRAY, Color.GRAY, Color.BLACK)
        val bitmap = WidgetGraph.draw(Samples.rising().readings, Samples.NOW, 600, 200, 2f, colors)
        assertEquals(600, bitmap.width)
        assertEquals(200, bitmap.height)
        assertEquals(1, WidgetGraph.draw(emptyList(), Samples.NOW, 0, 0, 1f, colors).width)
    }
}
