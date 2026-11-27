package app.steadmo

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.steadmo.core.CamapsParser

/** Reads CamAPS FX's notifications as the system posts them (SPEC.md §3). */
class CamapsListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.i(SteadmoApp.TAG, "notification listener connected")
        repository.setListenerConnected(true)
        try {
            activeNotifications?.forEach(::handle)
        } catch (e: SecurityException) {
            Log.w(SteadmoApp.TAG, "Couldn't read the active notifications on connect", e)
        }
    }

    override fun onListenerDisconnected() {
        Log.w(SteadmoApp.TAG, "notification listener disconnected; asking to rebind")
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
            repository.onCamapsNotification(dump, status)
        } catch (e: RuntimeException) {
            // One unreadable post must not stop the next; the app shows the source as stale.
            Log.e(SteadmoApp.TAG, "Couldn't read a CamAPS FX notification (id ${sbn.id})", e)
        }
    }
}
