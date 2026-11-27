package app.steadmo

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.steadmo.core.CalendarMeals
import app.steadmo.core.SteadmoLog
import java.util.concurrent.TimeUnit

/**
 * Pre-bolus reminders for calendar meals and restaurant bookings (SPEC.md
 * §4.14). Off until turned on, which asks for calendar access. The calendar
 * is read on the device; only an event's title and start are kept, in the
 * pending alarm.
 */
object CalendarMealReminders {
    private const val PREFS = "calendar_meals"
    const val EXTRA_TITLE = "title"
    const val EXTRA_START = "start"
    const val EXTRA_KEY = "key"
    const val ACTION = "app.steadmo.CALENDAR_MEAL"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", false)

    fun permitted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun set(context: Context, on: Boolean) {
        SteadmoLog.event("calendar meals: %s", if (on) "on" else "off")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
        val work = WorkManager.getInstance(context)
        if (on) {
            work.enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CalendarScanWorker>(3, TimeUnit.HOURS).build(),
            )
        } else {
            work.cancelUniqueWork(WORK)
            // A snooze already set for one says nothing once it's off (MealReminderReceiver).
            StatusNotifier(context).cancelMealReminder(calendar = true)
        }
        scan(context)
    }

    /** Sets the alarm for the next meal event, or clears it. */
    fun scan(
        context: Context,
        now: Long = System.currentTimeMillis(),
        events: () -> List<CalendarMeals.Event> = { query(context, now) },
    ) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (!enabled(context) || !permitted(context)) {
            alarms.cancel(pending(context, null, 0))
            return
        }
        val next = CalendarMeals.next(events(), now, reminded(context))
        if (next == null) {
            SteadmoLog.event("calendar meals: no meal in the next day")
            alarms.cancel(pending(context, null, 0))
            return
        }
        val (event, at) = next
        SteadmoLog.event("calendar meals: next reminder at %s", at)
        // On time with "Alarms & reminders" allowed, else within about an hour; a time already past goes off at once.
        Alarms.set(context, at, pending(context, event.title, event.start, event.key))
    }

    /** An event's reminder went off: don't set it again, even after a restart. Keeps only events still ahead. */
    fun reminded(context: Context, key: String, now: Long = System.currentTimeMillis()) {
        val kept = reminded(context).filter { (it.substringAfter(':').toLongOrNull() ?: 0) > now }.toSet()
        prefs(context).edit().putStringSet(KEY_REMINDED, kept + key).apply()
    }

    private fun reminded(context: Context): Set<String> = prefs(context).getStringSet(KEY_REMINDED, null).orEmpty()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun pending(context: Context, title: String?, start: Long, key: String? = null) = PendingIntent.getBroadcast(
        context, 2,
        Intent(context, MealReminderReceiver::class.java).setAction(ACTION)
            .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_START, start).putExtra(EXTRA_KEY, key),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private const val KEY_REMINDED = "reminded"

    /** The next day's event instances, timed and all-day, from every visible calendar. */
    private fun query(context: Context, now: Long): List<CalendarMeals.Event> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, now + CalendarMeals.LOOKAHEAD_MILLIS)
        }.build()
        val columns = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY,
        )
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 " +
            "AND (${CalendarContract.Instances.STATUS} IS NULL OR ${CalendarContract.Instances.STATUS} != ${CalendarContract.Events.STATUS_CANCELED}) " +
            "AND (${CalendarContract.Instances.SELF_ATTENDEE_STATUS} IS NULL OR ${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED})"
        return try {
            context.contentResolver.query(uri, columns, selection, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) add(CalendarMeals.Event(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2), c.getInt(3) != 0))
                }
            }.orEmpty()
        } catch (e: SecurityException) {
            SteadmoLog.failure(e, "calendar meals: calendar access was withdrawn")
            emptyList()
        }
    }

    private const val WORK = "calendar-meals"
}

/** Looks again every few hours, for events added since. */
class CalendarScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        CalendarMealReminders.scan(applicationContext)
        return Result.success()
    }
}
