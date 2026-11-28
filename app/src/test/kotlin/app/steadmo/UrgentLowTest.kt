package app.steadmo

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import androidx.test.core.app.ApplicationProvider
import app.steadmo.core.CamapsParser
import app.steadmo.core.GlucoseUnit
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
class UrgentLowTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var now = 0L

    @Before fun setUp() {
        File(app.filesDir, "readings.csv").delete()
        app.allowNotifications()
        now = System.currentTimeMillis()
    }

    private val repo by lazy { GlucoseRepository(app, StatusNotifier(app, clock = { now }), writer = { it.run() }, clock = { now }) }

    private fun post(value: String, after: Long) {
        now += after
        val dump = NotificationDump.of(app, camapsPost(app, value, now))
        repo.onCamapsNotification(dump, CamapsParser.parse(dump.viewTexts))
    }

    @Test fun `the channel is an alarm with the bundled descending tone that may bypass Do Not Disturb`() {
        StatusNotifier(app)
        val manager = app.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(StatusNotifier.CHANNEL_URGENT)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(AudioAttributes.USAGE_ALARM, channel.audioAttributes.usage)
        assertTrue(channel.canBypassDnd())
        assertEquals("android.resource://${app.packageName}/${R.raw.alert_urgent_low}", channel.sound.toString())
        assertNull(manager.getNotificationChannel("urgent_low"))
    }

    @Test fun `an urgent low sounds until OK, then stays quiet for 15 minutes`() {
        post("2.8", 0)
        val n = app.posted(4)!!
        assertEquals("Urgent low: 2.8 mmol/L", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(StatusNotifier.CHANNEL_URGENT, n.channelId)
        assertEquals(Notification.CATEGORY_ALARM, n.category)
        assertTrue(n.flags and Notification.FLAG_INSISTENT != 0)
        assertEquals("OK", n.actions.first().title)

        UrgentLowAckReceiver().onReceive(app, Intent())
        assertNull(app.posted(4))
        post("2.7", 5 * MINUTE)
        assertNull(app.posted(4))
        post("2.7", 15 * MINUTE)
        assertNotNull(app.posted(4))
    }

    @Test fun `3_5 trending down escalates immediately to Urgent low soon and redraws in mg per dL`() {
        post("3.8", 0)
        post("3.5", 5 * MINUTE)
        val n = app.posted(4)!!
        assertEquals("Urgent low soon: 3.5 mmol/L", n.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT)!!.contains("Heading below 3.0 in about"))
        // No duplicate Low or trend alert alongside Urgent low soon.
        assertNull(app.posted(StatusNotifier.ID_LOW))
        assertNull(app.posted(2))

        repo.setUnit(GlucoseUnit.MGDL)
        val redrawn = app.posted(4)!!
        assertEquals("Urgent low soon: 63 mg/dL", redrawn.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(redrawn.extras.getCharSequence(Notification.EXTRA_TEXT)!!.contains("Heading below 54"))
    }

    @Test fun `ignored for 5 minutes, it takes over the screen`() {
        post("2.8", 0)
        assertNull(app.posted(4)!!.fullScreenIntent)
        post("2.7", 5 * MINUTE)
        val n = app.posted(4)!!
        assertEquals(
            UrgentLowActivity::class.java.name,
            org.robolectric.Shadows.shadowOf(n.fullScreenIntent).savedIntent.component!!.className,
        )
        // OK starts the count again.
        UrgentLowAckReceiver().onReceive(app, Intent())
        post("2.7", 16 * MINUTE)
        assertNull(app.posted(4)!!.fullScreenIntent)
    }

    @Test fun `below the sensor's range is urgent too, and recovery clears it`() {
        post("<2.2", 0)
        assertNotNull(app.posted(4))
        post("3.6", 5 * MINUTE)
        assertNull(app.posted(4))
        assertNull(UrgentLowAck.get(app))
    }

    @Test fun `a replayed recovery still clears the alarm`() {
        post("2.8", 0)
        assertNotNull(app.posted(4))
        val recovered = now + 5 * MINUTE
        now += 8 * MINUTE
        val dump = NotificationDump.of(app, camapsPost(app, "3.6", recovered))
        repo.onCamapsNotification(dump, CamapsParser.parse(dump.viewTexts))
        assertNull(app.posted(4))
    }

    @Test fun `changing units redraws the alarm quietly, and not once acknowledged`() {
        post("2.8", 0)
        repo.setUnit(GlucoseUnit.MGDL)
        val n = app.posted(4)!!
        assertEquals("Urgent low: 50 mg/dL", n.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        UrgentLowAckReceiver().onReceive(app, Intent())
        repo.setUnit(GlucoseUnit.MMOL)
        assertNull(app.posted(4))
    }

    private fun dismissed(expires: Long) = Intent(app, UrgentLowAckReceiver::class.java)
        .setAction(UrgentLowAckReceiver.ACTION_DISMISSED).putExtra(UrgentLowAckReceiver.EXTRA_EXPIRES, expires)

    @Test fun `timing out with its reading isn't an acknowledgement`() {
        post("2.8", 0)
        val n = app.posted(4)!!
        assertEquals(UrgentLowAckReceiver.ACTION_DISMISSED, org.robolectric.Shadows.shadowOf(n.deleteIntent).savedIntent.action)
        UrgentLowAckReceiver().onReceive(app, dismissed(expires = System.currentTimeMillis() - MINUTE))
        assertNull(UrgentLowAck.get(app))
        assertNotNull(UrgentLowAck.alertingSince(app))
    }

    @Test fun `swiping it away acknowledges it`() {
        post("2.8", 0)
        UrgentLowAckReceiver().onReceive(app, dismissed(expires = System.currentTimeMillis() + 10 * MINUTE))
        assertNotNull(UrgentLowAck.get(app))
        assertNull(app.posted(4))
    }

    @Test fun `changing units keeps a full-screen takeover`() {
        post("2.8", 0)
        post("2.7", 6 * MINUTE)
        assertNotNull(app.posted(4)!!.fullScreenIntent)
        repo.setUnit(GlucoseUnit.MGDL)
        assertNotNull(app.posted(4)!!.fullScreenIntent)
    }

    @Test fun `an old reading doesn't sound`() {
        now = System.currentTimeMillis()
        val dump = NotificationDump.of(app, camapsPost(app, "2.8", now - 20 * MINUTE))
        repo.onCamapsNotification(dump, CamapsParser.parse(dump.viewTexts))
        assertNull(app.posted(4))
    }
}
