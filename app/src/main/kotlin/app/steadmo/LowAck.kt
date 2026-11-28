package app.steadmo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.steadmo.core.SteadmoLog

/** When the user last acknowledged or heard the low (< 3.9) alarm (SPEC.md §4.8). */
object LowAck {
    private const val PREFS = "low_alert"
    private const val KEY_ACK = "acknowledged_at"
    private const val KEY_ALERTED = "last_alerted_at"

    fun get(context: Context): Long? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_ACK, 0L).takeIf { it > 0 }

    fun lastAlerted(context: Context): Long? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_ALERTED, 0L).takeIf { it > 0 }

    fun recordAlert(context: Context, at: Long) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_ALERTED, at).apply()

    /** OK or swipe: quiet Low for 15 minutes (without suppressing Urgent Low). */
    fun acknowledge(context: Context, at: Long) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_ACK, at).remove(KEY_ALERTED).apply()

    fun clear(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ACK).remove(KEY_ALERTED).apply()
}

/** OK on the low alarm, or the alarm swiped away: quiet Low for 15 minutes. */
class LowAckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val expires = intent.getLongExtra(EXTRA_EXPIRES, Long.MAX_VALUE)
        if (intent.action == ACTION_DISMISSED && System.currentTimeMillis() >= expires - EXPIRY_SLACK_MILLIS) {
            // Removed because its reading went stale, not by the user: the next reading may sound again.
            SteadmoLog.event("low: timed out with its reading; not an acknowledgement")
            return
        }
        SteadmoLog.event("low: acknowledged")
        LowAck.acknowledge(context, System.currentTimeMillis())
        StatusNotifier(context).cancelLow()
    }

    companion object {
        const val ACTION_DISMISSED = "app.steadmo.LOW_DISMISSED"
        const val EXTRA_EXPIRES = "expires"

        /** The timeout's broadcast can arrive a moment before the expiry Steadmo worked out. */
        private const val EXPIRY_SLACK_MILLIS = 5_000L
    }
}
