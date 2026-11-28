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

/** MainActivity's pages, besides first-run's disclaimer. */
enum class Screen { HOME, SETTINGS, DISCLAIMER, WEEKLY_REPORT }

class MainActivity : ComponentActivity() {
    private var listenerAllowed by mutableStateOf(false)
    private var notificationsAllowed by mutableStateOf(false)
    private var alertsSilenced by mutableStateOf(false)
    private var fullScreenAllowed by mutableStateOf(true)
    private var mealReminderRules by mutableStateOf(listOf(app.steadmo.core.MealReminder.defaultRule()))
    private var calendarMealsOn by mutableStateOf(false)
    private val requestCalendar =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            SteadmoLog.event("ui: calendar access %s", if (granted) "granted" else "refused")
            CalendarMealReminders.set(this, granted)
            calendarMealsOn = granted
        }
    private var remindersOnTime by mutableStateOf(true)
    private var weeklyReportOn by mutableStateOf(true)
    private var weeklyReportDay by mutableStateOf(app.steadmo.core.WeeklyReportSchedule.DEFAULT_DAY)
    private var weeklyReportTime by mutableStateOf(app.steadmo.core.WeeklyReportSchedule.DEFAULT_TIME)
    private var pendingScreen by mutableStateOf<Screen?>(null)
    private var suggestShortcuts by mutableStateOf(false)
    private var shortcutsOn by mutableStateOf(false)
    private var disclaimerAccepted by mutableStateOf(false)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = it }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedScreen(intent)?.let { pendingScreen = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        disclaimerAccepted = Disclaimer.accepted(this)
        val initialScreen = requestedScreen(intent) ?: Screen.HOME
        setContent {
            // Which page is showing; saved, so rotation keeps it. Back returns home from each.
            var screen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialScreen) }
            androidx.compose.runtime.LaunchedEffect(pendingScreen) {
                pendingScreen?.let {
                    screen = it
                    pendingScreen = null
                }
            }
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
                if (screen == Screen.DISCLAIMER) {
                    DisclaimerScreen(onAccept = null, onLeave = { screen = Screen.HOME })
                    return@SteadmoTheme
                }
                val state by repository.state.collectAsStateWithLifecycle()
                if (screen == Screen.WEEKLY_REPORT) {
                    val zone = java.time.ZoneId.systemDefault()
                    val summary = androidx.compose.runtime.remember(state.readings, state.treatments, state.unit) {
                        app.steadmo.core.WeeklyReport.generate(
                            readings = state.readings,
                            now = System.currentTimeMillis(),
                            zone = zone,
                            unit = state.unit,
                            treatments = state.treatments,
                        )
                    }
                    WeeklyReportScreen(
                        summary = summary,
                        unit = state.unit,
                        zone = zone,
                        onShare = {
                            val text = app.steadmo.core.WeeklyReport.formatText(summary, state.unit, zone)
                            WeeklyReports.share(this, text)
                        },
                        onBack = { screen = Screen.HOME },
                    )
                    return@SteadmoTheme
                }
                if (screen == Screen.SETTINGS) {
                    SettingsScreen(
                        unit = state.unit,
                        onSetUnit = repository::setUnit,
                        onBack = { screen = Screen.HOME },
                        mealReminderRules = mealReminderRules,
                        formatTime = ::formatTime,
                        onToggleMealRule = { rule, on ->
                            mealReminderRules = MealReminders.updateRule(this, rule.copy(enabled = on))
                        },
                        onPickMealRuleTime = ::pickMealRuleTime,
                        onToggleMealRuleDay = { rule, day ->
                            val days = if (day in rule.days) rule.days - day else rule.days + day
                            mealReminderRules = MealReminders.updateRule(this, rule.copy(days = days))
                        },
                        onDeleteMealRule = { rule ->
                            mealReminderRules = MealReminders.deleteRule(this, rule.id)
                        },
                        onAddMealRule = ::addMealRule,
                        calendarMealsOn = calendarMealsOn,
                        onCalendarMealsOn = ::setCalendarMeals,
                        remindersOnTime = remindersOnTime,
                        onAllowRemindersOnTime = ::openExactAlarmSettings,
                        weeklyReportOn = weeklyReportOn,
                        weeklyReportDay = weeklyReportDay,
                        weeklyReportTime = formatTime(weeklyReportTime),
                        onWeeklyReportOn = { on ->
                            WeeklyReports.set(this, on, weeklyReportDay, weeklyReportTime)
                            weeklyReportOn = on
                        },
                        onPickWeeklyReportDay = { day ->
                            weeklyReportDay = day
                            WeeklyReports.set(this, weeklyReportOn, day, weeklyReportTime)
                        },
                        onPickWeeklyReportTime = ::pickWeeklyReportTime,
                        shortcutsOn = shortcutsOn,
                        onOpenShortcutsSettings = ::openAccessibilitySettings,
                        onOpenAppInfo = ::openAppInfo,
                    )
                    return@SteadmoTheme
                }
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
                    onShowDisclaimer = { screen = Screen.DISCLAIMER },
                    onOpenAppInfo = ::openAppInfo,
                    onGetLatest = ::openLatestRelease,
                    onKnownIssues = ::openKnownIssues,
                    shortcutsOn = shortcutsOn,
                    onOpenShortcutsSettings = ::openAccessibilitySettings,
                    onWeeklyReport = { screen = Screen.WEEKLY_REPORT },
                    onSettings = { screen = Screen.SETTINGS },
                    alertsSilenced = alertsSilenced,
                    fullScreenAllowed = fullScreenAllowed,
                    onAllowFullScreen = ::openFullScreenSettings,
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
        mealReminderRules = MealReminders.rules(this)
        calendarMealsOn = CalendarMealReminders.enabled(this) && CalendarMealReminders.permitted(this)
        weeklyReportOn = WeeklyReports.enabled(this)
        weeklyReportDay = WeeklyReports.day(this)
        weeklyReportTime = WeeklyReports.time(this)
        remindersOnTime = Alarms.exactAllowed(this)
        fullScreenAllowed = getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()
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
        launch("the download page", Intent(Intent.ACTION_VIEW, android.net.Uri.parse(LATEST_RELEASE_URL)))
    }

    /** "Not now" on the CamAPS FX shortcuts card: don't suggest it again (Settings still has it). */
    private fun dismissShortcutsSuggestion() {
        SteadmoLog.event("ui: CamAPS FX shortcuts suggestion dismissed")
        getSharedPreferences(PREFS_UI, MODE_PRIVATE).edit().putBoolean(KEY_SHORTCUTS_DISMISSED, true).apply()
        suggestShortcuts = false
    }

    private fun openSourceCode() {
        SteadmoLog.event("ui: opening the source code")
        launch("the source code", Intent(Intent.ACTION_VIEW, android.net.Uri.parse(SOURCE_URL)))
    }

    /** Open issues on GitHub: what's known to be wrong, and whether a report is already filed. */
    private fun openKnownIssues() {
        SteadmoLog.event("ui: opening known issues")
        launch("known issues", Intent(Intent.ACTION_VIEW, android.net.Uri.parse(KNOWN_ISSUES_URL)))
    }

    companion object {
        const val EXTRA_SCREEN = "app.steadmo.screen"
        const val LATEST_RELEASE_URL = BuildConfig.LATEST_DOWNLOAD_URL
        const val KNOWN_ISSUES_URL = "https://github.com/mikelward/steadmo/issues"
        const val SOURCE_URL = "https://github.com/mikelward/steadmo"
        private const val PREFS_UI = "ui"
        private const val KEY_SHORTCUTS_DISMISSED = "shortcuts_suggestion_dismissed"
    }

    private fun requestedScreen(intent: Intent?): Screen? =
        intent?.getStringExtra(EXTRA_SCREEN)?.let { name -> Screen.entries.firstOrNull { it.name == name } }

    /** Turning calendar meals on asks for calendar access first, if needed. */
    private fun setCalendarMeals(on: Boolean) {
        if (on && !CalendarMealReminders.permitted(this)) {
            requestCalendar.launch(Manifest.permission.READ_CALENDAR)
            return
        }
        CalendarMealReminders.set(this, on)
        calendarMealsOn = on
    }

    /** A local time in the phone's 12- or 24-hour format. */
    private fun formatTime(time: java.time.LocalTime): String = android.text.format.DateFormat.getTimeFormat(this).format(
        java.util.Date.from(time.atDate(java.time.LocalDate.now()).atZone(java.time.ZoneId.systemDefault()).toInstant()),
    )

    private fun pickMealRuleTime(rule: app.steadmo.core.MealReminderRule) {
        android.app.TimePickerDialog(
            this,
            { _, hour, minute ->
                mealReminderRules = MealReminders.updateRule(this, rule.copy(time = java.time.LocalTime.of(hour, minute)))
            },
            rule.time.hour,
            rule.time.minute,
            android.text.format.DateFormat.is24HourFormat(this),
        ).show()
    }

    private fun addMealRule() {
        val suggested = if (mealReminderRules.any { it.time == app.steadmo.core.MealReminder.DEFAULT_TIME }) {
            java.time.LocalTime.of(12, 0)
        } else {
            app.steadmo.core.MealReminder.DEFAULT_TIME
        }
        android.app.TimePickerDialog(
            this,
            { _, hour, minute ->
                mealReminderRules = MealReminders.addRule(this, java.time.LocalTime.of(hour, minute))
            },
            suggested.hour,
            suggested.minute,
            android.text.format.DateFormat.is24HourFormat(this),
        ).show()
    }

    private fun pickWeeklyReportTime() {
        android.app.TimePickerDialog(
            this,
            { _, hour, minute ->
                weeklyReportTime = java.time.LocalTime.of(hour, minute)
                WeeklyReports.set(this, weeklyReportOn, weeklyReportDay, weeklyReportTime)
            },
            weeklyReportTime.hour,
            weeklyReportTime.minute,
            android.text.format.DateFormat.is24HourFormat(this),
        ).show()
    }

    /** "Alarms & reminders" for Steadmo, so reminders go off on time. */
    private fun openExactAlarmSettings() {
        SteadmoLog.event("ui: opening alarms and reminders settings")
        launch(
            "Alarms & reminders",
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.fromParts("package", packageName, null)),
        )
    }

    /** Android 14+'s "Full screen intents" page for Steadmo. */
    private fun openFullScreenSettings() {
        SteadmoLog.event("ui: opening full screen intent settings")
        launch(
            "full screen settings",
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.fromParts("package", packageName, null)),
        )
    }

    /** Steadmo's alerts channel, where the user can raise its importance again. */
    private fun openAlertSettings() {
        SteadmoLog.event("ui: opening alert channel settings")
        launch(
            "alert settings",
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, StatusNotifier.CHANNEL_ALERTS),
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }

    /** Accessibility settings, where the user turns CamAPS FX shortcuts on or off. */
    private fun openAccessibilitySettings() {
        SteadmoLog.event("ui: opening accessibility settings for CamAPS FX shortcuts")
        launch("Accessibility settings", Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** App info, where Android 13+ hides "Allow restricted settings" for sideloaded apps. */
    private fun openAppInfo() {
        SteadmoLog.event("ui: opening app info for restricted settings")
        launch(
            "App info",
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null)),
        )
    }

    private fun openListenerSettings() {
        SteadmoLog.event("ui: opening notification access settings")
        val component = ComponentName(this, CamapsListener::class.java).flattenToString()
        launch(
            "notification access settings",
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component),
            // Some phones have no page for one app: the list of all.
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        )
    }

    /**
     * Opens the first of [intents] the phone can, e.g. a settings page, then a
     * broader one. With none (no browser, a missing settings page), says so
     * instead of crashing.
     */
    private fun launch(what: String, vararg intents: Intent) {
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (e: android.content.ActivityNotFoundException) {
                SteadmoLog.failure(e, "ui: nothing opens %s", intent.action)
            }
        }
        android.widget.Toast.makeText(this, "Couldn't open $what on this phone.", android.widget.Toast.LENGTH_LONG).show()
    }
}
