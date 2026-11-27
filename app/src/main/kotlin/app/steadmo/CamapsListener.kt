package app.steadmo

import app.steadmo.core.SteadmoLog
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.steadmo.core.CamapsParser

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

    /** How the user has set the posting channel, so Steadmo can suggest minimizing it. */
    private fun channelImportance(sbn: StatusBarNotification): Int? {
        val ranking = Ranking()
        return if (currentRanking?.getRanking(sbn.key, ranking) == true) ranking.channel?.importance else null
    }

    private fun handle(sbn: StatusBarNotification) {
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
