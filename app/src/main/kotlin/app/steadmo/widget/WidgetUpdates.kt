package app.steadmo.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import app.steadmo.GlucoseState
import app.steadmo.SteadmoApp
import app.steadmo.core.SteadmoLog
import app.steadmo.repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SteadmoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = SteadmoWidget()
}

/**
 * Keeps placed widgets current: redrawn on each new reading, and once more
 * when that reading goes stale via an idle-allowed alarm, so an old value
 * greys out on time even during Doze (SPEC.md §4.5, §4.17).
 */
object WidgetUpdates {
    suspend fun onState(context: Context, state: GlucoseState, now: Long = System.currentTimeMillis()) {
        val ids = try {
            GlanceAppWidgetManager(context).getGlanceIds(SteadmoWidget::class.java)
        } catch (e: Exception) {
            SteadmoLog.failure(e, "widget: couldn't list placed widgets")
            return
        }
        if (ids.isEmpty()) return
        SteadmoWidget().updateAll(context)
        scheduleStale(context, state, now)
        SteadmoLog.verbose("widget: %s updated", ids.size)
    }

    /**
     * When the widget next needs redrawing because something on it went
     * stale: the reading or the Auto mode report, whichever expires first
     * after [now]. The redraw re-arms for the other ([WidgetStaleReceiver]).
     */
    fun nextStaleRedraw(state: GlucoseState, now: Long): Long? =
        listOfNotNull(state.latest?.timeMillis, state.statusTime)
            .map { it + WidgetModel.STALE_MILLIS + 5_000 }
            .filter { it > now }
            .minOrNull()

    /**
     * Schedules an alarm once the next thing on the widget goes stale. Uses a
     * non-AllowWhileIdle RTC alarm so it never consumes the app's Doze
     * allow-while-idle quota needed by [app.steadmo.SignalWatch] (the screen is
     * off during Doze, and SignalWatch also refreshes widgets at 15 minutes).
     */
    fun scheduleStale(context: Context, state: GlucoseState, now: Long = System.currentTimeMillis()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = nextStaleRedraw(state, now)
        if (at == null) {
            alarms.cancel(pending(context))
            return
        }
        val op = pending(context)
        if (alarms.canScheduleExactAlarms()) {
            try {
                alarms.setExact(AlarmManager.RTC, at, op)
                SteadmoLog.verbose("widget: exact stale redraw at %s", at)
                return
            } catch (e: SecurityException) {
                SteadmoLog.failure(e, "widget: exact alarms revoked; setting an inexact stale redraw")
            }
        }
        alarms.set(AlarmManager.RTC, at, op)
        SteadmoLog.verbose("widget: stale redraw at %s", at)
    }

    /** Redraws placed widgets and re-arms for whatever expires next (reading or mode). */
    suspend fun redrawStale(context: Context) {
        SteadmoLog.event("widget: something went stale; redrawing")
        SteadmoWidget().updateAll(context)
        scheduleStale(context, context.repository.state.value)
    }

    internal fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, WidgetStaleReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** The alarm [WidgetUpdates.scheduleStale] sets so stale values grey out during Doze. */
class WidgetStaleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val scope = (context.applicationContext as? SteadmoApp)?.appScope ?: CoroutineScope(Dispatchers.Default)
        scope.launch {
            try {
                WidgetUpdates.redrawStale(context.applicationContext)
            } catch (e: Exception) {
                SteadmoLog.failure(e, "widget: stale redraw failed")
            } finally {
                result?.finish()
            }
        }
    }
}
