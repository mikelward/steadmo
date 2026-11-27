package app.steadmo

import android.app.Application
import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import app.steadmo.core.AlertKind
import app.steadmo.core.Bound
import app.steadmo.core.CamapsStatus
import app.steadmo.core.GlucoseUnit
import app.steadmo.core.Reading
import app.steadmo.core.Trend
import app.steadmo.core.TrendAlert
import app.steadmo.core.TrendArrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusNotifierTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val notifier = StatusNotifier(app, clock = { 7 * MINUTE })
    private val state = GlucoseState(
        readings = listOf(Reading(0, 8.0), Reading(5 * MINUTE, 8.9)),
        trend = Trend(0.12, TrendArrow.UP),
        status = CamapsStatus(8.9, null, GlucoseUnit.MMOL, "On"),
    )

    @Test
    fun `status shows the value, and the trend arrow as its icon`() {
        app.allowNotifications()
        notifier.showStatus(state)
        val n = app.posted(1)!!
        assertEquals(R.drawable.ic_trend_up, n.smallIcon.resId)
        assertEquals("8.9 mmol/L", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Rising · +0.12/min · Auto mode on", n.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(5 * MINUTE, n.`when`)
        // Fresh for 15 minutes from the reading, not from the post.
        assertEquals(StatusNotifier.STALE_MILLIS - 2 * MINUTE, n.timeoutAfter)
        assertEquals(StatusNotifier.CHANNEL_GLUCOSE, n.channelId)
    }

    @Test
    fun `no trend yet shows the dash icon`() {
        app.allowNotifications()
        notifier.showStatus(state.copy(trend = null, status = null))
        val n = app.posted(1)!!
        assertEquals(R.drawable.ic_trend_unknown, n.smallIcon.resId)
        assertEquals("", n.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun `an old reading clears the notification instead of showing it`() {
        app.allowNotifications()
        notifier.showStatus(state)
        StatusNotifier(app, clock = { 5 * MINUTE + StatusNotifier.STALE_MILLIS }).showStatus(state)
        assertNull(app.posted(1))
    }

    @Test
    fun `a bound shows its sign`() {
        app.allowNotifications()
        notifier.showStatus(state.copy(readings = listOf(Reading(5 * MINUTE, 2.2, Bound.BELOW)), trend = null))
        assertEquals("<2.2 mmol/L", app.posted(1)!!.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `nothing to show without a reading`() {
        app.allowNotifications()
        notifier.showStatus(GlucoseState())
        assertNull(app.posted(1))
    }

    @Test
    fun `nothing is posted without permission`() {
        notifier.showStatus(state)
        assertNull(app.posted(1))
    }

    @Test
    fun `alert uses its arrow and the alerts channel`() {
        app.allowNotifications()
        notifier.showAlert(TrendAlert(AlertKind.LOW_SOON, TrendArrow.DOWN, "Low in about 8 min", "5.1"))
        val n = app.posted(2)!!
        assertEquals(R.drawable.ic_trend_down, n.smallIcon.resId)
        assertEquals("Low in about 8 min", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(StatusNotifier.CHANNEL_ALERTS, n.channelId)
    }

    @Test
    fun `rising alert posts too`() {
        app.allowNotifications()
        notifier.showAlert(TrendAlert(AlertKind.RISING_FAST, TrendArrow.DOUBLE_UP, "Rising fast", ""))
        assertEquals(R.drawable.ic_trend_double_up, app.posted(2)!!.smallIcon.resId)
    }
}
