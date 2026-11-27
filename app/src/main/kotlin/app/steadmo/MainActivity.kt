package app.steadmo

import android.Manifest
import app.steadmo.core.SteadmoLog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private var listenerAllowed by mutableStateOf(false)
    private var notificationsAllowed by mutableStateOf(false)
    private var alertsSilenced by mutableStateOf(false)
    private var suggestShortcuts by mutableStateOf(false)
    private var shortcutsOn by mutableStateOf(false)
    private var disclaimerAccepted by mutableStateOf(false)
    private var showingDisclaimer by mutableStateOf(false)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        disclaimerAccepted = Disclaimer.accepted(this)
        setContent {
            SteadmoTheme {
                if (!disclaimerAccepted) {
                    DisclaimerScreen(
                        onAccept = {
                            Disclaimer.accept(this)
                            disclaimerAccepted = true
                            (application as? SteadmoApp)?.refreshWidgets()
                        },
                        onLeave = {
                            SteadmoLog.event("ui: disclaimer declined")
                            finish()
                        },
                    )
                    return@SteadmoTheme
                }
                if (showingDisclaimer) {
                    DisclaimerScreen(onAccept = null, onLeave = { showingDisclaimer = false })
                    return@SteadmoTheme
                }
                val state by repository.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    listenerAllowed = listenerAllowed,
                    notificationsAllowed = notificationsAllowed,
                    onAllowListener = ::openListenerSettings,
                    onAllowNotifications = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onOpenCamaps = { Camaps.open(this, state.camapsPackage) },
                    onMeal = { grams -> Camaps.openForMeal(this, state.camapsPackage, grams) },
                    onMode = { mode -> Camaps.openForMode(this, state.camapsPackage, mode) },
                    onOtherMeal = { Camaps.openForMealEntry(this, state.camapsPackage) },
                    suggestShortcuts = suggestShortcuts,
                    onDismissShortcuts = ::dismissShortcutsSuggestion,
                    version = BuildConfig.VERSION_NAME,
                    onSourceCode = ::openSourceCode,
                    onReportBug = { Thread { BugReport.share(this) }.start() },
                    onShowDisclaimer = { showingDisclaimer = true },
                    onOpenAppInfo = ::openAppInfo,
                    onGetLatest = ::openLatestRelease,
                    onKnownIssues = ::openKnownIssues,
                    shortcutsOn = shortcutsOn,
                    onOpenShortcutsSettings = ::openAccessibilitySettings,
                    onSetUnit = repository::setUnit,
                    alertsSilenced = alertsSilenced,
                    onOpenAlertSettings = ::openAlertSettings,
                    onMinimizeCamaps = { Camaps.openChannelSettings(this, state.camapsPackage, state.camapsChannelId) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        listenerAllowed = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        notificationsAllowed =
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        shortcutsOn = CamapsShortcuts.enabled(this)
        alertsSilenced = StatusNotifier.alertsSilenced(this)
        suggestShortcuts = !shortcutsOn && !getSharedPreferences(PREFS_UI, MODE_PRIVATE).getBoolean(KEY_SHORTCUTS_DISMISSED, false)
        SteadmoLog.event(
            "ui: resumed; notification access %s, notifications %s, CamAPS FX shortcuts %s, alerts silenced %s",
            listenerAllowed, notificationsAllowed, shortcutsOn, alertsSilenced,
        )
    }

    /**
     * The newest build: in debug builds, the debug APK itself, which the browser
     * downloads to install over this one; in release builds, the releases list.
     */
    private fun openLatestRelease() {
        SteadmoLog.event("ui: opening the latest release")
        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(LATEST_RELEASE_URL)))
    }

    /** "Not now" on the CamAPS FX shortcuts card: don't suggest it again (⋮ still has it). */
    private fun dismissShortcutsSuggestion() {
        SteadmoLog.event("ui: CamAPS FX shortcuts suggestion dismissed")
        getSharedPreferences(PREFS_UI, MODE_PRIVATE).edit().putBoolean(KEY_SHORTCUTS_DISMISSED, true).apply()
        suggestShortcuts = false
    }

    private fun openSourceCode() {
        SteadmoLog.event("ui: opening the source code")
        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(SOURCE_URL)))
    }

    /** Open issues on GitHub: what's known to be wrong, and whether a report is already filed. */
    private fun openKnownIssues() {
        SteadmoLog.event("ui: opening known issues")
        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(KNOWN_ISSUES_URL)))
    }

    companion object {
        const val LATEST_RELEASE_URL = BuildConfig.LATEST_DOWNLOAD_URL
        const val KNOWN_ISSUES_URL = "https://github.com/mikelward/steadmo/issues"
        const val SOURCE_URL = "https://github.com/mikelward/steadmo"
        private const val PREFS_UI = "ui"
        private const val KEY_SHORTCUTS_DISMISSED = "shortcuts_suggestion_dismissed"
    }

    /** Steadmo's alerts channel, where the user can raise its importance again. */
    private fun openAlertSettings() {
        SteadmoLog.event("ui: opening alert channel settings")
        startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, StatusNotifier.CHANNEL_ALERTS),
        )
    }

    /** Accessibility settings, where the user turns CamAPS FX shortcuts on or off. */
    private fun openAccessibilitySettings() {
        SteadmoLog.event("ui: opening accessibility settings for CamAPS FX shortcuts")
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** App info, where Android 13+ hides "Allow restricted settings" for sideloaded apps. */
    private fun openAppInfo() {
        SteadmoLog.event("ui: opening app info for restricted settings")
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null)),
        )
    }

    private fun openListenerSettings() {
        SteadmoLog.event("ui: opening notification access settings")
        val component = ComponentName(this, CamapsListener::class.java).flattenToString()
        startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component),
        )
    }
}
