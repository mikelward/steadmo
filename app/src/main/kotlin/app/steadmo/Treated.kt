package app.steadmo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.steadmo.core.SteadmoLog
import app.steadmo.core.Treatment

/** "Treated" on a low alert or the urgent low alarm (SPEC.md §4.10). */
class TreatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SteadmoLog.event("treated: from a notification")
        context.repository.recordTreatment(System.currentTimeMillis())
    }
}

/** The recheck reminder 15 minutes after "Treated". */
class RecheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SteadmoLog.event("treated: recheck reminder")
        val state = context.repository.state.value
        StatusNotifier(context).showRecheck(state.latest, state.unit)
    }
}

internal object Recheck {
    fun schedule(context: Context, treatedAt: Long) {
        val pending = PendingIntent.getBroadcast(
            context, 1, Intent(context, RecheckReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Inexact but allowed while idle: within a few minutes is fine for a recheck.
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, treatedAt + Treatment.RECHECK_MILLIS, pending)
    }
}
