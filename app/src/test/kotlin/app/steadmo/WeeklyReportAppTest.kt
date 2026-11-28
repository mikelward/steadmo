package app.steadmo

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.steadmo.core.CamapsStatus
import app.steadmo.core.GlucoseUnit
import app.steadmo.core.Reading
import app.steadmo.core.WeeklyReport
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WeeklyReportAppTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val zone = ZoneId.systemDefault()

    @Before fun setUp() = app.allowNotifications()

    private fun weeklyAlarms() = alarms.scheduledAlarms.filter {
        shadowOf(it.operation).savedIntent.component?.className == WeeklyReportReceiver::class.java.name
    }

    @Test fun `on by default Saturday at 11_00, scheduled on app start`() {
        assertTrue(WeeklyReports.enabled(app))
        assertEquals(DayOfWeek.SATURDAY, WeeklyReports.day(app))
        assertEquals(LocalTime.of(11, 0), WeeklyReports.time(app))
        assertEquals(1, weeklyAlarms().size)
    }

    @Test fun `changing day and time reschedules, turning off cancels alarm and notification`() {
        val wed = ZonedDateTime.of(2026, 11, 25, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        WeeklyReports.set(app, on = true, day = DayOfWeek.THURSDAY, time = LocalTime.of(19, 30), now = wed)
        assertEquals(DayOfWeek.THURSDAY, WeeklyReports.day(app))
        assertEquals(LocalTime.of(19, 30), WeeklyReports.time(app))
        val expected = ZonedDateTime.of(2026, 11, 26, 19, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, weeklyAlarms().single().triggerAtTime)

        val summary = WeeklyReport.generate(listOf(Reading(wed, 6.5)), wed, zone)
        StatusNotifier(app).showWeeklyReport(summary, GlucoseUnit.MMOL)
        assertNotNull(app.posted(StatusNotifier.ID_WEEKLY_REPORT))

        WeeklyReports.set(app, on = false)
        assertFalse(WeeklyReports.enabled(app))
        assertTrue(weeklyAlarms().isEmpty())
        assertNull(app.posted(StatusNotifier.ID_WEEKLY_REPORT))
    }

    @Test fun `time zone change and exact alarm permission reschedule weekly report`() {
        alarms.scheduledAlarms.toList().forEach { a -> a.operation?.let { app.getSystemService(AlarmManager::class.java).cancel(it) } }
        assertTrue(weeklyAlarms().isEmpty())

        TimeChangeReceiver().onReceive(app, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        assertEquals(1, weeklyAlarms().size)

        alarms.scheduledAlarms.toList().forEach { a -> a.operation?.let { app.getSystemService(AlarmManager::class.java).cancel(it) } }
        ExactAlarmPermissionReceiver().onReceive(app, Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))
        assertEquals(1, weeklyAlarms().size)
    }

    @Test fun `receiver skips when off or when there are no readings`() {
        WeeklyReports.set(app, on = false)
        WeeklyReportReceiver().onReceive(app, Intent(app, WeeklyReportReceiver::class.java).setAction(WeeklyReports.ACTION_FIRE))
        assertNull(app.posted(StatusNotifier.ID_WEEKLY_REPORT))

        WeeklyReports.set(app, on = true)
        WeeklyReportReceiver().onReceive(app, Intent(app, WeeklyReportReceiver::class.java).setAction(WeeklyReports.ACTION_FIRE))
        assertNull(app.posted(StatusNotifier.ID_WEEKLY_REPORT))
        assertEquals(1, weeklyAlarms().size)
    }

    @Test fun `receiver posts notification with summary and opens weekly report screen`() {
        val now = System.currentTimeMillis()
        val repo = app.repository
        repo.onCamapsNotification(
            NotificationDump(now - 10 * MINUTE, CAMAPS, "info", null, emptyList(), emptyList(), emptyList()),
            CamapsStatus(6.4, null, GlucoseUnit.MMOL, "On"),
        )
        repo.onCamapsNotification(
            NotificationDump(now - 5 * MINUTE, CAMAPS, "info", null, emptyList(), emptyList(), emptyList()),
            CamapsStatus(6.8, null, GlucoseUnit.MMOL, "On"),
        )

        WeeklyReportReceiver().onReceive(app, Intent(app, WeeklyReportReceiver::class.java).setAction(WeeklyReports.ACTION_FIRE))

        val n = app.posted(StatusNotifier.ID_WEEKLY_REPORT)
        assertNotNull(n)
        assertEquals(StatusNotifier.CHANNEL_REMINDERS, n!!.channelId)
        assertEquals("Weekly report · 100% in range", n.extras.getString(Notification.EXTRA_TITLE))
        val target = shadowOf(n.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, target.component?.className)
        assertEquals(Screen.WEEKLY_REPORT.name, target.getStringExtra(MainActivity.EXTRA_SCREEN))
    }

    @Test fun `share opens a chooser with plain text report`() {
        WeeklyReports.share(app, "Steadmo weekly report")
        val started = shadowOf(app).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_CHOOSER, started.action)
    }

    @Test fun `share falls back to clipboard when no app handles it`() {
        shadowOf(app).checkActivities(true)
        WeeklyReports.share(app, "Steadmo weekly report")
        val clip = app.getSystemService(android.content.ClipboardManager::class.java).primaryClip!!
        assertEquals("Steadmo weekly report", clip.getItemAt(0).text.toString())
    }

    @Test fun `weekly report screen shows empty state and populated issues`() {
        val now = ZonedDateTime.of(2026, 11, 28, 11, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        val empty = WeeklyReport.generate(emptyList(), now, ZoneId.of("UTC"))
        var backed = false
        compose.setContent {
            SteadmoTheme(dark = false) {
                WeeklyReportScreen(summary = empty, unit = GlucoseUnit.MMOL, zone = ZoneId.of("UTC"), onBack = { backed = true })
            }
        }
        compose.onNodeWithText("No readings in the last 7 days yet").assertExists()
        compose.onNodeWithText("Share").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        assertTrue(backed)
    }

    @Test fun `weekly report dialog toggles and picks day and time`() {
        var toggled: Boolean? = null
        var pickedDay: DayOfWeek? = null
        var pickedTime = false
        compose.setContent {
            SteadmoTheme(dark = false) {
                WeeklyReportDialog(
                    on = true,
                    day = DayOfWeek.SATURDAY,
                    time = "11:00",
                    onToggle = { toggled = it },
                    onPickDay = { pickedDay = it },
                    onPickTime = { pickedTime = true },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Thursday").performClick()
        assertEquals(DayOfWeek.THURSDAY, pickedDay)
        compose.onNodeWithText("Change").performClick()
        assertTrue(pickedTime)
        compose.onNodeWithText("Weekly notification").performClick()
        assertEquals(false, toggled)
    }
}
