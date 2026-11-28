package app.steadmo

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import app.steadmo.core.CamapsParser
import app.steadmo.widget.WidgetStaleReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ReliabilityTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))

    private fun cancelAllAlarms() = alarms.scheduledAlarms.toList().forEach {
        it.operation?.let { op -> app.getSystemService(AlarmManager::class.java).cancel(op) }
    }

    @Test fun `batteryUnrestricted reads PowerManager exemption state`() {
        val power = shadowOf(app.getSystemService(PowerManager::class.java))
        power.setIgnoringBatteryOptimizations(app.packageName, false)
        assertFalse(Reliability.batteryUnrestricted(app))
        power.setIgnoringBatteryOptimizations(app.packageName, true)
        assertTrue(Reliability.batteryUnrestricted(app))
    }

    @Test fun `alarmVolumeMuted checks alarm stream volume`() {
        val audio = app.getSystemService(AudioManager::class.java)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        assertTrue(Reliability.alarmVolumeMuted(app))
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 5, 0)
        assertFalse(Reliability.alarmVolumeMuted(app))
    }

    @Test fun `unusedAppRestrictionsDisabled reads auto-revoke whitelist`() {
        val pm = shadowOf(app.packageManager)
        pm.setAutoRevokeWhitelisted(false)
        assertFalse(Reliability.unusedAppRestrictionsDisabled(app))
        pm.setAutoRevokeWhitelisted(true)
        assertTrue(Reliability.unusedAppRestrictionsDisabled(app))
    }

    @Test fun `urgentLowSilenced detects silenced or disabled urgent low channel`() {
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(StatusNotifier.CHANNEL_URGENT)
        assertFalse(StatusNotifier.urgentLowSilenced(app))

        manager.createNotificationChannel(
            NotificationChannel(StatusNotifier.CHANNEL_URGENT, "Urgent low", NotificationManager.IMPORTANCE_HIGH),
        )
        assertFalse(StatusNotifier.urgentLowSilenced(app))

        manager.deleteNotificationChannel(StatusNotifier.CHANNEL_URGENT)
        manager.createNotificationChannel(
            NotificationChannel(StatusNotifier.CHANNEL_URGENT, "Urgent low", NotificationManager.IMPORTANCE_LOW),
        )
        assertTrue(StatusNotifier.urgentLowSilenced(app))
    }

    @Test fun `BootReceiver restores alarms on boot and package update`() {
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val now = System.currentTimeMillis()
        val dump = NotificationDump.of(app, camapsPost(app, "6.0", now))
        app.repository.onCamapsNotification(dump, CamapsParser.parse(dump.viewTexts))
        MealReminders.set(app, true)
        MealReminders.snooze(app, 30, now = now)
        app.repository.recordTreatment(now)
        shadowOf(app.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(ComponentName(app, CamapsListener::class.java), true)
        app.repository.setListenerConnected(false)

        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            cancelAllAlarms()
            BootReceiver().onReceive(app, Intent(action))
            val intents = alarms.scheduledAlarms.map { shadowOf(it.operation).savedIntent }
            assertTrue(action, intents.any { it.action == MealReminders.ACTION_FIRE })
            assertTrue(action, intents.any { it.action == MealReminders.ACTION_FIRE + ".AGAIN" })
            assertTrue(action, intents.any { it.component?.className == RecheckReceiver::class.java.name })
            assertTrue(action, intents.any { it.component?.className == SignalWatchReceiver::class.java.name })
            assertTrue(action, intents.any { it.component?.className == WeeklyReportReceiver::class.java.name })
            assertTrue(action, intents.any { it.component?.className == WidgetStaleReceiver::class.java.name })
        }
    }

    @Test fun `BootReceiver ignores unexpected actions`() {
        cancelAllAlarms()
        BootReceiver().onReceive(app, Intent("com.example.SPOOF"))
        assertEquals(0, alarms.scheduledAlarms.size)
    }

    @Test fun `SignalWatch requests rebind when access is on but listener is disconnected`() {
        app.allowNotifications()
        shadowOf(app.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(ComponentName(app, CamapsListener::class.java), true)
        app.repository.setListenerConnected(false)
        val start = System.currentTimeMillis()
        val dump = NotificationDump.of(app, camapsPost(app, "6.0", start))
        app.repository.onCamapsNotification(dump, CamapsParser.parse(dump.viewTexts))
        SignalWatch.run(app, now = start + 15 * MINUTE)
        assertTrue(app.posted(3) != null)
    }

    @Test fun `dndBlocksAlarms detects Total Silence and Priority without alarms`() {
        val manager = app.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        assertFalse(Reliability.dndBlocksAlarms(app))

        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        assertFalse(Reliability.dndBlocksAlarms(app))

        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        assertTrue(Reliability.dndBlocksAlarms(app))

        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        manager.deleteNotificationChannel(StatusNotifier.CHANNEL_URGENT)
        manager.setNotificationPolicy(NotificationManager.Policy(0, 0, 0))
        assertTrue(Reliability.dndBlocksAlarms(app))

        manager.createNotificationChannel(
            NotificationChannel(StatusNotifier.CHANNEL_URGENT, "Urgent low", NotificationManager.IMPORTANCE_HIGH).apply {
                setBypassDnd(true)
            },
        )
        assertFalse(Reliability.dndBlocksAlarms(app))

        manager.deleteNotificationChannel(StatusNotifier.CHANNEL_URGENT)
        manager.setNotificationPolicy(NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS, 0, 0))
        assertFalse(Reliability.dndBlocksAlarms(app))
    }

    @Test fun `liveUpdatesAllowed queries NotificationManager on Android 16`() {
        assertFalse(Reliability.liveUpdatesAllowed(app))
    }

    @Test fun `test urgent low alarm posts on its own id and OK stops it without acknowledging real lows`() {
        app.allowNotifications()
        UrgentLowAck.clear(app)
        StatusNotifier(app).showUrgentLowTest()
        val posted = app.posted(StatusNotifier.ID_URGENT_TEST)
        assertTrue(posted != null)
        assertEquals(StatusNotifier.CHANNEL_URGENT, posted!!.channelId)

        UrgentLowAckReceiver().onReceive(app, Intent(UrgentLowAckReceiver.ACTION_TEST_OK))
        assertEquals(null, app.posted(StatusNotifier.ID_URGENT_TEST))
        assertEquals(null, UrgentLowAck.get(app))
    }
}
