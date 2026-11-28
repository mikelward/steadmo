package app.steadmo

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.steadmo.core.MealReminder
import app.steadmo.core.MealReminderRule
import app.steadmo.widget.WidgetActionActivity
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MealReminderTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val zone = ZoneId.systemDefault()

    @Before fun setUp() = app.allowNotifications()

    private fun reminderAlarms() = alarms.scheduledAlarms.filter {
        shadowOf(it.operation).savedIntent.component?.className == MealReminderReceiver::class.java.name
    }

    @Test fun `on by default at 18_00, scheduled from app start`() {
        assertTrue(MealReminders.enabled(app))
        assertEquals(LocalTime.of(18, 0), MealReminders.time(app))
        assertEquals(listOf(MealReminder.defaultRule()), MealReminders.rules(app))
        assertEquals(1, reminderAlarms().size)
    }

    @Test fun `a new time reschedules, and off cancels`() {
        val morning = ZonedDateTime.now(zone).withHour(9).withMinute(0).toInstant().toEpochMilli()
        MealReminders.set(app, true, LocalTime.of(19, 30), now = morning)
        val expected = ZonedDateTime.now(zone).withHour(19).withMinute(30).withSecond(0).withNano(0).toInstant().toEpochMilli()
        assertEquals(expected, reminderAlarms().single().triggerAtTime)
        MealReminders.set(app, false)
        assertFalse(MealReminders.enabled(app))
        assertTrue(reminderAlarms().isEmpty())
    }

    @Test fun `adding, updating and deleting rules schedules the soonest active rule`() {
        val mondayMorning = ZonedDateTime.of(2026, 11, 23, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val added = MealReminders.addRule(
            app,
            time = LocalTime.of(12, 30),
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            now = mondayMorning,
        )
        assertEquals(2, added.size)
        val mondayLunch = ZonedDateTime.of(2026, 11, 23, 12, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(mondayLunch, reminderAlarms().single().triggerAtTime)

        // Move the lunch rule to Tuesday only; Monday 18:00 is now soonest.
        val lunch = added.last()
        MealReminders.updateRule(app, lunch.copy(days = setOf(DayOfWeek.TUESDAY)), now = mondayMorning)
        val mondayDinner = ZonedDateTime.of(2026, 11, 23, 18, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(mondayDinner, reminderAlarms().single().triggerAtTime)

        // Delete the dinner rule; Tuesday 12:30 is now soonest.
        MealReminders.deleteRule(app, added.first().id, now = mondayMorning)
        val tuesdayLunch = ZonedDateTime.of(2026, 11, 24, 12, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(tuesdayLunch, reminderAlarms().single().triggerAtTime)

        // Delete the last rule; no alarms remain.
        MealReminders.deleteRule(app, lunch.id, now = mondayMorning)
        assertTrue(MealReminders.rules(app).isEmpty())
        assertFalse(MealReminders.enabled(app))
        assertTrue(reminderAlarms().isEmpty())
    }

    @Test fun `migrates legacy single-reminder prefs into a rule`() {
        app.getSharedPreferences("meal_reminder", android.content.Context.MODE_PRIVATE).edit()
            .clear()
            .putBoolean("on", false)
            .putInt("minute_of_day", 19 * 60 + 15)
            .commit()
        assertEquals(
            listOf(MealReminderRule(id = 1L, time = LocalTime.of(19, 15), enabled = false)),
            MealReminders.rules(app),
        )
    }

    @Test fun `turning it off cancels a pending snooze and the shown reminder`() {
        StatusNotifier(app).showMealReminder()
        MealReminders.snooze(app, 30)
        MealReminders.set(app, false)
        assertTrue(reminderAlarms().isEmpty())
        assertNull(app.posted(7))
        // A snooze already on its way says nothing once it's off.
        MealReminderReceiver().onReceive(app, Intent(app, MealReminderReceiver::class.java).setAction(MealReminders.ACTION_FIRE + ".AGAIN"))
        assertNull(app.posted(7))
    }

    @Test fun `a restart keeps today's reminder until it goes off`() {
        val today = ZonedDateTime.now(zone).withHour(18).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
        MealReminders.schedule(app, now = today - 60 * MINUTE)
        val evening = today + MINUTE
        MealReminders.schedule(app, now = evening)
        assertEquals(today, reminderAlarms().single().triggerAtTime)
        MealReminders.shown(app)
        MealReminders.schedule(app, now = evening)
        assertEquals(today + 24 * 60 * MINUTE, reminderAlarms().single().triggerAtTime)
    }

    @Test fun `a time zone or clock change reschedules`() {
        MealReminders.set(app, false)
        assertTrue(reminderAlarms().isEmpty())
        MealReminders.set(app, true)
        alarms.scheduledAlarms.toList().forEach { a -> a.operation?.let { app.getSystemService(AlarmManager::class.java).cancel(it) } }
        TimeChangeReceiver().onReceive(app, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        assertEquals(1, reminderAlarms().size)
    }

    @Test fun `only the system's clock broadcasts reschedule`() {
        val due = ZonedDateTime.now(zone).withHour(18).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
        MealReminders.schedule(app, now = due - 60 * MINUTE)
        MealReminders.schedule(app, now = due + MINUTE)
        TimeChangeReceiver().onReceive(app, Intent("com.example.SPOOF"))
        MealReminders.schedule(app, now = due + MINUTE)
        assertEquals(due, reminderAlarms().single().triggerAtTime)
    }

    @Test fun `a time zone change drops an overdue alarm set for the old zone`() {
        val london = ZoneId.of("Europe/London")
        val due = ZonedDateTime.of(2026, 11, 27, 18, 0, 0, 0, london).toInstant().toEpochMilli()
        MealReminders.schedule(app, now = due - 60 * MINUTE, zone = london)
        val newYork = ZoneId.of("America/New_York")
        MealReminders.clockChanged(app, now = due + MINUTE, zone = newYork)
        val expected = ZonedDateTime.of(2026, 11, 27, 18, 0, 0, 0, newYork).toInstant().toEpochMilli()
        assertEquals(expected, reminderAlarms().single().triggerAtTime)
    }

    @Test fun `the reminder offers Bolus and two snoozes`() {
        MealReminderReceiver().onReceive(app, Intent(app, MealReminderReceiver::class.java).setAction(MealReminders.ACTION_FIRE))
        val n = app.posted(7)!!
        assertEquals("Time to bolus for your meal?", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(listOf("Bolus", "In 30 min", "In 60 min"), n.actions.map { it.title.toString() })
        val bolusIntent = shadowOf(n.actions[0].actionIntent).savedIntent
        assertEquals("steadmo://widget/camaps/meal", bolusIntent.dataString)
        assertTrue(bolusIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        Robolectric.buildActivity(WidgetActionActivity::class.java, bolusIntent).create()
        assertNull(app.posted(7))
        assertEquals(StatusNotifier.CHANNEL_REMINDERS, n.channelId)
    }

    @Test fun `In 30 min clears it and reminds again later`() {
        StatusNotifier(app).showMealReminder()
        val snooze = shadowOf(app.posted(7)!!.actions[1].actionIntent).savedIntent
        val before = System.currentTimeMillis()
        MealReminderReceiver().onReceive(app, snooze)
        assertNull(app.posted(7))
        val again = reminderAlarms().maxBy { it.triggerAtTime }
        assertTrue(again.triggerAtTime >= before + 30 * MINUTE)

        MealReminderReceiver().onReceive(app, shadowOf(again.operation).savedIntent)
        assertEquals("Time to bolus for your meal?", app.posted(7)!!.extras.getString(Notification.EXTRA_TITLE))
    }
}
