package app.steadmo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.steadmo.core.SteadmoLog

/** When the user last acknowledged the urgent low alarm (OK, or swiping it away). */
object UrgentLowAck {
    private const val PREFS = "urgent_low"
    private const val KEY = "acknowledged_at"

    fun get(context: Context): Long? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY, 0L).takeIf { it > 0 }

    fun set(context: Context, at: Long) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY, at).apply()

    fun clear(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
}

/** OK on the urgent low alarm, or the alarm swiped away: quiet it for a while. */
class UrgentLowAckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SteadmoLog.event("urgent low: acknowledged")
        UrgentLowAck.set(context, System.currentTimeMillis())
        StatusNotifier(context).cancelUrgentLow()
    }
}
