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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).remove(KEY_SINCE).apply()

    private const val KEY_SINCE = "alerting_since"

    /** When the current unacknowledged alarm first sounded, or null. */
    fun alertingSince(context: Context): Long? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_SINCE, 0L).takeIf { it > 0 }

    fun startAlerting(context: Context, at: Long) {
        if (alertingSince(context) == null) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_SINCE, at).apply()
        }
    }

    /** OK or Treated: quiet for a while, and the next alarm starts a new count. */
    fun acknowledge(context: Context, at: Long) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY, at).remove(KEY_SINCE).apply()
}

/** OK on the urgent low alarm, or the alarm swiped away: quiet it for a while. */
class UrgentLowAckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val expires = intent.getLongExtra(EXTRA_EXPIRES, Long.MAX_VALUE)
        if (intent.action == ACTION_DISMISSED && System.currentTimeMillis() >= expires - EXPIRY_SLACK_MILLIS) {
            // Removed because its reading went stale, not by the user: the next reading may sound again.
            SteadmoLog.event("urgent low: timed out with its reading; not an acknowledgement")
            return
        }
        SteadmoLog.event("urgent low: acknowledged")
        UrgentLowAck.acknowledge(context, System.currentTimeMillis())
        StatusNotifier(context).cancelUrgentLow()
    }

    companion object {
        const val ACTION_DISMISSED = "app.steadmo.URGENT_LOW_DISMISSED"
        const val EXTRA_EXPIRES = "expires"

        /** The timeout's broadcast can arrive a moment before the expiry Steadmo worked out. */
        private const val EXPIRY_SLACK_MILLIS = 5_000L
    }
}
