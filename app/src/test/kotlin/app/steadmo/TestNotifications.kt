package app.steadmo

import android.app.Application
import android.app.NotificationManager
import org.robolectric.Shadows.shadowOf

fun Application.allowNotifications() = shadowOf(this).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

fun Application.posted(id: Int) =
    shadowOf(getSystemService(NotificationManager::class.java)).getNotification(id)
