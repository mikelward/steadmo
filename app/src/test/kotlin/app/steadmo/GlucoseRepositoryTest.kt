package app.steadmo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.steadmo.core.CamapsParser
import app.steadmo.core.TrendArrow
import java.io.File
import org.junit.Assert.assertEquals
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

    private fun GlucoseRepository.post(value: String?, time: Long, mode: String = "On") {
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

    @Test fun `old posts replayed on reconnect neither alert nor show`() {
        now = 3 * 60 * MINUTE
        val repo = repository()
        repo.post("15.0", 0)
        repo.post("14.0", 5 * MINUTE)
        assertEquals(2, repo.state.value.readings.size)
        assertNull(app.posted(2))
        assertNull(app.posted(1))
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
