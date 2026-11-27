package app.steadmo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import app.steadmo.core.SteadmoLog

/**
 * Which CamAPS FX screens other apps may open (exported activities) and
 * which intents it answers, for the bug report. The meal and Boost buttons
 * can only open the right screen if CamAPS FX exposes one; this shows whether
 * it does, from the user's own installed version.
 */
object CamapsEntryPoints {
    /** Intents worth probing: the ones an app's screens commonly answer. */
    private val probes = listOf(
        Intent.ACTION_MAIN,
        Intent.ACTION_VIEW,
        Intent.ACTION_SEND,
        Intent.ACTION_INSERT,
        Intent.ACTION_EDIT,
        Intent.ACTION_CREATE_SHORTCUT,
    )

    fun describe(context: Context, pkg: String?): String {
        if (pkg == null) return "CamAPS FX not seen"
        val pm = context.packageManager
        return try {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
            buildString {
                appendLine("version: ${info.versionName} (${info.longVersionCode})")
                val activities = info.activities.orEmpty()
                appendLine("activities: ${activities.size}, exported: ${activities.count { it.exported }}")
                activities.filter { it.exported }.forEach { appendLine("exported: ${it.name}") }
                probes.forEach { action ->
                    val found = pm.queryIntentActivities(Intent(action).setPackage(pkg), 0)
                        .map { it.activityInfo.name }
                    if (found.isNotEmpty()) appendLine("answers $action: ${found.joinToString(", ")}")
                }
            }.trimEnd()
        } catch (e: PackageManager.NameNotFoundException) {
            SteadmoLog.failure(e, "report: CamAPS FX package %s not visible", pkg)
            "$pkg not installed or not visible"
        }
    }
}
