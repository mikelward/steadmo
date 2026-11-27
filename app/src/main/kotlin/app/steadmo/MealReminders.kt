package app.steadmo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.steadmo.core.MealReminder
import app.steadmo.core.SteadmoLog
import java.time.LocalTime
import java.time.ZoneId

/** The daily meal reminder's setting and alarms (SPEC.md §4.13). */
object MealReminders {
    private const val PREFS = "meal_reminder"
    const val ACTION_FIRE = "app.steadmo.MEAL_REMINDER"
    const val EXTRA_SNOOZE = "snooze_minutes"
    const val EXTRA_EVENT = "event"
    private const val KEY_PENDING = "pending_at"
    private const val KEY_SNOOZE_AT = "snooze_at"
    private const val KEY_SNOOZE_EVENT = "snooze_event"

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("on", true)

    fun time(context: Context): LocalTime =
        LocalTime.ofSecondOfDay(prefs(context).getInt("minute_of_day", MealReminder.DEFAULT_TIME.toSecondOfDay() / 60) * 60L)

    fun set(context: Context, on: Boolean, time: LocalTime = time(context), now: Long = System.currentTimeMillis()) {
        SteadmoLog.event("meal reminder: %s at %s", if (on) "on" else "off", time)
        // A new time replaces the one set, rather than firing a missed one now.
        prefs(context).edit().putBoolean("on", on).putInt("minute_of_day", time.toSecondOfDay() / 60).remove(KEY_PENDING).apply()
        schedule(context, now)
    }

    /** Sets (or clears) tomorrow's or today's reminder; called on start, on change and after each one. */
    fun schedule(context: Context, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val daily = pending(context, 0, Intent(context, MealReminderReceiver::class.java).setAction(ACTION_FIRE))
        if (!enabled(context)) {
            alarms.cancel(daily)
            prefs(context).edit().remove(KEY_PENDING).apply()
            // A calendar meal's snooze shares this alarm; keep it while that's on (the receiver drops a daily one).
            if (!CalendarMealReminders.enabled(context)) {
                alarms.cancel(again(context))
                snoozeDone(context)
            }
            StatusNotifier(context).cancelMealReminder(calendar = false)
            return
        }
        val pending = prefs(context).getLong(KEY_PENDING, -1).takeIf { it >= 0 }
        val at = MealReminder.nextAt(now, time(context), zone, pending)
        prefs(context).edit().putLong(KEY_PENDING, at).apply()
        // On time with "Alarms & reminders" allowed, else within about an hour; either way while idle.
        Alarms.set(context, at, daily)
        SteadmoLog.verbose("meal reminder: next at %s", at)
    }

    /** The clock or time zone changed: work the next one out afresh, as the set one's local time has moved. */
    fun clockChanged(context: Context, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        prefs(context).edit().remove(KEY_PENDING).apply()
        schedule(context, now, zone)
    }

    /** The set reminder went off: the next is tomorrow's, even right after a restart. */
    fun shown(context: Context) = prefs(context).edit().remove(KEY_PENDING).apply()

    /** "In 30 min" or "In 60 min": once more, later. */
    fun snooze(context: Context, minutes: Int, event: String? = null, now: Long = System.currentTimeMillis()) {
        SteadmoLog.event("meal reminder: again in %s min", minutes)
        val at = now + minutes * 60_000L
        prefs(context).edit().putLong(KEY_SNOOZE_AT, at).putString(KEY_SNOOZE_EVENT, event).apply()
        Alarms.set(context, at, again(context, event))
    }

    /**
     * Sets a snooze again on start: taking away "Alarms & reminders" stops
     * Steadmo and cancels its exact alarms, and nothing else re-arms a snooze.
     */
    fun restoreSnooze(context: Context, now: Long = System.currentTimeMillis()) {
        val at = prefs(context).getLong(KEY_SNOOZE_AT, -1).takeIf { it >= 0 } ?: return
        if (now - at > MealReminder.LATE_MILLIS) {
            SteadmoLog.event("meal reminder: a snooze for %s is too old; dropping it", at)
            snoozeDone(context)
            return
        }
        SteadmoLog.event("meal reminder: restoring the snooze for %s", at)
        Alarms.set(context, at, again(context, prefs(context).getString(KEY_SNOOZE_EVENT, null)))
    }

    /** The snooze went off, or was dropped: nothing to restore. */
    fun snoozeDone(context: Context) = prefs(context).edit().remove(KEY_SNOOZE_AT).remove(KEY_SNOOZE_EVENT).apply()

    /** [event] is a calendar meal's heading, kept so the reminder says which meal again. */
    private fun again(context: Context, event: String? = null) = pending(
        context, 1,
        Intent(context, MealReminderReceiver::class.java).setAction(ACTION_FIRE + ".AGAIN").putExtra(EXTRA_EVENT, event),
    )

    private fun pending(context: Context, code: Int, intent: Intent) =
        PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** The clock or time zone changed: the daily reminder's time is local, so set it again. */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Exported for the system's broadcasts; another app could send it anything explicitly.
        if (intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED) {
            SteadmoLog.warning("meal reminder: ignoring unexpected %s", intent.action)
            return
        }
        SteadmoLog.event("meal reminder: %s; rescheduling", intent.action)
        MealReminders.clockChanged(context)
        CalendarMealReminders.scan(context)
    }
}

/** e.g. "Dinner with Sam at 19:00". */
internal fun calendarTitle(context: Context, title: String, start: Long): String =
    "$title at ${android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(start))}"

/** The reminder itself, and its "In 30 min" and "In 60 min" actions. */
class MealReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifier = StatusNotifier(context)
        // Calendar meals have their own setting (SPEC.md §4.14).
        if (intent.action == CalendarMealReminders.ACTION) {
            val title = intent.getStringExtra(CalendarMealReminders.EXTRA_TITLE)
            val start = intent.getLongExtra(CalendarMealReminders.EXTRA_START, 0)
            when {
                title == null || !CalendarMealReminders.enabled(context) -> SteadmoLog.event("calendar meals: off; not reminding")
                // Delivered late: too late to bolus before eating.
                start <= System.currentTimeMillis() -> SteadmoLog.event("calendar meals: meal already started; not reminding")
                else -> {
                    SteadmoLog.event("calendar meals: reminder")
                    notifier.showMealReminder(calendarTitle(context, title, start))
                    intent.getStringExtra(CalendarMealReminders.EXTRA_KEY)?.let { CalendarMealReminders.reminded(context, it) }
                }
            }
            CalendarMealReminders.scan(context)
            return
        }
        val snooze = intent.getIntExtra(MealReminders.EXTRA_SNOOZE, 0)
        val daily = intent.action == MealReminders.ACTION_FIRE
        // A snooze carries its calendar event, if it came from one: each answers to its own setting.
        val calendar = intent.getStringExtra(MealReminders.EXTRA_EVENT) != null
        val on = if (calendar) CalendarMealReminders.enabled(context) else MealReminders.enabled(context)
        if (!on) {
            // Turned off since this was set (a snooze, say): say nothing.
            SteadmoLog.event("meal reminder: off; not reminding")
            if (intent.action == MealReminders.ACTION_FIRE + ".AGAIN") MealReminders.snoozeDone(context)
            return
        }
        when {
            snooze > 0 -> {
                notifier.cancelMealReminder()
                MealReminders.snooze(context, snooze, intent.getStringExtra(MealReminders.EXTRA_EVENT))
            }
            daily -> {
                SteadmoLog.event("meal reminder: daily")
                notifier.showMealReminder()
                MealReminders.shown(context)
                MealReminders.schedule(context)
            }
            else -> {
                MealReminders.snoozeDone(context)
                SteadmoLog.event("meal reminder: again")
                notifier.showMealReminder(intent.getStringExtra(MealReminders.EXTRA_EVENT))
            }
        }
    }
}
