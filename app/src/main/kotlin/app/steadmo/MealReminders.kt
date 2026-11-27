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
    private const val KEY_PENDING = "pending_at"

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
            alarms.cancel(again(context))
            StatusNotifier(context).cancelMealReminder()
            prefs(context).edit().remove(KEY_PENDING).apply()
            return
        }
        val pending = prefs(context).getLong(KEY_PENDING, -1).takeIf { it >= 0 }
        val at = MealReminder.nextAt(now, time(context), zone, pending)
        prefs(context).edit().putLong(KEY_PENDING, at).apply()
        // Allowed while idle, so Doze can't hold it until the phone wakes; needs no exact-alarm permission.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, daily)
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
    fun snooze(context: Context, minutes: Int, now: Long = System.currentTimeMillis()) {
        SteadmoLog.event("meal reminder: again in %s min", minutes)
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + minutes * 60_000L, again(context))
    }

    private fun again(context: Context) =
        pending(context, 1, Intent(context, MealReminderReceiver::class.java).setAction(ACTION_FIRE + ".AGAIN"))

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
    }
}

/** The reminder itself, and its "In 30 min" and "In 60 min" actions. */
class MealReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifier = StatusNotifier(context)
        if (!MealReminders.enabled(context)) {
            // Turned off since this was set (a snooze, say): say nothing.
            SteadmoLog.event("meal reminder: off; not reminding")
            return
        }
        val snooze = intent.getIntExtra(MealReminders.EXTRA_SNOOZE, 0)
        when {
            snooze > 0 -> {
                notifier.cancelMealReminder()
                MealReminders.snooze(context, snooze)
            }
            intent.action == MealReminders.ACTION_FIRE -> {
                SteadmoLog.event("meal reminder: daily")
                notifier.showMealReminder()
                MealReminders.shown(context)
                MealReminders.schedule(context)
            }
            else -> {
                SteadmoLog.event("meal reminder: again")
                notifier.showMealReminder()
            }
        }
    }
}
