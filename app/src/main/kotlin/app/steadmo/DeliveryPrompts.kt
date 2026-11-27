package app.steadmo

import android.content.Context
import app.steadmo.core.DeliveryApp
import app.steadmo.core.DeliveryPrompt
import app.steadmo.core.SteadmoLog

/**
 * Suggests a pre-bolus when a delivery app says the rider is nearby
 * (SPEC.md §4.12). What the app's notification says is read, never stored
 * or logged: only the app, and whether it matched, are.
 */
object DeliveryPrompts {
    private const val PREFS = "delivery"

    /** The last delivery notification's words, for a bug report the user sends; memory only. */
    @Volatile var lastSeen: String? = null
        private set

    fun onNotification(
        context: Context,
        app: DeliveryApp,
        texts: List<String>,
        postTime: Long,
        now: Long,
        notifier: StatusNotifier,
    ) {
        lastSeen = "${app.label}: ${texts.joinToString(" | ")}"
        if (!DeliveryPrompt.fresh(postTime, now)) {
            SteadmoLog.event("delivery: %s post is %s ms old; not prompting", app.label, now - postTime)
            return
        }
        if (!DeliveryPrompt.nearby(texts)) {
            SteadmoLog.event("delivery: %s update, rider not nearby", app.label)
            return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(app.name, 0L).takeIf { it > 0 }
        if (!DeliveryPrompt.due(last, now)) {
            SteadmoLog.event("delivery: %s rider nearby; prompted %s ms ago", app.label, now - last!!)
            return
        }
        SteadmoLog.event("delivery: %s rider nearby; suggesting a pre-bolus", app.label)
        prefs.edit().putLong(app.name, now).apply()
        notifier.showPreBolus(app.label)
    }
}
