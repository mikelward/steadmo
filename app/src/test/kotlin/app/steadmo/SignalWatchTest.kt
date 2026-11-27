package app.steadmo

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.steadmo.core.CamapsParser
import app.steadmo.core.Heartbeat
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SignalWatchTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))

    @Before fun setUp() {
        app.allowNotifications()
        File(app.filesDir, "readings.csv").delete()
    }

    private fun reading(time: Long) {
        val dump = NotificationDump.of(app, camapsPost(app, "6.0", time))
        app.repository.onCamapsNotification(dump, CamapsParser.parse(dump.viewTexts))
    }

    private fun allowListener() = shadowOf(app.getSystemService(android.app.NotificationManager::class.java))
        .setNotificationListenerAccessGranted(android.content.ComponentName(app, CamapsListener::class.java), true)

    @Test fun `nothing to watch before the first reading`() {
        SignalWatch.run(app, now = 0)
        assertNull(app.posted(3))
    }

    @Test fun `a reading sets the check for 15 minutes later`() {
        val now = System.currentTimeMillis()
        reading(now)
        assertEquals(now + Heartbeat.QUIET_MILLIS, alarms.peekNextScheduledAlarm()!!.triggerAtTime)
        SignalWatch.run(app, now = now + 5 * MINUTE)
        assertNull(app.posted(3))
    }

    @Test fun `15 quiet minutes warn, and the next reading clears it`() {
        allowListener()
        val start = System.currentTimeMillis()
        reading(start)
        SignalWatch.run(app, now = start + 15 * MINUTE)
        val n = app.posted(3)!!
        assertEquals("No glucose for 15 min", n.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT)!!.contains("The sensor, Bluetooth or CamAPS FX may be down"))
        assertEquals(StatusNotifier.CHANNEL_ALERTS, n.channelId)
        assertEquals(start + 45 * MINUTE, alarms.peekNextScheduledAlarm()!!.triggerAtTime)

        reading(start + 20 * MINUTE)
        assertNull(app.posted(3))
    }

    @Test fun `a stale replay keeps the warning up`() {
        val start = System.currentTimeMillis() - 20 * MINUTE
        reading(start)
        SignalWatch.run(app, now = start + 15 * MINUTE)
        assertNotNull(app.posted(3))
        val dump = NotificationDump.of(app, camapsPost(app, "6.1", start + 5 * MINUTE))
        app.repository.onCamapsNotification(dump, CamapsParser.parse(dump.viewTexts))
        assertNotNull(app.posted(3))
    }

    @Test fun `says when notification access is the cause`() {
        val start = System.currentTimeMillis()
        reading(start)
        SignalWatch.run(app, now = start + 16 * MINUTE)
        val text = app.posted(3)!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text, text.contains("notification access is off"))
    }

    @Test fun `the alarm runs the check`() {
        reading(System.currentTimeMillis() - 20 * MINUTE)
        SignalWatchReceiver().onReceive(app, Intent())
        assertNotNull(app.posted(3))
    }

    @Test fun `long silences show hours`() {
        assertEquals("1 h", StatusNotifier.duration(60))
        assertEquals("1 h 15 min", StatusNotifier.duration(75))
        assertEquals("45 min", StatusNotifier.duration(45))
    }
}
