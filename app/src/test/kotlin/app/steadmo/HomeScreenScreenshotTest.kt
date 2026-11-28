package app.steadmo

import app.steadmo.core.GlucoseUnit
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The home screen in each state it can be in, light and dark. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun home(state: GlucoseState, listener: Boolean = true, notifications: Boolean = true, now: Long = Samples.NOW): @Composable () -> Unit = {
        HomeScreen(state, listener, notifications, {}, {}, {}, {}, clock = { now }, ticking = false)
    }

    @Test fun rising() = both("home-rising") { home(Samples.rising()) }
    @Test fun risingMgdl() = both("home-rising-mgdl") { home(Samples.rising().copy(unit = GlucoseUnit.MGDL)) }
    @Test fun fallingFast() = both("home-falling-fast") { home(Samples.fallingFast()) }
    @Test fun stale() = both("home-stale") { home(Samples.rising(), now = Samples.NOW + 20 * MINUTE) }
    @Test fun belowSensor() = both("home-below-sensor") { home(Samples.belowSensor()) }
    @Test fun minimizeCamaps() = both("home-minimize-camaps") {
        home(Samples.rising().copy(camapsChannelId = "info", camapsChannelImportance = android.app.NotificationManager.IMPORTANCE_LOW))
    }
    @Test fun disclaimer() = both("disclaimer") { { DisclaimerScreen(onAccept = {}, onLeave = {}) } }
    @Test fun alertsSilenced() = both("home-alerts-silenced") {
        { HomeScreen(Samples.rising(), true, true, {}, {}, {}, {}, alertsSilenced = true, clock = { Samples.NOW }, ticking = false) }
    }
    @Test fun treated() = both("home-treated") {
        home(Samples.rising().copy(treatments = listOf(Samples.NOW - 60 * MINUTE)))
    }
    @Test fun urgentLow() = both("urgent-low") { { UrgentLowScreen("2.8 mmol/L", "just now", stale = false, onTreated = {}, onOk = {}) } }
    @Test fun urgentLowStale() = both("urgent-low-stale") { { UrgentLowScreen("2.8 mmol/L", "12 min ago", stale = true, onTreated = {}, onOk = {}) } }
    @Test fun fullScreenCard() = both("home-full-screen") {
        { HomeScreen(Samples.rising(), true, true, {}, {}, {}, {}, fullScreenAllowed = false, clock = { Samples.NOW }, ticking = false) }
    }
    @Test fun reliabilityCards() = both("home-reliability") {
        {
            HomeScreen(
                Samples.rising(), true, true, {}, {}, {}, {},
                urgentLowSilenced = true,
                alarmVolumeMuted = true,
                exactAlarmsAllowed = false,
                batteryUnrestricted = false,
                unusedAppRestrictionsDisabled = false,
                clock = { Samples.NOW },
                ticking = false,
            )
        }
    }
    @Test fun settings() = both("settings") { { SettingsScreen(GlucoseUnit.MMOL, onSetUnit = {}, onBack = {}) } }
    @Test fun setup() = both("home-setup") { home(GlucoseState(), listener = false, notifications = false) }

    @Test fun aboutLight() = dialog("about", dark = false) { AboutDialog("48+abc1234", onSourceCode = {}, onDismiss = {}) }
    @Test fun aboutDark() = dialog("about", dark = true) { AboutDialog("48+abc1234", onSourceCode = {}, onDismiss = {}) }
    @Test fun suggestShortcuts() = both("home-suggest-shortcuts") {
        { HomeScreen(Samples.rising(), true, true, {}, {}, {}, {}, suggestShortcuts = true, clock = { Samples.NOW }, ticking = false) }
    }
    private val sampleMealRules = listOf(
        app.steadmo.core.MealReminder.defaultRule(),
        app.steadmo.core.MealReminderRule(
            id = 2L,
            time = java.time.LocalTime.of(12, 30),
            days = setOf(
                java.time.DayOfWeek.MONDAY,
                java.time.DayOfWeek.TUESDAY,
                java.time.DayOfWeek.WEDNESDAY,
                java.time.DayOfWeek.THURSDAY,
                java.time.DayOfWeek.FRIDAY,
            ),
        ),
    )

    @Test fun mealReminderLight() = dialog("meal-reminder", dark = false) {
        MealReminderDialog(
            rules = sampleMealRules,
            onToggleRule = { _, _ -> }, onPickRuleTime = {}, onToggleRuleDay = { _, _ -> },
            onDeleteRule = {}, onAddRule = {}, onDismiss = {},
        )
    }
    @Test fun mealReminderDark() = dialog("meal-reminder", dark = true) {
        MealReminderDialog(
            rules = sampleMealRules,
            onToggleRule = { _, _ -> }, onPickRuleTime = {}, onToggleRuleDay = { _, _ -> },
            onDeleteRule = {}, onAddRule = {}, onDismiss = {},
        )
    }
    @Test fun unitsLight() = dialog("units", dark = false) {
        UnitsDialog(GlucoseUnit.MMOL, onPick = {}, onDismiss = {})
    }
    @Test fun unitsDark() = dialog("units", dark = true) {
        UnitsDialog(GlucoseUnit.MMOL, onPick = {}, onDismiss = {})
    }
    @Test fun weeklyReport() {
        val zone = java.time.ZoneId.of("UTC")
        val end = java.time.ZonedDateTime.of(2026, 11, 28, 11, 0, 0, 0, zone).toInstant().toEpochMilli()
        val summary = app.steadmo.core.WeeklyReport.Summary(
            periodStart = end - app.steadmo.core.WeeklyReport.WINDOW_MILLIS,
            periodEnd = end,
            readingCount = 1940,
            coveragePercent = 96,
            averageMmol = 7.4,
            lowPercent = 6,
            veryLowPercent = 1,
            inRangePercent = 74,
            highPercent = 20,
            cvPercent = 33,
            treatmentCount = 2,
            comparison = app.steadmo.core.WeeklyReport.WeekComparison(
                priorInRangePercent = 69,
                inRangeDeltaPercent = 5,
                averageDeltaMmol = -0.3,
                lowDeltaPercent = 1,
                highDeltaPercent = -6,
            ),
            issues = listOf(
                app.steadmo.core.WeeklyReport.ReportIssue(
                    app.steadmo.core.WeeklyReport.IssueSeverity.HIGH,
                    "Lows on 3 nights this week",
                    "Below 3.9 mmol/L between 23:00 and 07:00 on Tue, Thu and Fri (lowest 3.2).",
                ),
                app.steadmo.core.WeeklyReport.ReportIssue(
                    app.steadmo.core.WeeklyReport.IssueSeverity.MODERATE,
                    "Highs on 4 evenings this week",
                    "Above 10.0 mmol/L between 17:00 and 23:00 on Mon, Wed, Thu and Fri (highest 13.1).",
                ),
                app.steadmo.core.WeeklyReport.ReportIssue(
                    app.steadmo.core.WeeklyReport.IssueSeverity.INFO,
                    "Time in range up 5% from last week",
                    "74% this week, compared with 69% last week.",
                ),
            ),
        )
        both("weekly-report") {
            { WeeklyReportScreen(summary = summary, unit = GlucoseUnit.MMOL, zone = zone, onShare = {}, onBack = {}) }
        }
    }
    @Test fun weeklyReportEmpty() {
        val zone = java.time.ZoneId.of("UTC")
        val end = java.time.ZonedDateTime.of(2026, 11, 28, 11, 0, 0, 0, zone).toInstant().toEpochMilli()
        val summary = app.steadmo.core.WeeklyReport.generate(emptyList(), end, zone)
        both("weekly-report-empty") {
            { WeeklyReportScreen(summary = summary, unit = GlucoseUnit.MMOL, zone = zone, onShare = {}, onBack = {}) }
        }
    }
    @Test fun weeklyReportDialogLight() = dialog("weekly-report-dialog", dark = false) {
        WeeklyReportDialog(
            on = true,
            day = java.time.DayOfWeek.SATURDAY,
            time = "11:00",
            onToggle = {},
            onPickDay = {},
            onPickTime = {},
            onDismiss = {},
        )
    }
    @Test fun weeklyReportDialogDark() = dialog("weekly-report-dialog", dark = true) {
        WeeklyReportDialog(
            on = true,
            day = java.time.DayOfWeek.SATURDAY,
            time = "11:00",
            onToggle = {},
            onPickDay = {},
            onPickTime = {},
            onDismiss = {},
        )
    }

    /** A dialog has its own window, so each theme gets its own activity. */
    private fun dialog(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.activity.setContent { SteadmoTheme(dark = dark) { content() } }
        compose.onNode(androidx.compose.ui.test.isDialog())
            .captureRoboImage("src/test/snapshots/images/$name-${if (dark) "dark" else "light"}.png")
    }

    @Test fun shortcutsLight() = shortcuts(dark = false)
    @Test fun shortcutsDark() = shortcuts(dark = true)

    /** A dialog has its own window, so each theme gets its own activity. */
    private fun shortcuts(dark: Boolean) {
        compose.activity.setContent {
            SteadmoTheme(dark = dark) {
                ShortcutsDialog(on = false, onOpenSettings = {}, onOpenAppInfo = {}, onDismiss = {})
            }
        }
        compose.onNode(androidx.compose.ui.test.isDialog())
            .captureRoboImage("src/test/snapshots/images/camaps-shortcuts-${if (dark) "dark" else "light"}.png")
    }

    private fun both(name: String, content: () -> @Composable () -> Unit) {
        capture(this, compose, "$name-light.png", dark = false, content())
        capture(this, compose, "$name-dark.png", dark = true, content())
    }
}

/** Renders [content] in the activity's window and writes it under src/test/snapshots/images. */
fun capture(
    @Suppress("UNUSED_PARAMETER") test: Any,
    compose: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, out ComponentActivity>,
    name: String,
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    compose.activity.setContent { SteadmoTheme(dark = dark) { content() } }
    compose.waitForIdle()
    // Always drawn, so every test run exercises the drawing code; Roborazzi
    // writes the PNG only with -Proborazzi.test.record=true.
    val root = compose.activity.window.decorView.rootView
    root.measure(
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
    )
    root.layout(0, 0, 1080, 2400)
    val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
    root.draw(Canvas(bitmap))
    bitmap.captureRoboImage(filePath = "src/test/snapshots/images/$name")
}
