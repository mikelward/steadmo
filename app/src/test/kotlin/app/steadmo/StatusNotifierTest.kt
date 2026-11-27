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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val low = state.copy(
        readings = listOf(Reading(0, 4.4), Reading(MINUTE, 3.8), Reading(6 * MINUTE, 3.6)),
        trend = Trend(-0.07, TrendArrow.DOWN_SLIGHT),
    )

    @Test
    fun `out of range is a Live Update with a chip and range bar`() {
        app.allowNotifications()
        notifier.showStatus(low, promote = true)
        val n = app.posted(1)!!
        assertTrue(n.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertEquals("3.6", n.shortCriticalText)
        assertEquals(StatusNotifier.scale(3.6), n.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals("Low: 3.6 mmol/L", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "Falling slowly · −0.07/min · Auto mode on. Below 3.9 for 5 min",
            n.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun `not promoted unless asked`() {
        app.allowNotifications()
        notifier.showStatus(low)
        assertFalse(app.posted(1)!!.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
    }

    @Test
    fun `high says so, and a just-started run has no duration`() {
        app.allowNotifications()
        notifier.showStatus(state.copy(readings = listOf(Reading(7 * MINUTE, 12.0)), trend = null), promote = true)
        val n = app.posted(1)!!
        assertEquals("High: 12.0 mmol/L", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Auto mode on. Above 10.0", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun `the bar's scale is clamped to 2 to 14`() {
        assertEquals(0, StatusNotifier.scale(1.0))
        assertEquals(19, StatusNotifier.scale(3.9))
        assertEquals(StatusNotifier.SCALE_MAX, StatusNotifier.scale(25.0))
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
    fun `an alert for an old reading is not shown`() {
        app.allowNotifications()
        StatusNotifier(app, clock = { StatusNotifier.STALE_MILLIS }).showAlert(
            TrendAlert(AlertKind.RISING_FAST, TrendArrow.DOUBLE_UP, "Rising fast", ""), 0,
        )
        assertNull(app.posted(2))
    }

    @Test
    fun `a bound shows its sign`() {
        app.allowNotifications()
        notifier.showStatus(state.copy(readings = listOf(Reading(5 * MINUTE, 2.2, Bound.BELOW)), trend = null))
        assertEquals("Low: <2.2 mmol/L", app.posted(1)!!.extras.getString(Notification.EXTRA_TITLE))
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
        notifier.showAlert(TrendAlert(AlertKind.LOW_SOON, TrendArrow.DOWN, "Low in about 8 min", "5.1"), 6 * MINUTE)
        val n = app.posted(2)!!
        assertEquals(StatusNotifier.STALE_MILLIS - MINUTE, n.timeoutAfter)
        assertEquals(R.drawable.ic_trend_down, n.smallIcon.resId)
        assertEquals("Low in about 8 min", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(StatusNotifier.CHANNEL_ALERTS, n.channelId)
    }

    @Test
    fun `rising alert posts too`() {
        app.allowNotifications()
        notifier.showAlert(TrendAlert(AlertKind.RISING_FAST, TrendArrow.DOUBLE_UP, "Rising fast", ""), 7 * MINUTE)
        assertEquals(R.drawable.ic_trend_double_up, app.posted(2)!!.smallIcon.resId)
    }
}
