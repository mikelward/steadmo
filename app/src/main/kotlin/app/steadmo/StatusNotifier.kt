package app.steadmo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import app.steadmo.core.AlertPolicy
import app.steadmo.core.TrendAlert
import app.steadmo.core.TrendArrow

/**
 * Steadmo's own notifications. The status bar icon is the trend arrow, so the
 * direction shows without pulling the shade down.
 */
class StatusNotifier(private val context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_GLUCOSE, context.getString(R.string.channel_glucose), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_glucose_description); setShowBadge(false) },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.channel_alerts_description) },
        )
    }

    fun showStatus(state: GlucoseState) {
        val latest = state.latest ?: return
        // The notification lives only as long as its reading is fresh, however
        // late it was posted (a reconnect replay, a mode-only update).
        val remaining = STALE_MILLIS - (clock() - latest.timeMillis)
        if (remaining <= 0) {
            manager.cancel(ID_STATUS)
            return
        }
        val arrow = state.trend?.arrow
        val rate = state.trend?.mmolPerMinute
        val detail = listOfNotNull(
            arrow?.label,
            rate?.let { "${if (it >= 0) "+" else "−"}${AlertPolicy.fmt2(kotlin.math.abs(it))}/min" },
            state.status?.autoMode?.let { "Auto mode ${it.lowercase()}" },
        ).joinToString(" · ")
        val color = rangeColor(latest.mmol).toArgb()
        val notification = Notification.Builder(context, CHANNEL_GLUCOSE)
            .setSmallIcon(arrow.drawable())
            .setLargeIcon(arrowBitmap(arrow, color))
            .setColor(color)
            .setContentTitle("${latest.display} mmol/L")
            .setContentText(detail)
            .setWhen(latest.timeMillis)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            // A reading this old is no use; better gone than wrong.
            .setTimeoutAfter(remaining)
            .setContentIntent(openApp())
            .build()
        post(ID_STATUS, notification)
    }

    fun showAlert(alert: TrendAlert) {
        val color = (if (alert.arrow.name.contains("DOWN")) LowColor else HighColor).toArgb()
        val notification = Notification.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(alert.arrow.drawable())
            .setLargeIcon(arrowBitmap(alert.arrow, color))
            .setColor(color)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setTimeoutAfter(STALE_MILLIS)
            .setContentIntent(openApp())
            .build()
        post(ID_ALERT, notification)
    }

    private fun post(id: Int, notification: Notification) {
        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.i(SteadmoApp.TAG, "Notifications aren't allowed; not posting $id")
            return
        }
        manager.notify(id, notification)
    }

    private fun arrowBitmap(arrow: TrendArrow?, color: Int): Bitmap {
        val drawable = context.getDrawable(arrow.drawable())!!.mutate()
        drawable.setTint(color)
        return drawable.toBitmap(ICON_PX, ICON_PX)
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_GLUCOSE = "glucose"
        const val CHANNEL_ALERTS = "trend_alerts"
        private const val ID_STATUS = 1
        private const val ID_ALERT = 2
        private const val ICON_PX = 128
        const val STALE_MILLIS = 15 * 60_000L
    }
}
