package app.steadmo

import app.steadmo.core.GlucoseUnit
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.steadmo.core.CamapsParser
import app.steadmo.core.TrendArrow
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GlucoseRepositoryTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val file = File(app.filesDir, "readings.csv")

    @Before fun setUp() {
        file.delete()
        app.allowNotifications()
    }

    private var now = 10 * MINUTE

    private fun repository() = GlucoseRepository(
        app, StatusNotifier(app, clock = { now }), writer = { it.run() }, clock = { now },
    )

    /** Posts as CamAPS would at [time]; [live] false replays it at the current [now]. */
    private fun GlucoseRepository.post(value: String?, time: Long, mode: String = "On", live: Boolean = true) {
        if (live) now = time
        val dump = NotificationDump.of(app, camapsPost(app, value, time, mode))
        onCamapsNotification(dump, CamapsParser.parse(dump.viewTexts))
    }

    @Test fun `readings build a trend and are saved`() {
        val repo = repository()
        repo.post("6.0", 0)
        repo.post("6.5", 5 * MINUTE)
        repo.post("7.0", 10 * MINUTE)
        val state = repo.state.value
        assertEquals(3, state.readings.size)
        assertEquals(TrendArrow.UP_SLIGHT, state.trend!!.arrow)
        assertEquals("On", state.status!!.autoMode)
        assertEquals(CAMAPS, state.camapsPackage)
        assertEquals(3, file.readLines().size)
        assertEquals(R.drawable.ic_trend_up_slight, app.posted(1)!!.smallIcon.resId)
    }

    @Test fun `the unit is kept across restarts and redraws the status`() {
        val repo = repository()
        assertEquals(GlucoseUnit.MMOL, repo.state.value.unit)
        repo.setUnit(GlucoseUnit.MGDL)
        assertNull(app.posted(1))
        repo.post("6.0", 0)
        assertEquals("108 mg/dL", app.posted(1)!!.extras.getString(android.app.Notification.EXTRA_TITLE))
        repo.setUnit(GlucoseUnit.MMOL)
        assertEquals("6.0 mmol/L", app.posted(1)!!.extras.getString(android.app.Notification.EXTRA_TITLE))
        repo.setUnit(GlucoseUnit.MGDL)
        assertEquals(GlucoseUnit.MGDL, repository().state.value.unit)
    }

    @Test fun `an unknown saved unit falls back to mmol per L`() {
        app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit().putString("unit", "BOGUS").commit()
        assertEquals(GlucoseUnit.MMOL, repository().state.value.unit)
    }

    @Test fun `saved readings come back after a restart`() {
        repository().apply {
            post("6.0", 0)
            post("6.5", 5 * MINUTE)
        }
        val state = repository().state.value
        assertEquals(2, state.readings.size)
        assertNotNull(state.trend)
    }

    @Test fun `a fast fall alerts once, as soon as two readings show it`() {
        val repo = repository()
        repo.post("15.0", 0)
        repo.post("14.0", 5 * MINUTE)
        repo.post("13.0", 10 * MINUTE)
        assertEquals("Falling fast: 14.0", app.posted(2)!!.extras.getString(android.app.Notification.EXTRA_TITLE))
    }

    @Test fun `a shown alert is redrawn quietly in the new unit, keeping its expiry`() {
        val repo = repository()
        repo.post("15.0", 0)
        repo.post("14.0", 5 * MINUTE)
        val first = app.posted(2)!!
        repo.setUnit(GlucoseUnit.MGDL)
        val redrawn = app.posted(2)!!
        assertEquals("Falling fast: 252", redrawn.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertTrue(redrawn.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(first.timeoutAfter, redrawn.timeoutAfter)
    }

    @Test fun `a dismissed alert stays dismissed when the unit changes`() {
        val repo = repository()
        repo.post("15.0", 0)
        repo.post("14.0", 5 * MINUTE)
        app.getSystemService(android.app.NotificationManager::class.java).cancel(2)
        repo.setUnit(GlucoseUnit.MGDL)
        assertNull(app.posted(2))
    }

    @Test fun `no alert, nothing to redraw`() {
        val repo = repository()
        repo.post("6.0", 0)
        repo.setUnit(GlucoseUnit.MGDL)
        assertNull(app.posted(2))
    }

    @Test fun `an alert isn't repeated early after a restart`() {
        repository().apply {
            post("15.0", 0)
            post("14.0", 5 * MINUTE)
        }
        app.getSystemService(android.app.NotificationManager::class.java).cancel(2)
        repository().apply {
            post("13.0", 10 * MINUTE)
            post("12.0", 15 * MINUTE)
        }
        assertNull(app.posted(2))
    }

    @Test fun `old posts replayed on reconnect neither alert nor show`() {
        now = 3 * 60 * MINUTE
        val repo = repository()
        repo.post("15.0", 0, live = false)
        repo.post("14.0", 5 * MINUTE, live = false)
        assertEquals(2, repo.state.value.readings.size)
        assertNull(app.posted(2))
        assertNull(app.posted(1))
    }

    @Test fun `a replayed reading a few minutes old shows but does not predict`() {
        now = 12 * MINUTE
        val repo = repository()
        repo.post("7.0", 0, live = false)
        repo.post("6.0", 5 * MINUTE, live = false)
        assertEquals(R.drawable.ic_trend_double_down, app.posted(1)!!.smallIcon.resId)
        assertNull(app.posted(2))
    }

    @Test fun `a recovery clears the prediction it invalidated`() {
        val repo = repository()
        repo.post("7.0", 0)
        repo.post("6.0", 5 * MINUTE)
        assertEquals("Low in about 10 min", app.posted(2)!!.extras.getString(android.app.Notification.EXTRA_TITLE))
        repo.post("7.0", 10 * MINUTE)
        assertNull(app.posted(2))
    }

    @Test fun `a throttled repeat keeps the alert up`() {
        val repo = repository()
        repo.post("15.0", 0)
        repo.post("14.0", 5 * MINUTE)
        repo.post("13.0", 10 * MINUTE)
        assertEquals("Falling fast: 14.0", app.posted(2)!!.extras.getString(android.app.Notification.EXTRA_TITLE))
    }

    private fun promoted() = app.posted(1)!!.extras.getBoolean(android.app.Notification.EXTRA_REQUEST_PROMOTED_ONGOING)

    @Test fun `out of range promotes the status, back in range demotes it`() {
        val repo = repository()
        repo.post("4.2", 0)
        assertFalse(promoted())
        repo.post("3.6", 5 * MINUTE)
        assertTrue(promoted())
        repo.post("4.4", 10 * MINUTE)
        assertFalse(promoted())
    }

    @Test fun `a low on the way promotes while still in range`() {
        val repo = repository()
        repo.post("7.0", 0)
        repo.post("6.0", 5 * MINUTE)
        assertTrue(promoted())
    }

    @Test fun `the channel CamAPS posts on is kept for the minimize prompt`() {
        val repo = repository()
        val dump = NotificationDump.of(app, camapsPost(app, "6.0", 0), channelImportance = 2).copy(channelId = "info")
        repo.onCamapsNotification(dump, CamapsParser.parse(dump.viewTexts))
        assertEquals("info", repo.state.value.camapsChannelId)
        assertEquals(2, repo.state.value.camapsChannelImportance)
    }

    @Test fun `a bound is kept, shown, and left out of the trend`() {
        val repo = repository()
        repo.post("4.0", 0)
        repo.post("<2.2", 5 * MINUTE)
        val latest = repo.state.value.latest!!
        assertEquals("<2.2", latest.display)
        assertNull(repo.state.value.trend)
        assertEquals("<2.2", repository().state.value.latest!!.display)
    }

    @Test fun `a repost of the same value changes nothing saved`() {
        val repo = repository()
        repo.post("6.0", 0)
        repo.post("6.0", MINUTE, mode = "Off")
        assertEquals(1, file.readLines().size)
        assertEquals("Off", repo.state.value.status!!.autoMode)
    }

    @Test fun `an unreadable notification is kept for the details view only`() {
        val repo = repository()
        val dump = NotificationDump.of(app, camapsPost(app, null, 0))
        repo.onCamapsNotification(dump, null)
        assertEquals(dump, repo.state.value.lastDump)
        assertNull(repo.state.value.latest)
        assertTrue(!file.exists())
    }

    @Test fun `a corrupt file starts empty`() {
        file.writeText("not,a,reading\n")
        assertEquals(0, repository().state.value.readings.size)
    }

    @Test fun `listener connection is tracked`() {
        val repo = repository()
        repo.setListenerConnected(true)
        assertTrue(repo.state.value.listenerConnected)
    }
}
