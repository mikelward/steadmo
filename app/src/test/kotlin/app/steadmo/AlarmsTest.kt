package app.steadmo

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
class AlarmsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val operation = PendingIntent.getBroadcast(app, 9, Intent("test"), PendingIntent.FLAG_IMMUTABLE)

    @After fun reset() = ShadowAlarmManager.setCanScheduleExactAlarms(false)

    @Test fun `on time once Alarms and reminders is allowed`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        Alarms.set(app, 1_000_000L, operation)
        val alarm = alarms.scheduledAlarms.single { it.operation == operation }
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs)
        assertTrue(alarm.isAllowWhileIdle)
    }

    @Test fun `inexact without it, still while idle`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        Alarms.set(app, 1_000_000L, operation)
        val alarm = alarms.scheduledAlarms.single { it.operation == operation }
        assertTrue(alarm.windowLengthMs != ShadowAlarmManager.WINDOW_EXACT)
        assertTrue(alarm.isAllowWhileIdle)
    }

    @Test fun `allowing it sets the reminders again, on time`() {
        MealReminders.set(app, true)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        ExactAlarmPermissionReceiver().onReceive(app, Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))
        val reminder = alarms.scheduledAlarms.single { shadowOf(it.operation).savedIntent.action == MealReminders.ACTION_FIRE }
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, reminder.windowLengthMs)
    }

    @Test fun `allowing it makes a pending snooze and recheck on time too`() {
        val now = System.currentTimeMillis()
        MealReminders.snooze(app, 30, now = now)
        app.repository.recordTreatment(now)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        ExactAlarmPermissionReceiver().onReceive(app, Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))
        val snooze = alarms.scheduledAlarms.single { shadowOf(it.operation).savedIntent.action == MealReminders.ACTION_FIRE + ".AGAIN" }
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, snooze.windowLengthMs)
        val recheck = alarms.scheduledAlarms.single {
            shadowOf(it.operation).savedIntent.component?.className == RecheckReceiver::class.java.name
        }
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, recheck.windowLengthMs)
    }

    private fun cancelAll() = alarms.scheduledAlarms.toList().forEach {
        it.operation?.let { op -> app.getSystemService(AlarmManager::class.java).cancel(op) }
    }

    @Test fun `a snooze cancelled with the permission comes back on start, until it goes off`() {
        val now = System.currentTimeMillis()
        MealReminders.snooze(app, 30, event = "Lunch at 12:30", now = now)
        cancelAll()
        MealReminders.restoreSnooze(app, now = now + MINUTE)
        val again = alarms.scheduledAlarms.single { shadowOf(it.operation).savedIntent.action == MealReminders.ACTION_FIRE + ".AGAIN" }
        assertEquals(now + 30 * MINUTE, again.triggerAtTime)
        assertEquals("Lunch at 12:30", shadowOf(again.operation).savedIntent.getStringExtra(MealReminders.EXTRA_EVENT))
        MealReminderReceiver().onReceive(app, shadowOf(again.operation).savedIntent)
        cancelAll()
        MealReminders.restoreSnooze(app, now = now + 31 * MINUTE)
        assertTrue(alarms.scheduledAlarms.none { shadowOf(it.operation).savedIntent.action == MealReminders.ACTION_FIRE + ".AGAIN" })
    }

    @Test fun `an old snooze isn't restored`() {
        val now = System.currentTimeMillis()
        MealReminders.snooze(app, 30, now = now)
        cancelAll()
        MealReminders.restoreSnooze(app, now = now + 4 * 60 * MINUTE)
        assertTrue(alarms.scheduledAlarms.none { shadowOf(it.operation).savedIntent.action == MealReminders.ACTION_FIRE + ".AGAIN" })
    }

    @Test fun `a recheck still ahead comes back on start`() {
        val now = System.currentTimeMillis()
        Recheck.restore(app, listOf(now - 5 * MINUTE), now)
        fun rechecks() = alarms.scheduledAlarms.filter {
            shadowOf(it.operation).savedIntent.component?.className == RecheckReceiver::class.java.name
        }
        assertEquals(now + 10 * MINUTE, rechecks().single().triggerAtTime)
        cancelAll()
        Recheck.restore(app, listOf(now - 20 * MINUTE), now)
        Recheck.restore(app, emptyList(), now)
        assertTrue(rechecks().isEmpty())
    }

    @Test fun `ignores anything but the system's broadcast`() {
        val before = alarms.scheduledAlarms.map { it.triggerAtTime to it.windowLengthMs }
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        ExactAlarmPermissionReceiver().onReceive(app, Intent("com.example.SPOOF"))
        assertEquals(before, alarms.scheduledAlarms.map { it.triggerAtTime to it.windowLengthMs })
    }
}
