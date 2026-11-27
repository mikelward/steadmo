package app.steadmo

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import app.steadmo.core.AlertPolicy
import app.steadmo.core.SteadmoLog
import com.mikelward.androidlog.DebugLog
import com.mikelward.androidlog.android.DebugReport
import com.mikelward.androidlog.android.ReportScreenshot
import com.mikelward.androidlog.android.ShareOutcome
import java.io.File

/**
 * "Report a bug": the app's state, the last CamAPS FX notification as read,
 * and the debug log (this run and earlier ones), with a screenshot, sent
 * through the share sheet (SPEC.md §6). Nothing is sent without the user
 * picking where it goes; the report is also copied to the clipboard.
 */
object BugReport {
    /** What the report says about the app, before the log lines. */
    data class Facts(
        val version: String,
        val device: String,
        val sdk: Int,
        val listenerAllowed: Boolean,
        val notificationsAllowed: Boolean,
        val state: GlucoseState,
        val now: Long,
        /** What CamAPS FX lets other apps open ([CamapsEntryPoints]). */
        val camapsEntryPoints: String = "",
        /** When the disclaimer was accepted, or null. */
        val disclaimerAcceptedAt: Long? = null,
        val shortcutsOn: Boolean = false,
    )

    fun facts(context: Context, state: GlucoseState, now: Long = System.currentTimeMillis()) = Facts(
        version = BuildConfig.VERSION_NAME,
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
        sdk = Build.VERSION.SDK_INT,
        listenerAllowed = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context),
        notificationsAllowed =
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        state = state,
        now = now,
        camapsEntryPoints = CamapsEntryPoints.describe(context, state.camapsPackage),
        disclaimerAcceptedAt = Disclaimer.acceptedAt(context),
        shortcutsOn = CamapsShortcuts.enabled(context),
    )

    /** The app's section of the report: facts, then the newest log lines that fit. */
    fun section(facts: Facts, logLines: List<String>): String = buildString {
        val s = facts.state
        appendLine("Steadmo ${facts.version} on ${facts.device}, Android SDK ${facts.sdk}")
        appendLine("Disclaimer: version ${Disclaimer.VERSION}, ${facts.disclaimerAcceptedAt?.let { "accepted at $it" } ?: "not accepted"}")
        appendLine("Notification access: ${yesNo(facts.listenerAllowed)}; notifications: ${yesNo(facts.notificationsAllowed)}; listener connected: ${yesNo(s.listenerConnected)}; CamAPS FX shortcuts: ${yesNo(facts.shortcutsOn)}")
        appendLine("CamAPS FX: ${s.camapsPackage ?: "not seen"}; channel ${s.camapsChannelId ?: "unknown"}, importance ${importance(s.camapsChannelImportance)}")
        val latest = s.latest
        if (latest == null) {
            appendLine("Readings: none")
        } else {
            val age = (facts.now - latest.timeMillis) / 1000
            appendLine("Readings: ${s.readings.size}; latest ${latest.display} mmol/L, ${age}s old")
        }
        appendLine("Units: ${s.unit.label}")
        appendLine("Trend: ${s.trend?.arrow ?: "none"}${s.trend?.let { ", ${AlertPolicy.fmt2(it.mmolPerMinute)} mmol/L/min" } ?: ""}")
        appendLine("Auto mode: ${s.status?.autoMode ?: "unknown"}")
        s.lastDump?.let {
            appendLine()
            appendLine("--- last CamAPS FX notification ---")
            appendLine(it.describe())
        }
        if (facts.camapsEntryPoints.isNotEmpty()) {
            appendLine()
            appendLine("--- CamAPS FX screens other apps can open ---")
            appendLine(facts.camapsEntryPoints)
        }
        appendLine()
        appendLine("--- log ---")
        logLines.forEach { appendLine(it) }
    }.trimEnd()

    private fun yesNo(b: Boolean) = if (b) "yes" else "no"

    private fun importance(value: Int?) = when (value) {
        null -> "unknown"
        NotificationManager.IMPORTANCE_NONE -> "off"
        NotificationManager.IMPORTANCE_MIN -> "minimized"
        NotificationManager.IMPORTANCE_LOW -> "silent"
        NotificationManager.IMPORTANCE_DEFAULT -> "default"
        NotificationManager.IMPORTANCE_HIGH -> "high"
        else -> value.toString()
    }

    /**
     * Collects and shares the report. Blocking: call off the main thread. The
     * screenshot is of [activity]'s window; a failed capture sends text only.
     */
    fun share(activity: Activity, log: DebugLog = SteadmoLog): ShareOutcome {
        val context = activity.applicationContext
        SteadmoLog.event("report: started")
        val screenshot = ReportScreenshot.capture(activity, File(context.cacheDir, "bug-reports"), log)
            ?.let { screenshotUri(it, log) { file -> FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) } }
        val app = context as? SteadmoApp
        val facts = facts(context, context.repository.state.value)
        val report = DebugReport.collect(log, app?.diagnosticSink) {
            val rest = section(facts, emptyList())
            val budget = maxOf(DebugReport.APP_SECTION_CHARS - rest.length, 0)
            section(facts, DebugReport.logLines(log, budget))
        }
        val outcome = DebugReport.deliver(
            context = context,
            log = log,
            report = report,
            subject = context.getString(R.string.bug_report_subject),
            chooserTitle = context.getString(R.string.bug_report_chooser_title),
            clipboardLabel = context.getString(R.string.bug_report_clipboard_label),
            screenshot = screenshot,
        )
        SteadmoLog.event("report: %s", outcome)
        activity.runOnUiThread {
            when (outcome) {
                ShareOutcome.SHARED -> Unit
                ShareOutcome.COPIED_ONLY -> Toast.makeText(context, R.string.bug_report_copied, Toast.LENGTH_LONG).show()
                ShareOutcome.FAILED -> Toast.makeText(context, R.string.bug_report_failed, Toast.LENGTH_LONG).show()
            }
        }
        return outcome
    }

    /** A content:// URI for the screenshot, or null (text only) if the provider refuses. */
    internal fun screenshotUri(file: File, log: DebugLog, mint: (File) -> Uri): Uri? = try {
        mint(file)
    } catch (e: Exception) {
        log.failure(e, "report: couldn't share the screenshot; sending text only")
        file.delete()
        null
    }
}
