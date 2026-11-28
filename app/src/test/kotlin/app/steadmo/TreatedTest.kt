package app.steadmo

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.steadmo.core.CamapsParser
import app.steadmo.core.Treatment
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
class TreatedTest {
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

    @Test fun `low alerts and the urgent low alarm offer Treated`() {
        post("5.6", 0)
        post("4.9", 5 * MINUTE)
        assertEquals("Treated", app.posted(2)!!.actions.single().title)
        post("2.8", 5 * MINUTE)
        assertEquals(listOf("OK", "Treated"), app.posted(4)!!.actions.map { it.title })
    }

    @Test fun `rise alerts don't offer Treated`() {
        post("8.0", 0)
        post("9.0", 5 * MINUTE)
        assertNull(app.posted(2)!!.actions)
    }

    @Test fun `Treated records it, clears the low alerts, holds off the rise and reminds to recheck`() {
        post("5.6", 0)
        post("4.9", 5 * MINUTE)
        post("2.8", 5 * MINUTE)
        repo.recordTreatment(now)
        assertEquals(listOf(now), repo.state.value.treatments)
        assertNull(app.posted(2))
        assertNull(app.posted(4))
        val recheck = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
            .single { shadowOf(it.operation).savedIntent.component!!.className == RecheckReceiver::class.java.name }
        assertEquals(now + Treatment.RECHECK_MILLIS, recheck.triggerAtTime)

        // The rise the treatment causes doesn't alert.
        post("4.0", 5 * MINUTE)
        post("5.2", 5 * MINUTE)
        post("6.6", 5 * MINUTE)
        assertNull(app.posted(2))

        // Kept across restarts.
        val again = GlucoseRepository(app, StatusNotifier(app, clock = { now }), writer = { it.run() }, clock = { now })
        assertEquals(1, again.state.value.treatments.size)
    }

    @Test fun `the notification action records a treatment and the recheck prompts when readings are missing`() {
        TreatedReceiver().onReceive(app, Intent())
        assertEquals(1, app.repository.state.value.treatments.size)
        // Before 15 minutes have passed, an early alarm is skipped.
        RecheckReceiver().onReceive(app, Intent())
        assertNull(app.posted(5))
        // At 15 minutes with no reading yet, prompts a manual recheck.
        app.repository.onTreatmentRecheck(app.repository.state.value.treatments.last() + Treatment.RECHECK_MILLIS)
        val n = app.posted(5)!!
        assertEquals("Recheck your glucose", n.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT)!!.startsWith("You treated a low 15 min ago."))
    }

    @Test fun `the recheck gives the latest value with its time, and never a stale one`() {
        post("4.4", 0)
        val reading = repo.state.value.latest!!
        StatusNotifier(app, clock = { now + 2 * MINUTE }).showRecheck(reading, repo.state.value.unit)
        val fresh = app.posted(5)!!
        assertTrue(fresh.extras.getCharSequence(Notification.EXTRA_TEXT)!!.contains("Latest 4.4 mmol/L at "))
        assertEquals(reading.timeMillis, fresh.`when`)
        StatusNotifier(app, clock = { now + 20 * MINUTE }).showRecheck(reading, repo.state.value.unit)
        val text = app.posted(5)!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text, text.contains("No glucose from CamAPS FX since "))
        assertTrue(!text.contains("4.4"))
    }

    @Test fun `treatments expire after a day without another one`() {
        repo.recordTreatment(now)
        post("6.0", Treatment.RETENTION_MILLIS + MINUTE)
        assertEquals(emptyList<Long>(), repo.state.value.treatments)
        val restarted = GlucoseRepository(app, StatusNotifier(app, clock = { now }), writer = { it.run() }, clock = { now })
        assertEquals(emptyList<Long>(), restarted.state.value.treatments)
    }

    @Test fun `old saved treatments are dropped on load`() {
        repo.recordTreatment(now)
        now += Treatment.RETENTION_MILLIS + MINUTE
        val restarted = GlucoseRepository(app, StatusNotifier(app, clock = { now }), writer = { it.run() }, clock = { now })
        assertEquals(emptyList<Long>(), restarted.state.value.treatments)
    }

    @Test fun `Treated clears a low alert shown before a restart`() {
        post("5.6", 0)
        post("4.9", 5 * MINUTE)
        assertNotNull(app.posted(2))
        val restarted = GlucoseRepository(app, StatusNotifier(app, clock = { now }), writer = { it.run() }, clock = { now })
        restarted.recordTreatment(now)
        assertNull(app.posted(2))
    }
}
