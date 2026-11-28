package app.steadmo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import app.steadmo.core.SteadmoLog
import app.steadmo.core.WeeklyReport
import app.steadmo.core.WeeklyReportSchedule
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId

/** The weekly report's setting, schedule and share helper (SPEC.md §4.16). */
object WeeklyReports {
    private const val PREFS = "weekly_report"
    const val ACTION_FIRE = "app.steadmo.WEEKLY_REPORT"
    private const val KEY_ON = "on"
    private const val KEY_DAY = "day_of_week"
    private const val KEY_MINUTE = "minute_of_day"
    private const val KEY_PENDING = "pending_at"

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, true)

    fun day(context: Context): DayOfWeek {
        val value = prefs(context).getInt(KEY_DAY, WeeklyReportSchedule.DEFAULT_DAY.value)
        return DayOfWeek.entries.firstOrNull { it.value == value } ?: WeeklyReportSchedule.DEFAULT_DAY
    }

    fun time(context: Context): LocalTime {
        val defaultMinute = WeeklyReportSchedule.DEFAULT_TIME.toSecondOfDay() / 60
        val minute = prefs(context).getInt(KEY_MINUTE, defaultMinute).coerceIn(0, 24 * 60 - 1)
        return LocalTime.ofSecondOfDay(minute * 60L)
    }

    fun set(
        context: Context,
        on: Boolean,
        day: DayOfWeek = day(context),
        time: LocalTime = time(context),
        now: Long = System.currentTimeMillis(),
    ) {
        SteadmoLog.event("weekly report: %s on %s at %s", if (on) "on" else "off", day, time)
        prefs(context).edit()
            .putBoolean(KEY_ON, on)
            .putInt(KEY_DAY, day.value)
            .putInt(KEY_MINUTE, time.toSecondOfDay() / 60)
            .remove(KEY_PENDING)
            .apply()
        schedule(context, now)
    }

    /** Sets (or clears) the next weekly report alarm; called on start, on change and after each report. */
    fun schedule(context: Context, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val operation = pending(context)
        if (!enabled(context)) {
            alarms.cancel(operation)
            prefs(context).edit().remove(KEY_PENDING).apply()
            StatusNotifier(context).cancelWeeklyReport()
            return
        }
        val existing = prefs(context).getLong(KEY_PENDING, -1).takeIf { it >= 0 }
        val at = WeeklyReportSchedule.nextAt(now, day(context), time(context), zone, existing)
        prefs(context).edit().putLong(KEY_PENDING, at).apply()
        Alarms.set(context, at, operation)
        SteadmoLog.verbose("weekly report: next at %s", at)
    }

    /** The clock or time zone changed: compute the next report afresh in local time. */
    fun clockChanged(context: Context, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        prefs(context).edit().remove(KEY_PENDING).apply()
        schedule(context, now, zone)
    }

    /** The scheduled report fired: the next one is a week later. */
    fun shown(context: Context) = prefs(context).edit().remove(KEY_PENDING).apply()

    /** Shares the plain-text weekly report via Android's share sheet, with clipboard fallback. */
    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.weekly_report_subject))
            .putExtra(Intent.EXTRA_TEXT, text)
        val chooser = Intent.createChooser(send, context.getString(R.string.weekly_report_chooser_title))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
            SteadmoLog.event("weekly report: share sheet opened")
        } catch (e: ActivityNotFoundException) {
            SteadmoLog.failure(e, "weekly report: no app to share with; copying instead")
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.weekly_report_subject), text))
            Toast.makeText(context, R.string.weekly_report_copied, Toast.LENGTH_LONG).show()
        }
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 20,
        Intent(context, WeeklyReportReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Posts the weekly report notification when its alarm fires, then schedules next week's. */
class WeeklyReportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!WeeklyReports.enabled(context)) {
            SteadmoLog.event("weekly report: off; not posting")
            return
        }
        val state = context.repository.state.value
        val now = System.currentTimeMillis()
        val summary = WeeklyReport.generate(state.readings, now, ZoneId.systemDefault(), state.unit, state.treatments)
        if (summary.readingCount > 0) {
            SteadmoLog.event(
                "weekly report: posting (%s readings, %s%% in range, %s issues)",
                summary.readingCount, summary.inRangePercent, summary.issues.size,
            )
            StatusNotifier(context).showWeeklyReport(summary, state.unit)
        } else {
            SteadmoLog.event("weekly report: no readings in the last 7 days; skipping notification")
        }
        WeeklyReports.shown(context)
        WeeklyReports.schedule(context, now)
    }
}
