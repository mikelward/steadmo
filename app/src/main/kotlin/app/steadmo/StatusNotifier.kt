package app.steadmo

import app.steadmo.core.SteadmoLog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import app.steadmo.core.rate
import app.steadmo.core.reading
import app.steadmo.core.value
import app.steadmo.core.HIGH_MMOL
import app.steadmo.core.LOW_MMOL
import app.steadmo.core.RangeSide
import app.steadmo.core.Reading
import app.steadmo.core.outOfRangeSince
import app.steadmo.core.side
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

    /**
     * Shows the latest reading. With [promote] (out of range, or a low on the
     * way) it becomes an Android 16 Live Update: a status bar chip with the
     * value, and a bar placing it against the range (SPEC.md §4.3).
     */
    fun showStatus(state: GlucoseState, promote: Boolean = false) {
        val latest = state.latest ?: return
        // The notification lives only as long as its reading is fresh, however
        // late it was posted (a reconnect replay, a mode-only update).
        val remaining = STALE_MILLIS - (clock() - latest.timeMillis)
        if (remaining <= 0) {
            SteadmoLog.event("notify: reading %s ms old; status cleared", clock() - latest.timeMillis)
            manager.cancel(ID_STATUS)
            return
        }
        val unit = state.unit
        val arrow = state.trend?.arrow
        val rate = state.trend?.mmolPerMinute
        val detail = listOfNotNull(
            arrow?.label,
            rate?.let { "${unit.rate(it)}/min" },
            state.status?.autoMode?.let { "Auto mode ${it.lowercase()}" },
        ).joinToString(" · ")
        val color = rangeColor(latest.mmol).toArgb()
        val side = latest.side()
        val shown = "${unit.reading(latest)} ${unit.label}"
        val title = when (side) {
            RangeSide.LOW -> "Low: $shown"
            RangeSide.HIGH -> "High: $shown"
            RangeSide.IN_RANGE -> shown
        }
        val since = outOfRangeSince(state.readings)?.let { start ->
            val minutes = (latest.timeMillis - start) / 60_000
            val edge = if (side == RangeSide.LOW) "Below ${unit.value(LOW_MMOL)}" else "Above ${unit.value(HIGH_MMOL)}"
            if (minutes > 0) "$edge for $minutes min" else edge
        }
        val text = listOfNotNull(detail.ifEmpty { null }, since).joinToString(". ")
        val builder = Notification.Builder(context, CHANNEL_GLUCOSE)
            .setSmallIcon(arrow.drawable())
            .setLargeIcon(arrowBitmap(arrow, color))
            .setColor(color)
            .setContentTitle(title)
            .setContentText(text)
            .setWhen(latest.timeMillis)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            // A reading this old is no use; better gone than wrong.
            .setTimeoutAfter(remaining)
            .setContentIntent(openApp())
        val promoted = promote && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
        if (promoted) promote(builder, unit.reading(latest), latest)
        SteadmoLog.verbose("notify: status %s, arrow %s, live update %s (asked %s)", latest.display, arrow, promoted, promote)
        post(ID_STATUS, builder.build())
    }

    /** A Live Update: chip text, promotion request, and the range bar. */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun promote(builder: Notification.Builder, chip: String, latest: Reading) {
        val low = LowColor.toArgb()
        val inRange = InRangeColor.toArgb()
        val high = HighColor.toArgb()
        builder
            .setShortCriticalText(chip)
            .setStyle(
                Notification.ProgressStyle()
                    .setStyledByProgress(false)
                    .setProgressSegments(
                        listOf(
                            Notification.ProgressStyle.Segment(scale(LOW_MMOL)).setColor(low),
                            Notification.ProgressStyle.Segment(scale(HIGH_MMOL) - scale(LOW_MMOL)).setColor(inRange),
                            Notification.ProgressStyle.Segment(SCALE_MAX - scale(HIGH_MMOL)).setColor(high),
                        ),
                    )
                    .setProgress(scale(latest.mmol)),
            )
            .extras.putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
    }

    /**
     * Shows [alert] until its reading, taken at [readingTime], is stale. [quiet]
     * updates one already showing (e.g. in a new unit) without sounding again.
     */
    fun showAlert(alert: TrendAlert, readingTime: Long, quiet: Boolean = false) {
        val remaining = STALE_MILLIS - (clock() - readingTime)
        if (remaining <= 0) {
            SteadmoLog.event("notify: alert %s for a stale reading not shown", alert.kind)
            return
        }
        val color = (if (alert.arrow.name.contains("DOWN")) LowColor else HighColor).toArgb()
        val notification = Notification.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(alert.arrow.drawable())
            .setLargeIcon(arrowBitmap(alert.arrow, color))
            .setColor(color)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOnlyAlertOnce(quiet)
            .setTimeoutAfter(remaining)
            .setContentIntent(openApp())
            .build()
        post(ID_ALERT, notification)
    }

    /** Whether a trend alert is still on screen (not dismissed, tapped or timed out). */
    fun alertShowing(): Boolean = manager.activeNotifications.any { it.id == ID_ALERT }

    fun cancelAlert() {
        SteadmoLog.event("notify: alert cancelled")
        manager.cancel(ID_ALERT)
    }

    private fun post(id: Int, notification: Notification) {
        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            SteadmoLog.warning("notify: notifications aren't allowed; not posting %s", id)
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

        /** The Live Update bar runs from 2 to 14 mmol/L in tenths. */
        const val SCALE_MAX = 120
        fun scale(mmol: Double): Int = ((mmol - 2.0) * 10).toInt().coerceIn(0, SCALE_MAX)
    }
}
