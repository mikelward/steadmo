package app.steadmo

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.steadmo.core.CalendarMeals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CalendarMealRemindersTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val now = System.currentTimeMillis()
    private val dinner = CalendarMeals.Event(7, "Dinner with Sam", now + 3 * 60 * MINUTE, allDay = false)

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        app.allowNotifications()
    }

    private fun calendarAlarms() = alarms.scheduledAlarms.filter {
        shadowOf(it.operation).savedIntent.action == CalendarMealReminders.ACTION
    }

    @Test fun `off by default, and nothing without calendar access`() {
        CalendarMealReminders.scan(app, now) { listOf(dinner) }
        assertTrue(calendarAlarms().isEmpty())
        CalendarMealReminders.set(app, true)
        CalendarMealReminders.scan(app, now) { listOf(dinner) }
        assertTrue(calendarAlarms().isEmpty())
        assertTrue(WorkManager.getInstance(app).getWorkInfosForUniqueWork("calendar-meals-changes").get().isEmpty())
    }

    @Test fun `reminds when the next meal event starts, and looks again on changes and every few hours`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
        CalendarMealReminders.set(app, true)
        CalendarMealReminders.scan(app, now) { listOf(dinner) }
        val alarm = calendarAlarms().single()
        assertEquals(dinner.start, alarm.triggerAtTime)
        assertEquals("Dinner with Sam", shadowOf(alarm.operation).savedIntent.getStringExtra(CalendarMealReminders.EXTRA_TITLE))
        assertEquals(1, WorkManager.getInstance(app).getWorkInfosForUniqueWork("calendar-meals").get().size)
        val watch = WorkManager.getInstance(app).getWorkInfosForUniqueWork("calendar-meals-changes").get().single()
        assertEquals(androidx.work.WorkInfo.State.ENQUEUED, watch.state)
        assertEquals(1, watch.constraints.contentUriTriggers.size)

        CalendarMealReminders.scan(app, now) { emptyList() }
        assertTrue(calendarAlarms().isEmpty())
    }

    @Test fun `the reminder names the event, even with the daily reminder off`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
        CalendarMealReminders.set(app, true)
        MealReminders.set(app, false)
        MealReminderReceiver().onReceive(
            app,
            Intent(app, MealReminderReceiver::class.java).setAction(CalendarMealReminders.ACTION)
                .putExtra(CalendarMealReminders.EXTRA_TITLE, "Dinner with Sam")
                .putExtra(CalendarMealReminders.EXTRA_START, dinner.start),
        )
        val title = app.posted(7)!!.extras.getString(Notification.EXTRA_TITLE)!!
        assertTrue(title, title.startsWith("Dinner with Sam at ") && title.endsWith(". Bolus before you eat?"))
    }

    @Test fun `with the daily reminder off, a restart keeps a calendar reminder and its snooze`() {
        CalendarMealReminders.set(app, true)
        MealReminders.set(app, false)
        StatusNotifier(app).showMealReminder("Lunch")
        MealReminders.snooze(app, 30, event = "Lunch")
        MealReminders.schedule(app)
        assertTrue(app.posted(7) != null)
        assertEquals(1, alarms.scheduledAlarms.count { shadowOf(it.operation).savedIntent.action == MealReminders.ACTION_FIRE + ".AGAIN" })
    }

    @Test fun `a due reminder survives a rescan until it goes off, and then isn't set again`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
        CalendarMealReminders.set(app, true)
        val due = dinner.start
        CalendarMealReminders.scan(app, due + MINUTE) { listOf(dinner) }
        val alarm = calendarAlarms().single()
        assertEquals(due, alarm.triggerAtTime)
        MealReminderReceiver().onReceive(app, shadowOf(alarm.operation).savedIntent)
        assertTrue(app.posted(7) != null)
        CalendarMealReminders.scan(app, due + 2 * MINUTE) { listOf(dinner) }
        assertTrue(calendarAlarms().isEmpty())
    }

    @Test fun `a snoozed calendar reminder names the event again`() {
        CalendarMealReminders.set(app, true)
        MealReminders.set(app, false)
        StatusNotifier(app).showMealReminder("Lunch at 12:30")
        MealReminderReceiver().onReceive(app, shadowOf(app.posted(7)!!.actions[1].actionIntent).savedIntent)
        assertNull(app.posted(7))
        val again = alarms.scheduledAlarms.single { shadowOf(it.operation).savedIntent.action == MealReminders.ACTION_FIRE + ".AGAIN" }
        MealReminderReceiver().onReceive(app, shadowOf(again.operation).savedIntent)
        assertEquals("Lunch at 12:30. Bolus before you eat?", app.posted(7)!!.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test fun `turning calendar meals off clears its reminder and silences its snooze, not the daily one's`() {
        CalendarMealReminders.set(app, true)
        StatusNotifier(app).showMealReminder("Lunch at 12:30")
        val snooze = shadowOf(app.posted(7)!!.actions[1].actionIntent).savedIntent
        MealReminderReceiver().onReceive(app, snooze)
        val again = shadowOf(alarms.scheduledAlarms.single { shadowOf(it.operation).savedIntent.action == MealReminders.ACTION_FIRE + ".AGAIN" }.operation).savedIntent
        StatusNotifier(app).showMealReminder("Lunch at 12:30")
        CalendarMealReminders.set(app, false)
        assertNull(app.posted(7))
        MealReminderReceiver().onReceive(app, again)
        assertNull(app.posted(7))
        // The daily one is left alone.
        StatusNotifier(app).showMealReminder()
        CalendarMealReminders.set(app, false)
        assertTrue(app.posted(7) != null)
    }

    @Test fun `a reminder delivered just after the start still shows, long after says nothing`() {
        CalendarMealReminders.set(app, true)
        MealReminderReceiver().onReceive(
            app,
            Intent(app, MealReminderReceiver::class.java).setAction(CalendarMealReminders.ACTION)
                .putExtra(CalendarMealReminders.EXTRA_TITLE, "Lunch")
                .putExtra(CalendarMealReminders.EXTRA_START, System.currentTimeMillis() - MINUTE),
        )
        assertTrue(app.posted(7) != null)
    }

    @Test fun `a reminder delivered over 30 minutes after the start says nothing`() {
        CalendarMealReminders.set(app, true)
        MealReminderReceiver().onReceive(
            app,
            Intent(app, MealReminderReceiver::class.java).setAction(CalendarMealReminders.ACTION)
                .putExtra(CalendarMealReminders.EXTRA_TITLE, "Lunch")
                .putExtra(CalendarMealReminders.EXTRA_START, System.currentTimeMillis() - 31 * MINUTE),
        )
        assertNull(app.posted(7))
    }

    @Test fun `turned off, a pending calendar reminder says nothing and is not marked reminded`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
        CalendarMealReminders.set(app, false)
        MealReminderReceiver().onReceive(
            app,
            Intent(app, MealReminderReceiver::class.java).setAction(CalendarMealReminders.ACTION)
                .putExtra(CalendarMealReminders.EXTRA_TITLE, dinner.title)
                .putExtra(CalendarMealReminders.EXTRA_START, dinner.start)
                .putExtra(CalendarMealReminders.EXTRA_KEY, dinner.key),
        )
        assertNull(app.posted(7))
        assertEquals(0, WorkManager.getInstance(app).getWorkInfosForUniqueWork("calendar-meals").get().count { !it.state.isFinished })
        // Turning it back on before the meal still schedules the reminder.
        CalendarMealReminders.set(app, true)
        CalendarMealReminders.scan(app, now) { listOf(dinner) }
        assertEquals(1, calendarAlarms().size)
    }

    @Test fun `calendar query excludes hidden calendars, canceled events and declined invites`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
        var capturedSelection: String? = null
        var capturedUri: android.net.Uri? = null
        val provider = object : android.content.ContentProvider() {
            override fun onCreate() = true
            override fun query(
                uri: android.net.Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?,
            ): android.database.Cursor {
                capturedSelection = selection
                capturedUri = uri
                return android.database.MatrixCursor(projection ?: emptyArray())
            }
            override fun getType(uri: android.net.Uri): String? = null
            override fun insert(uri: android.net.Uri, values: android.content.ContentValues?): android.net.Uri? = null
            override fun delete(uri: android.net.Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: android.net.Uri, values: android.content.ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        }
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.CalendarContract.AUTHORITY, provider)
        CalendarMealReminders.set(app, true)
        assertTrue(capturedSelection.orEmpty().contains(android.provider.CalendarContract.Instances.VISIBLE))
        assertTrue(capturedSelection.orEmpty().contains(android.provider.CalendarContract.Instances.STATUS))
        assertTrue(capturedSelection.orEmpty().contains(android.provider.CalendarContract.Instances.SELF_ATTENDEE_STATUS))
        // From 30 minutes ago, so a short meal added after it began (and ended) still reminds.
        CalendarMealReminders.query(app, now)
        val (begin, end) = capturedUri!!.pathSegments.takeLast(2).map(String::toLong)
        assertEquals(now - CalendarMeals.LATE_MILLIS, begin)
        assertEquals(now + CalendarMeals.LOOKAHEAD_MILLIS, end)
    }

    @Test fun `a calendar change looks again and keeps watching`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
        CalendarMealReminders.set(app, true)
        val worker = androidx.work.testing.TestListenableWorkerBuilder<CalendarChangeWorker>(app).build()
        kotlinx.coroutines.runBlocking { assertEquals(androidx.work.ListenableWorker.Result.success(), worker.doWork()) }
        assertTrue(
            WorkManager.getInstance(app).getWorkInfosForUniqueWork("calendar-meals-changes").get().any { !it.state.isFinished },
        )
        CalendarMealReminders.set(app, false)
        assertTrue(
            WorkManager.getInstance(app).getWorkInfosForUniqueWork("calendar-meals-changes").get().all { it.state.isFinished },
        )
    }

    @Test fun `the worker scans`() {
        val worker = androidx.work.testing.TestListenableWorkerBuilder<CalendarScanWorker>(app).build()
        kotlinx.coroutines.runBlocking { assertEquals(androidx.work.ListenableWorker.Result.success(), worker.doWork()) }
    }
}
