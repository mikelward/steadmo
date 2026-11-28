package app.steadmo

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

/** The recheck 15 minutes after "Treated" (SPEC.md §4.10). */
class RecheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SteadmoLog.event("treated: recheck alarm")
        context.repository.onTreatmentRecheck()
    }
}

internal object Recheck {
    /**
     * Sets the recheck again on start if it's still ahead: taking away "Alarms
     * & reminders" stops Steadmo and cancels its exact alarms.
     */
    fun restore(context: Context, treatments: List<Long>, now: Long = System.currentTimeMillis()) {
        val last = treatments.maxOrNull() ?: return
        if (last + Treatment.RECHECK_MILLIS > now) {
            SteadmoLog.event("treated: restoring the recheck for %s", last + Treatment.RECHECK_MILLIS)
            schedule(context, last)
        }
    }

    fun schedule(context: Context, treatedAt: Long) {
        val pending = PendingIntent.getBroadcast(
            context, 1, Intent(context, RecheckReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        Alarms.set(context, treatedAt + Treatment.RECHECK_MILLIS, pending)
    }
}
