package app.steadmo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.steadmo.core.Heartbeat
import app.steadmo.core.SteadmoLog

/**
 * Warns when no glucose has come from CamAPS FX for 15 minutes (SPEC.md §4.7),
 * by an alarm set from each reading. It adds to CamAPS FX's own signal loss
 * alarm; it doesn't replace it.
 */
object SignalWatch {
    private const val PREFS = "signal_watch"
    private const val KEY_WARNED = "warned_at"

    /** Checks now and sets the next alarm; called by the alarm, on start, and after each reading. */
    fun run(context: Context, now: Long = System.currentTimeMillis(), notifier: StatusNotifier = StatusNotifier(context)) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val warned = prefs.getLong(KEY_WARNED, 0L).takeIf { it > 0 }
        val latest = context.repository.state.value.latest?.timeMillis
        when (val decision = Heartbeat.check(latest, now, warned)) {
            Heartbeat.Decision.Idle -> {
                SteadmoLog.event("signal: no check needed (latest %s)", latest)
                cancel(context)
            }
            is Heartbeat.Decision.CheckAt -> schedule(context, decision.at)
            is Heartbeat.Decision.Warn -> {
                val access = context.getSystemService(android.app.NotificationManager::class.java)
                    .isNotificationListenerAccessGranted(android.content.ComponentName(context, CamapsListener::class.java))
                SteadmoLog.warning("signal: nothing for %s min; notification access %s", decision.quietMinutes, access)
                notifier.showSignalLoss(decision.quietMinutes, latest!!, access)
                prefs.edit().putLong(KEY_WARNED, now).apply()
                schedule(context, decision.nextCheck)
            }
        }
    }

    /**
     * A reading arrived: clear any warning and watch from it. A replayed one
     * already past the silence threshold doesn't mean glucose is back, so the
     * warning stays and the usual check decides what's next.
     */
    fun onReading(context: Context, readingTime: Long, notifier: StatusNotifier, now: Long = System.currentTimeMillis()) {
        if (now - readingTime >= Heartbeat.QUIET_MILLIS) {
            SteadmoLog.event("signal: replayed reading %s ms old; still no current glucose", now - readingTime)
            run(context, now, notifier)
            return
        }
        notifier.cancelSignalLoss()
        schedule(context, readingTime + Heartbeat.QUIET_MILLIS)
    }

    private fun schedule(context: Context, at: Long) {
        SteadmoLog.verbose("signal: next check at %s", at)
        // Inexact but allowed while idle: within minutes is enough, and needs no exact-alarm permission.
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
    }

    private fun cancel(context: Context) = context.getSystemService(AlarmManager::class.java).cancel(pending(context))

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, SignalWatchReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** The alarm [SignalWatch] sets. */
class SignalWatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SteadmoLog.event("signal: alarm")
        SignalWatch.run(context)
    }
}
