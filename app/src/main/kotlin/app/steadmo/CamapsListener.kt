package app.steadmo

import app.steadmo.core.SteadmoLog
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.steadmo.core.CamapsParser
import app.steadmo.core.DeliveryApp

/** Reads CamAPS FX's notifications as the system posts them (SPEC.md §3). */
class CamapsListener : NotificationListenerService() {

    override fun onListenerConnected() {
        SteadmoLog.event("listener: connected")
        repository.setListenerConnected(true)
        try {
            activeNotifications?.forEach(::handle)
        } catch (e: SecurityException) {
            SteadmoLog.failure(e, "listener: couldn't read active notifications on connect")
        }
    }

    override fun onListenerDisconnected() {
        SteadmoLog.warning("listener: disconnected; asking to rebind")
        repository.setListenerConnected(false)
        requestRebind(ComponentName(this, CamapsListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

    /**
     * The user changed a channel's settings (minimized CamAPS FX's, say): pick
     * up its new importance now, rather than at CamAPS FX's next post.
     */
    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        val camaps = try {
            activeNotifications?.firstOrNull { it.packageName.startsWith(CamapsParser.PACKAGE_PREFIX) }
        } catch (e: SecurityException) {
            SteadmoLog.failure(e, "listener: couldn't read active notifications on a ranking update")
            null
        } ?: return
        val importance = channelImportance(camaps, rankingMap) ?: return
        repository.onCamapsChannel(camaps.notification.channelId, importance)
    }

    /** How the user has set the posting channel, so Steadmo can suggest minimizing it. */
    private fun channelImportance(sbn: StatusBarNotification, rankings: RankingMap? = currentRanking): Int? {
        val ranking = Ranking()
        return if (rankings?.getRanking(sbn.key, ranking) == true) ranking.channel?.importance else null
    }

    private fun handle(sbn: StatusBarNotification) {
        DeliveryApp.of(sbn.packageName)?.let { app ->
            try {
                val texts = NotificationDump.of(this, sbn).extrasTexts
                DeliveryPrompts.onNotification(this, app, texts, sbn.postTime, System.currentTimeMillis(), StatusNotifier(this))
            } catch (e: RuntimeException) {
                // A delivery app's post is only a hint; failing to read one costs a suggestion, nothing more.
                SteadmoLog.failure(e, "listener: couldn't read a %s notification", app.label)
            }
            return
        }
        if (!sbn.packageName.startsWith(CamapsParser.PACKAGE_PREFIX)) return
        try {
            val dump = NotificationDump.of(this, sbn, channelImportance(sbn))
            val status = CamapsParser.parse(dump.viewTexts) ?: CamapsParser.parse(dump.extrasTexts)
            if (status == null) {
                SteadmoLog.event(
                    "listener: CamAPS post id %s channel %s not understood: views %s extras %s",
                    sbn.id, dump.channelId, dump.viewTexts, dump.extrasTexts,
                )
            } else {
                SteadmoLog.event(
                    "listener: CamAPS post id %s channel %s importance %s: %s %s%s, auto mode %s",
                    sbn.id, dump.channelId, dump.channelImportance, status.bound?.symbol ?: "", status.mmol, status.unit.label,
                    status.autoMode,
                )
            }
            repository.onCamapsNotification(dump, status)
        } catch (e: RuntimeException) {
            // One unreadable post must not stop the next; the app shows the source as stale.
            SteadmoLog.failure(e, "listener: couldn't read CamAPS FX notification id %s", sbn.id)
        }
    }
}
