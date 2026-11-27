package app.steadmo

import android.app.Notification
import android.content.Context
import android.os.Process
import android.service.notification.StatusBarNotification
import android.widget.RemoteViews

const val CAMAPS = "com.camdiab.fx_alert.mmoll"
const val MINUTE = 60_000L

/** A notification shaped like CamAPS FX's "My diabetes info", posted by [pkg] at [postTime]. */
fun camapsPost(
    context: Context,
    value: String?,
    postTime: Long,
    mode: String = "On",
    pkg: String = CAMAPS,
    id: Int = 1,
): StatusBarNotification {
    val views = RemoteViews(context.packageName, R.layout.test_camaps_notification).apply {
        setTextViewText(R.id.mode, mode)
        setTextViewText(R.id.value, value ?: "")
    }
    val notification = Notification.Builder(context, "test")
        .setSmallIcon(R.drawable.ic_trend_flat)
        .setCustomContentView(views)
        .build()
    @Suppress("DEPRECATION")
    notification.contentView = views
    return StatusBarNotification(pkg, pkg, id, null, 0, 0, 0, notification, Process.myUserHandle(), postTime)
}
