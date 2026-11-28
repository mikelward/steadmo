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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LowAlertTest {
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

    @Test fun `the Low channel uses the bundled low tone on the alarm stream and asks to bypass DND`() {
        StatusNotifier(app)
        val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel(StatusNotifier.CHANNEL_LOW)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(AudioAttributes.USAGE_ALARM, channel.audioAttributes.usage)
        assertTrue(channel.canBypassDnd())
        assertEquals("android.resource://${app.packageName}/${R.raw.alert_low}", channel.sound.toString())
    }

    @Test fun `below 3_9 loops on the Low channel, stays quiet for 15 min when kept, and repeats at 15 min`() {
        post("3.4", 0)
        val n = app.posted(StatusNotifier.ID_LOW)!!
        assertEquals("Low: 3.4 mmol/L", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(StatusNotifier.CHANNEL_LOW, n.channelId)
        assertEquals(Notification.CATEGORY_ALARM, n.category)
        assertTrue(n.flags and Notification.FLAG_INSISTENT != 0)
        assertNull(n.fullScreenIntent)
        assertEquals(listOf("OK", "Treated"), n.actions.map { it.title })

        // Subsequent flat low reading updates text quietly without re-chiming.
        post("3.5", 5 * MINUTE)
        val updated = app.posted(StatusNotifier.ID_LOW)!!
        assertEquals("Low: 3.5 mmol/L", updated.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(updated.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)

        // Ignored for 15 min: sounds again.
        post("3.4", 10 * MINUTE)
        val repeated = app.posted(StatusNotifier.ID_LOW)!!
        assertEquals(0, repeated.flags and Notification.FLAG_ONLY_ALERT_ONCE)
    }

    @Test fun `OK on Low snoozes Low for 15 min without blocking Urgent Low`() {
        post("3.5", 0)
        assertNotNull(app.posted(StatusNotifier.ID_LOW))

        LowAckReceiver().onReceive(app, Intent())
        assertNull(app.posted(StatusNotifier.ID_LOW))
        post("3.5", 5 * MINUTE)
        assertNull(app.posted(StatusNotifier.ID_LOW))

        // Dropping to urgent low breaks through immediately, but preserves Low's 15-min snooze.
        post("2.8", 5 * MINUTE)
        assertNotNull(app.posted(4))
        assertNull(app.posted(StatusNotifier.ID_LOW))
        UrgentLowAckReceiver().onReceive(app, Intent())

        // Returning to non-urgent low within Low's original 15-min snooze stays quiet, then alerts once expired.
        post("3.5", 4 * MINUTE)
        assertNull(app.posted(StatusNotifier.ID_LOW))
        post("3.5", 5 * MINUTE)
        assertNotNull(app.posted(StatusNotifier.ID_LOW))
    }

    private fun dismissed(expires: Long) = Intent(app, LowAckReceiver::class.java)
        .setAction(LowAckReceiver.ACTION_DISMISSED)
        .putExtra(LowAckReceiver.EXTRA_EXPIRES, expires)

    @Test fun `swiping Low away acknowledges it, but timeout expiry does not`() {
        post("3.4", 0)
        val n = app.posted(StatusNotifier.ID_LOW)!!
        assertEquals(LowAckReceiver.ACTION_DISMISSED, shadowOf(n.deleteIntent).savedIntent.action)
        LowAckReceiver().onReceive(app, dismissed(expires = System.currentTimeMillis() - MINUTE))
        assertNull(LowAck.get(app))

        LowAckReceiver().onReceive(app, dismissed(expires = System.currentTimeMillis() + 10 * MINUTE))
        assertNotNull(LowAck.get(app))
        assertNull(app.posted(StatusNotifier.ID_LOW))
    }

    @Test fun `Treated snoozes both Low and Urgent Low for 15 min, then recheck alerts at the right tier`() {
        post("3.8", 0)
        post("3.5", 5 * MINUTE) // falling -0.06/min -> Urgent low soon!
        assertNotNull(app.posted(4))

        repo.recordTreatment(now)
        assertNull(app.posted(4))
        assertNull(app.posted(StatusNotifier.ID_LOW))

        // CGM lag dip to 2.8 at 10 min stays quiet while sugar absorbs.
        post("2.8", 10 * MINUTE)
        assertNull(app.posted(4))
        assertNull(app.posted(StatusNotifier.ID_LOW))

        // At 15 min still 2.8 -> Urgent Low fires on recheck.
        now += 5 * MINUTE
        repo.onTreatmentRecheck(now)
        assertNotNull(app.posted(4))
        assertNull(app.posted(5))
    }

    @Test fun `15-minute recheck fires Low if still flat below 3_9`() {
        post("3.5", 0)
        repo.recordTreatment(now)
        post("3.5", 13 * MINUTE)
        now += 2 * MINUTE
        repo.onTreatmentRecheck(now)
        assertNotNull(app.posted(StatusNotifier.ID_LOW))
        assertNull(app.posted(5))
    }

    @Test fun `15-minute recheck stays quiet when visibly rising after treatment`() {
        post("2.9", 0)
        repo.recordTreatment(now)
        post("3.2", 5 * MINUTE)
        post("3.5", 5 * MINUTE)
        now += 5 * MINUTE
        repo.onTreatmentRecheck(now)
        assertNull(app.posted(StatusNotifier.ID_LOW))
        assertNull(app.posted(4))
        assertNull(app.posted(5))
        // Next rising reading (+0.06/min = UP_SLIGHT, still < 3.9) also stays quiet.
        post("3.8", 0)
        assertNull(app.posted(StatusNotifier.ID_LOW))
    }

    @Test fun `15-minute recheck stays quiet once back in range and skips superseded alarms`() {
        post("3.5", 0)
        repo.recordTreatment(now)
        post("4.5", 13 * MINUTE)
        now += 2 * MINUTE
        repo.onTreatmentRecheck(now)
        assertNull(app.posted(StatusNotifier.ID_LOW))
        assertNull(app.posted(5))

        // Superseded by a newer treatment: recheck skips.
        repo.recordTreatment(now)
        now += 5 * MINUTE
        repo.onTreatmentRecheck(now)
        assertNull(app.posted(5))
        assertNull(app.posted(StatusNotifier.ID_LOW))
    }

    @Test fun `15-minute recheck prompts manual check on Low channel when readings are stale and clears on new reading`() {
        post("3.5", 0)
        repo.recordTreatment(now)
        // Exact alarms can fire a moment early; within 5s still runs the recheck.
        now += 15 * MINUTE - 2_000L
        repo.onTreatmentRecheck(now)
        val recheck = app.posted(5)!!
        assertEquals("Recheck your glucose", recheck.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(StatusNotifier.CHANNEL_LOW, recheck.channelId)

        post("4.5", MINUTE)
        assertNull(app.posted(5))
    }

    @Test fun `changing units redraws Low quietly and recovery clears it`() {
        post("3.4", 0)
        repo.setUnit(GlucoseUnit.MGDL)
        val n = app.posted(StatusNotifier.ID_LOW)!!
        assertEquals("Low: 61 mg/dL", n.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)

        post("4.4", 5 * MINUTE)
        assertNull(app.posted(StatusNotifier.ID_LOW))
        assertNull(LowAck.get(app))
    }

    @Test fun `a replayed low reading does not sound`() {
        val dump = NotificationDump.of(app, camapsPost(app, "3.4", now - 10 * MINUTE))
        repo.onCamapsNotification(dump, CamapsParser.parse(dump.viewTexts))
        assertNull(app.posted(StatusNotifier.ID_LOW))
    }

    @Test fun `lowSilenced detects silenced Low channel`() {
        val manager = app.getSystemService(NotificationManager::class.java)
        StatusNotifier(app)
        assertFalse(StatusNotifier.lowSilenced(app))
        manager.deleteNotificationChannel(StatusNotifier.CHANNEL_LOW)
        assertFalse(StatusNotifier.lowSilenced(app))
        manager.createNotificationChannel(
            android.app.NotificationChannel(StatusNotifier.CHANNEL_LOW, "Low", NotificationManager.IMPORTANCE_LOW),
        )
        assertTrue(StatusNotifier.lowSilenced(app))
    }
}
