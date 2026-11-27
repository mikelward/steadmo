package app.steadmo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.steadmo.core.SteadmoLog

/**
 * Steadmo's alarms: reminders, the signal-loss check and the recheck after a
 * treated low. With "Alarms & reminders" allowed they go off on time;
 * without it, Android may deliver them up to an hour late (SPEC.md §4.13).
 * Either way they go off while the phone is idle.
 */
object Alarms {
    fun exactAllowed(context: Context): Boolean = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun set(context: Context, at: Long, operation: PendingIntent) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (alarms.canScheduleExactAlarms()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
                return
            } catch (e: SecurityException) {
                // Revoked between the check and the call: inexact still goes off.
                SteadmoLog.failure(e, "alarms: exact alarms revoked; setting an inexact one")
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
    }
}

/**
 * "Alarms & reminders" was allowed: set the reminders again, on time. Taking
 * it away stops Steadmo instead, so app start restores what it cancelled.
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Exported for the system's broadcast; another app could send it anything explicitly.
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            SteadmoLog.warning("alarms: ignoring unexpected %s", intent.action)
            return
        }
        SteadmoLog.event("alarms: exact alarms now %s; rescheduling reminders", if (Alarms.exactAllowed(context)) "allowed" else "not allowed")
        MealReminders.schedule(context)
        MealReminders.restoreSnooze(context)
        CalendarMealReminders.scan(context)
        Recheck.restore(context, context.repository.state.value.treatments)
        SignalWatch.run(context)
    }
}
