package app.steadmo

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import app.steadmo.core.SteadmoLog

/**
 * Checks and recovery hooks that keep Steadmo's listener, alarms and alerts
 * running reliably across Doze, reboots, updates and long stretches without
 * opening the app (SPEC.md §4.17).
 */
object Reliability {
    /** Whether Steadmo is exempt from battery optimizations (no manifest permission needed). */
    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Whether the alarm volume stream used by urgent low alarms is muted. */
    fun alarmVolumeMuted(context: Context): Boolean =
        context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM) == 0

    /** Whether Android's "Pause app activity if unused" / permission auto-revoke is off for Steadmo. */
    fun unusedAppRestrictionsDisabled(context: Context): Boolean =
        context.packageManager.isAutoRevokeWhitelisted

    /** Asks the system to bind [CamapsListener] again when access is granted but it is disconnected. */
    fun requestListenerRebind(context: Context) {
        try {
            NotificationListenerService.requestRebind(ComponentName(context, CamapsListener::class.java))
        } catch (e: RuntimeException) {
            SteadmoLog.failure(e, "reliability: couldn't request notification listener rebind")
        }
    }

    /** Re-arms all scheduled alarms and calendar watches after a reboot, update, or permission change. */
    fun restoreAlarms(context: Context) {
        MealReminders.schedule(context)
        MealReminders.restoreSnooze(context)
        CalendarMealReminders.scan(context)
        CalendarMealReminders.watch(context)
        WeeklyReports.schedule(context)
        Recheck.restore(context, context.repository.state.value.treatments)
        SignalWatch.run(context)
    }

    /** Restores alarms and nudges the notification listener if access is granted but not yet bound. */
    fun restoreAfterBootOrUpdate(context: Context) {
        restoreAlarms(context)
        val access = context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(context, CamapsListener::class.java))
        if (access && !context.repository.state.value.listenerConnected) {
            SteadmoLog.event("reliability: notification access granted but listener not connected; requesting rebind")
            requestListenerRebind(context)
        }
    }
}

/**
 * Re-arms alarms and nudges [CamapsListener] after a phone reboot or app update,
 * so Steadmo doesn't wait for the user to open it (SPEC.md §4.17).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            SteadmoLog.warning("reliability: ignoring unexpected %s", action)
            return
        }
        SteadmoLog.event("reliability: %s; restoring alarms and listener", action)
        Reliability.restoreAfterBootOrUpdate(context)
    }
}
