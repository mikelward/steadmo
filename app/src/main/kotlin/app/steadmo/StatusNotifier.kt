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
import app.steadmo.core.isLow
import app.steadmo.core.AlertKind
import app.steadmo.core.GlucoseUnit
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
            // An alarm, so Do Not Disturb lets it through wherever it lets alarms through.
            NotificationChannel(CHANNEL_URGENT, context.getString(R.string.channel_urgent), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = context.getString(R.string.channel_urgent_description)
                    setSound(
                        android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    enableVibration(true)
                    setBypassDnd(true)
                },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_reminders_description) },
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
            .apply { if (alert.kind.isLow) addAction(treatedAction()) }
            .addExtras(android.os.Bundle().apply { putString(EXTRA_KIND, alert.kind.name) })
            .build()
        post(ID_ALERT, notification)
    }

    /**
     * The urgent low alarm (SPEC.md §4.8): sounds until the user taps OK or
     * glucose comes back above 3.0, and lasts only while [latest] is fresh.
     */
    fun showUrgentLow(latest: Reading, unit: GlucoseUnit, quiet: Boolean = false, fullScreen: Boolean = false) {
        val remaining = STALE_MILLIS - (clock() - latest.timeMillis)
        if (remaining <= 0) return
        val ok = PendingIntent.getBroadcast(
            context, 0, Intent(context, UrgentLowAckReceiver::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        // Android also sends the delete intent when the alarm times out with its reading;
        // the expiry lets the receiver tell that from a swipe (SPEC.md §4.8).
        val dismissed = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, UrgentLowAckReceiver::class.java).setAction(UrgentLowAckReceiver.ACTION_DISMISSED)
                .putExtra(UrgentLowAckReceiver.EXTRA_EXPIRES, latest.timeMillis + STALE_MILLIS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL_URGENT)
            .setSmallIcon(R.drawable.ic_trend_double_down)
            .setColor(LowColor.toArgb())
            .setContentTitle("Urgent low: ${unit.reading(latest)} ${unit.label}")
            .setContentText("Treat it now. This repeats until you tap OK.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setTimeoutAfter(remaining)
            .setContentIntent(openApp())
            .setDeleteIntent(dismissed)
            .addAction(Notification.Action.Builder(null, "OK", ok).build())
            .addAction(treatedAction())
            .setOnlyAlertOnce(quiet)
            .apply {
                // Ignored for a while: take over the screen, even when locked (SPEC.md §4.8).
                if (fullScreen) {
                    setFullScreenIntent(
                        PendingIntent.getActivity(
                            context, 3, Intent(context, UrgentLowActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                            PendingIntent.FLAG_IMMUTABLE,
                        ),
                        true,
                    )
                }
            }
            .build()
        // Sounds over and over until it's acknowledged or cleared.
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        SteadmoLog.warning("notify: urgent low %s%s", unit.reading(latest), if (fullScreen) ", full screen" else "")
        post(ID_URGENT, notification)
    }

    fun cancelUrgentLow() = manager.cancel(ID_URGENT)

    /** The daily meal reminder: Bolus, or remind again in 30 or 60 minutes (SPEC.md §4.13). */
    fun showMealReminder(event: String? = null) {
        val bolus = PendingIntent.getActivity(
            context, 5,
            Intent(context, app.steadmo.widget.WidgetActionActivity::class.java)
                .setData(android.net.Uri.parse("${app.steadmo.widget.WidgetActionActivity.SCHEME}://widget/camaps/meal")),
            PendingIntent.FLAG_IMMUTABLE,
        )
        fun later(minutes: Int) = Notification.Action.Builder(
            null, "In $minutes min",
            PendingIntent.getBroadcast(
                context, 10 + minutes,
                Intent(context, MealReminderReceiver::class.java).putExtra(MealReminders.EXTRA_SNOOZE, minutes)
                    .putExtra(MealReminders.EXTRA_EVENT, event),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).build()
        val notification = Notification.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_shortcut_meal)
            .setContentTitle(if (event == null) "Time to bolus for dinner?" else "$event. Bolus before you eat?")
            .apply { if (event != null) extras.putBoolean(EXTRA_CALENDAR, true) }
            .setContentText("Bolus before you eat, so insulin is working by the time you do.")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(bolus)
            .addAction(Notification.Action.Builder(null, "Bolus", bolus).build())
            .apply { app.steadmo.core.MealReminder.SNOOZES_MINUTES.forEach { addAction(later(it)) } }
            .build()
        post(ID_MEAL_REMINDER, notification)
    }

    fun cancelMealReminder() = manager.cancel(ID_MEAL_REMINDER)

    /** Clears the meal reminder if it's a calendar meal's ([calendar]) or the daily one's, leaving the other. */
    fun cancelMealReminder(calendar: Boolean) {
        val shown = manager.activeNotifications.firstOrNull { it.id == ID_MEAL_REMINDER } ?: return
        if (shown.notification.extras.getBoolean(EXTRA_CALENDAR) == calendar) manager.cancel(ID_MEAL_REMINDER)
    }

    /** A delivery app says the rider is nearby: bolus now, so insulin is working by the time it's here. */
    fun showPreBolus(appLabel: String) {
        val bolus = PendingIntent.getActivity(
            context, 4,
            Intent(context, app.steadmo.widget.WidgetActionActivity::class.java)
                .setData(android.net.Uri.parse("${app.steadmo.widget.WidgetActionActivity.SCHEME}://widget/camaps/meal"))
                .putExtra(app.steadmo.widget.WidgetActionActivity.EXTRA_CANCEL_PRE_BOLUS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_shortcut_meal)
            .setContentTitle("Food nearly here")
            .setContentText("$appLabel says your rider is nearby. Bolus now?")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(bolus)
            .addAction(Notification.Action.Builder(null, "Bolus", bolus).build())
            .setTimeoutAfter(PRE_BOLUS_TIMEOUT_MILLIS)
            .build()
        post(ID_PRE_BOLUS, notification)
    }

    fun cancelPreBolus() = manager.cancel(ID_PRE_BOLUS)

    private fun treatedAction(): Notification.Action = Notification.Action.Builder(
        null, "Treated",
        PendingIntent.getBroadcast(context, 2, Intent(context, TreatedReceiver::class.java), PendingIntent.FLAG_IMMUTABLE),
    ).build()

    /**
     * The kind of trend alert on screen, from the notification itself, so it
     * survives the process being restarted.
     */
    fun shownAlertKind(): AlertKind? = manager.activeNotifications.firstOrNull { it.id == ID_ALERT }
        ?.notification?.extras?.getString(EXTRA_KIND)?.let { name -> AlertKind.entries.firstOrNull { it.name == name } }

    /**
     * 15 minutes after "Treated": check glucose is coming back up. The latest
     * reading is given with its time, never as "now", and only if it is fresh.
     */
    fun showRecheck(latest: Reading?, unit: GlucoseUnit) {
        val age = latest?.let { clock() - it.timeMillis }
        val at = latest?.let { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it.timeMillis)) }
        val now = when {
            latest == null -> "No reading yet."
            age!! < STALE_MILLIS -> "Latest ${unit.reading(latest)} ${unit.label} at $at."
            else -> "No glucose from CamAPS FX since $at."
        }
        val notification = Notification.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon((null as TrendArrow?).drawable())
            .setColor(LowColor.toArgb())
            .setContentTitle("Recheck your glucose")
            .setContentText("You treated a low 15 min ago. $now")
            .setCategory(Notification.CATEGORY_REMINDER)
            .apply { if (latest != null) setWhen(latest.timeMillis).setShowWhen(true) }
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        post(ID_RECHECK, notification)
    }

    /** Whether the urgent low alarm is still on screen (not acknowledged or timed out). */
    fun urgentLowShowing(): Boolean = manager.activeNotifications.any { it.id == ID_URGENT }

    /** Whether a trend alert is still on screen (not dismissed, tapped or timed out). */
    fun alertShowing(): Boolean = manager.activeNotifications.any { it.id == ID_ALERT }

    /**
     * Nothing from CamAPS FX for [quietMinutes] since [since]: sensor signal,
     * Bluetooth or CamAPS FX may be down, or Steadmo has lost [access] to its
     * notification. Stays until the next reading clears it.
     */
    fun showSignalLoss(quietMinutes: Long, since: Long, access: Boolean) {
        val at = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(since))
        val text = if (access) {
            "Nothing from CamAPS FX since $at. The sensor, Bluetooth or CamAPS FX may be down: check CamAPS FX."
        } else {
            "Steadmo can't read CamAPS FX: notification access is off. Open Steadmo to turn it back on."
        }
        val notification = Notification.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon((null as TrendArrow?).drawable())
            .setContentTitle("No glucose for ${duration(quietMinutes)}")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setColor(HighColor.toArgb())
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        post(ID_SIGNAL, notification)
    }

    fun cancelSignalLoss() = manager.cancel(ID_SIGNAL)

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
        private const val EXTRA_CALENDAR = "app.steadmo.calendar_meal"
        const val CHANNEL_ALERTS = "trend_alerts"
        private const val ID_STATUS = 1
        private const val ID_ALERT = 2
        private const val ID_SIGNAL = 3
        private const val ID_URGENT = 4
        private const val ID_RECHECK = 5
        private const val ID_PRE_BOLUS = 6
        private const val ID_MEAL_REMINDER = 7
        const val CHANNEL_REMINDERS = "reminders"

        /** A pre-bolus suggestion is no use once the food has long arrived. */
        const val PRE_BOLUS_TIMEOUT_MILLIS = 45 * 60_000L
        private const val EXTRA_KIND = "app.steadmo.alert_kind"
        const val CHANNEL_URGENT = "urgent_low"

        /** e.g. "15 min", "1 h 15 min". */
        fun duration(minutes: Long): String =
            if (minutes < 60) "$minutes min" else "${minutes / 60} h" + (minutes % 60).let { if (it > 0) " $it min" else "" }
        private const val ICON_PX = 128
        const val STALE_MILLIS = 15 * 60_000L

        /**
         * Whether the user has turned Steadmo's alerts channel below Default
         * (no sound) or off, so alerts, signal loss included, would go unheard.
         */
        fun alertsSilenced(context: Context): Boolean {
            val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ALERTS)
            return channel != null && channel.importance < NotificationManager.IMPORTANCE_DEFAULT
        }

        /** The Live Update bar runs from 2 to 14 mmol/L in tenths. */
        const val SCALE_MAX = 120
        fun scale(mmol: Double): Int = ((mmol - 2.0) * 10).toInt().coerceIn(0, SCALE_MAX)
    }
}
