package app.steadmo

import app.steadmo.core.CamapsGoal
import app.steadmo.core.SteadmoLog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/**
 * Opens CamAPS FX. Steadmo never doses: every bolus, Boost and Ease-off is set
 * and confirmed in CamAPS FX (SPEC.md §2, D2).
 */
object Camaps {
    private val packages = listOf(
        "com.camdiab.fx_alert.mmoll",
        "com.camdiab.fx_alert.mgdl",
        "com.camdiab.fx_alert.hx.mmoll",
        "com.camdiab.fx_alert.hx.mgdl",
    )

    fun open(context: Context, seenPackage: String?): Boolean {
        val candidates = listOfNotNull(seenPackage) + packages
        val intent = candidates.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
        if (intent == null) {
            SteadmoLog.warning("camaps: not installed, or not visible to Steadmo")
            Toast.makeText(context, "CamAPS FX isn't installed", Toast.LENGTH_SHORT).show()
            return false
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SteadmoLog.event("camaps: opened %s", intent.`package`)
        return true
    }

    /**
     * Opens the settings for CamAPS FX's notification channel [channelId], or
     * its app notification settings if the channel page won't open.
     */
    fun openChannelSettings(context: Context, pkg: String?, channelId: String?) {
        val target = pkg ?: packages.first()
        SteadmoLog.event("camaps: opening channel settings for %s channel %s", target, channelId)
        val channel = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, target)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(channel)
        } catch (e: ActivityNotFoundException) {
            SteadmoLog.failure(e, "camaps: no channel settings page; opening app notification settings")
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, target)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Copies [grams] so it can be pasted into CamAPS FX's meal entry, then opens
     * it. With CamAPS FX shortcuts on, Steadmo also types them in (SPEC.md §4.6); the
     * copy stays as the fallback.
     */
    fun openForMeal(context: Context, seenPackage: String?, grams: Int) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Carbs", grams.toString()))
        SteadmoLog.event("camaps: meal %s g copied", grams)
        val shortcuts = CamapsShortcuts.enabled(context)
        val started = shortcuts && CamapsShortcuts.begin(context, CamapsGoal.Meal(grams))
        if (open(context, seenPackage)) {
            val text = when {
                started -> "Entering $grams g in CamAPS FX…"
                shortcuts -> "A meal was entered under a minute ago, so Steadmo won't enter this one. $grams g is copied."
                else -> "$grams g copied. Enter it in CamAPS FX."
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        } else {
            CamapsShortcuts.clear()
        }
    }

    /**
     * Opens CamAPS FX and, with shortcuts on, its [mode] (Boost or Ease-off)
     * dialog, for the user to set and confirm there (SPEC.md §4.6).
     */
    fun openForMode(context: Context, seenPackage: String?, mode: CamapsGoal.Mode) {
        val shortcuts = CamapsShortcuts.enabled(context)
        val started = shortcuts && CamapsShortcuts.begin(context, mode)
        if (!open(context, seenPackage)) {
            CamapsShortcuts.clear()
        } else if (shortcuts && !started) {
            Toast.makeText(context, "${mode.name} was opened under a minute ago. Opening CamAPS FX only.", Toast.LENGTH_LONG).show()
        }
    }
}
