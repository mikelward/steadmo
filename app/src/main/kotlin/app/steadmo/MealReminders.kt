package app.steadmo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.steadmo.core.CalendarMeals
import app.steadmo.core.MealReminder
import app.steadmo.core.MealReminderRule
import app.steadmo.core.SteadmoLog
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId

/** Scheduled meal reminder rules and alarms (SPEC.md §4.13). */
object MealReminders {
    private const val PREFS = "meal_reminder"
    const val ACTION_FIRE = "app.steadmo.MEAL_REMINDER"
    const val EXTRA_SNOOZE = "snooze_minutes"
    const val EXTRA_EVENT = "event"
    private const val KEY_RULES = "rules"
    private const val KEY_PENDING = "pending_at"
    private const val KEY_SNOOZE_AT = "snooze_at"
    private const val KEY_SNOOZE_EVENT = "snooze_event"

    /** All saved rules, migrating the legacy single daily reminder if needed. */
    fun rules(context: Context): List<MealReminderRule> {
        val p = prefs(context)
        if (p.contains(KEY_RULES)) return MealReminder.decode(p.getString(KEY_RULES, "").orEmpty())
        val on = p.getBoolean("on", true)
        val time = LocalTime.ofSecondOfDay(p.getInt("minute_of_day", MealReminder.DEFAULT_TIME.toSecondOfDay() / 60) * 60L)
        return listOf(MealReminder.defaultRule(on = on, time = time))
    }

    /** Whether at least one scheduled rule is enabled with at least one day selected. */
    fun enabled(context: Context): Boolean = rules(context).any { it.enabled && it.days.isNotEmpty() }

    fun time(context: Context): LocalTime = rules(context).firstOrNull()?.time ?: MealReminder.DEFAULT_TIME

    fun setRules(context: Context, rules: List<MealReminderRule>, now: Long = System.currentTimeMillis()) {
        SteadmoLog.event("meal reminder: %s rule(s) saved (%s active)", rules.size, rules.count { it.enabled && it.days.isNotEmpty() })
        // A rule change replaces any pending alarm, rather than firing an obsolete one now.
        prefs(context).edit().putString(KEY_RULES, MealReminder.encode(rules)).remove(KEY_PENDING).apply()
        schedule(context, now)
    }

    fun addRule(
        context: Context,
        time: LocalTime,
        days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
        now: Long = System.currentTimeMillis(),
    ): List<MealReminderRule> {
        val current = rules(context)
        val id = (current.maxOfOrNull { it.id } ?: 0L) + 1L
        val updated = current + MealReminderRule(id = id, time = time, days = days, enabled = true)
        setRules(context, updated, now)
        return updated
    }

    fun updateRule(context: Context, rule: MealReminderRule, now: Long = System.currentTimeMillis()): List<MealReminderRule> {
        val updated = rules(context).map { if (it.id == rule.id) rule else it }
        setRules(context, updated, now)
        return updated
    }

    fun deleteRule(context: Context, id: Long, now: Long = System.currentTimeMillis()): List<MealReminderRule> {
        val updated = rules(context).filterNot { it.id == id }
        setRules(context, updated, now)
        return updated
    }

    fun set(context: Context, on: Boolean, time: LocalTime = time(context), now: Long = System.currentTimeMillis()) {
        val current = rules(context)
        val updated = if (current.isEmpty()) {
            listOf(MealReminder.defaultRule(on = on, time = time))
        } else {
            listOf(current.first().copy(enabled = on, time = time)) + current.drop(1)
        }
        setRules(context, updated, now)
    }

    /** Sets (or clears) the soonest scheduled reminder; called on start, on change and after each one. */
    fun schedule(context: Context, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val daily = pending(context, 0, Intent(context, MealReminderReceiver::class.java).setAction(ACTION_FIRE))
        val pending = prefs(context).getLong(KEY_PENDING, -1).takeIf { it >= 0 }
        val at = MealReminder.nextAt(now, rules(context), zone, pending)
        if (at == null) {
            alarms.cancel(daily)
            prefs(context).edit().remove(KEY_PENDING).apply()
            cancelSnooze(context, calendar = false)
            StatusNotifier(context).cancelMealReminder(calendar = false)
            return
        }
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
        val event = prefs(context).getString(KEY_SNOOZE_EVENT, null)
        val on = if (event != null) CalendarMealReminders.enabled(context) else enabled(context)
        if (!on || now - at > MealReminder.LATE_MILLIS) {
            SteadmoLog.event("meal reminder: dropping saved snooze for %s (on=%s)", at, on)
            snoozeDone(context)
            return
        }
        SteadmoLog.event("meal reminder: restoring the snooze for %s", at)
        Alarms.set(context, at, again(context, event))
    }

    /** Cancels a pending snooze if it belongs to a calendar meal ([calendar]) or the daily reminder, leaving the other. */
    fun cancelSnooze(context: Context, calendar: Boolean) {
        val hasSnooze = prefs(context).getLong(KEY_SNOOZE_AT, -1) >= 0
        val isCalendar = prefs(context).getString(KEY_SNOOZE_EVENT, null) != null
        if (!hasSnooze || isCalendar != calendar) return
        PendingIntent.getBroadcast(
            context, 1,
            Intent(context, MealReminderReceiver::class.java).setAction(ACTION_FIRE + ".AGAIN"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
        snoozeDone(context)
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
                // Delivered long after it started: the meal is well underway.
                !CalendarMeals.current(start, System.currentTimeMillis()) ->
                    SteadmoLog.event("calendar meals: meal started too long ago; not reminding")
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
