package app.steadmo

import app.steadmo.core.CamapsGoal
import app.steadmo.core.GlucoseUnit
import android.app.NotificationManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val meals = mutableListOf<Int>()
    private var opened = 0
    private var listenerAsked = 0
    private var notificationsAsked = 0

    private var minimizeAsked = 0
    private var bugReports = 0
    private var latestAsked = 0
    private var issuesAsked = 0
    private var disclaimerShown = 0
    private var shortcutsSettings = 0
    private var appInfo = 0

    private fun show(
        state: GlucoseState,
        listener: Boolean = true,
        notifications: Boolean = true,
        now: Long = Samples.NOW,
        shortcuts: Boolean = false,
    ) {
        compose.setContent {
            SteadmoTheme(dark = false) {
                HomeScreen(
                    state = state,
                    listenerAllowed = listener,
                    notificationsAllowed = notifications,
                    onAllowListener = { listenerAsked++ },
                    onAllowNotifications = { notificationsAsked++ },
                    onOpenCamaps = { opened++ },
                    onMeal = { meals += it },
                    onMinimizeCamaps = { minimizeAsked++ },
                    onReportBug = { bugReports++ },
                    onGetLatest = { latestAsked++ },
                    onKnownIssues = { issuesAsked++ },
                    onShowDisclaimer = { disclaimerShown++ },
                    onOpenAppInfo = { appInfo++ },
                    shortcutsOn = shortcuts,
                    onOpenShortcutsSettings = { shortcutsSettings++ },
                    clock = { now },
                    ticking = false,
                )
            }
        }
    }

    @Test fun `meal presets pass their grams and Other opens CamAPS FX`() {
        show(Samples.rising())
        compose.onNodeWithText("20 g").performScrollTo().performClick()
        compose.onNodeWithText("45 g").performScrollTo().performClick()
        compose.onNodeWithText("Other").performScrollTo().performClick()
        assertEquals(listOf(20, 45), meals)
        assertEquals(1, opened)
    }

    @Test fun `Boost and Ease-off ask for their CamAPS FX dialogs`() {
        val modes = mutableListOf<CamapsGoal.Mode>()
        compose.setContent {
            SteadmoTheme(dark = false) {
                HomeScreen(Samples.rising(), true, true, {}, {}, { opened++ }, {}, onMode = { modes += it }, ticking = false)
            }
        }
        compose.onNodeWithText("Boost").performScrollTo().performClick()
        compose.onNodeWithText("Ease-off").performScrollTo().performClick()
        assertEquals(listOf(CamapsGoal.BOOST, CamapsGoal.EASE_OFF), modes)
    }

    @Test fun `Boost opens CamAPS FX by default`() {
        show(Samples.rising())
        compose.onNodeWithText("Boost").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test fun `shows value, arrow and rate`() {
        show(Samples.rising())
        compose.onNodeWithText("9.4").assertIsDisplayed()
        compose.onNodeWithContentDescription("Rising").assertIsDisplayed()
        compose.onNodeWithText("Rising · +0.12 mmol/L/min").assertIsDisplayed()
        compose.onNodeWithText("Auto mode on").assertIsDisplayed()
    }

    @Test fun `asks for access it lacks`() {
        show(GlucoseState(), listener = false, notifications = false)
        compose.onNodeWithText("Allow notification access").assertIsDisplayed()
        compose.onNodeWithText("Allow notifications").assertIsDisplayed()
        compose.onNodeWithText("Waiting for CamAPS FX. In CamAPS FX's notification settings, turn on My diabetes info.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun `old readings say so`() {
        show(Samples.rising(), now = Samples.NOW + 20 * MINUTE)
        compose.onNodeWithText("No new reading from CamAPS FX for over 10 minutes.").performScrollTo().assertIsDisplayed()
    }

    @Test fun `an old Auto mode report says how old it is`() {
        show(Samples.rising(), now = Samples.NOW + 20 * MINUTE)
        compose.onNodeWithText("Auto mode on, 20 minutes ago").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun `suggests minimizing CamAPS FX until it is`() {
        show(Samples.rising().copy(camapsChannelId = "info", camapsChannelImportance = NotificationManager.IMPORTANCE_LOW))
        compose.onNodeWithText("Open settings").performClick()
        assertEquals(1, minimizeAsked)
    }

    @Test fun `no suggestion once CamAPS FX is minimized`() {
        show(Samples.rising().copy(camapsChannelId = "info", camapsChannelImportance = NotificationManager.IMPORTANCE_MIN))
        compose.onNodeWithText("Open settings").assertDoesNotExist()
    }

    @Test fun `the menu offers the latest version and the disclaimer`() {
        show(Samples.rising())
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Get latest version").performClick()
        assertEquals(1, latestAsked)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Known issues").performClick()
        assertEquals(1, issuesAsked)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Disclaimer").performClick()
        assertEquals(1, disclaimerShown)
    }

    @Test fun `CamAPS FX shortcuts explains itself before opening settings`() {
        show(Samples.rising())
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("CamAPS FX shortcuts").performClick()
        compose.onNodeWithText("Steadmo never delivers insulin", substring = true).assertIsDisplayed()
        compose.onNodeWithText("They're off", substring = true).assertIsDisplayed()
        compose.onNodeWithText("App info").performClick()
        assertEquals(1, appInfo)

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("CamAPS FX shortcuts").performClick()
        compose.onNodeWithText("Open settings").performClick()
        assertEquals(1, shortcutsSettings)
        compose.onNodeWithText("Open settings").assertDoesNotExist()
    }

    @Test fun `CamAPS FX shortcuts says when it's on`() {
        show(Samples.rising(), shortcuts = true)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("CamAPS FX shortcuts").performClick()
        compose.onNodeWithText("They're on.").assertIsDisplayed()
        compose.onNodeWithText("App info").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("They're on.").assertDoesNotExist()
    }

    @Test fun `silenced alerts say so and open their settings`() {
        var opened = 0
        compose.setContent {
            SteadmoTheme(dark = false) {
                HomeScreen(
                    Samples.rising(), true, true, {}, {}, {}, {},
                    alertsSilenced = true, onOpenAlertSettings = { opened++ }, ticking = false,
                )
            }
        }
        compose.onNodeWithText("Alerts are silenced").assertIsDisplayed()
        compose.onNodeWithText("Open settings").performClick()
        assertEquals(1, opened)
    }

    @Test fun `About shows the version and the licence`() {
        var source = 0
        compose.setContent {
            SteadmoTheme(dark = false) {
                HomeScreen(Samples.rising(), true, true, {}, {}, {}, {}, version = "48+abc", onSourceCode = { source++ }, ticking = false)
            }
        }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("About").performClick()
        compose.onNodeWithText("Version 48+abc").assertIsDisplayed()
        compose.onNodeWithText("Source code").performClick()
        assertEquals(1, source)
        compose.onNodeWithText("Licence").performClick()
        compose.onNodeWithText("Apache License 2.0").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Version 48+abc").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Version 48+abc").assertDoesNotExist()
    }

    @Test fun `suggests CamAPS FX shortcuts until set up or dismissed`() {
        var dismissed = 0
        var other = 0
        compose.setContent {
            SteadmoTheme(dark = false) {
                HomeScreen(
                    Samples.rising(), true, true, {}, {}, {}, {},
                    suggestShortcuts = true, onDismissShortcuts = { dismissed++ }, onOtherMeal = { other++ }, ticking = false,
                )
            }
        }
        compose.onNodeWithText("Go straight to CamAPS FX's screens").assertIsDisplayed()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(1, dismissed)
        compose.onNodeWithText("Set up").performClick()
        compose.onNodeWithText("Open settings").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Other").performScrollTo().performClick()
        assertEquals(1, other)
    }

    @Test fun `asks to let urgent lows fill the screen`() {
        var asked = 0
        compose.setContent {
            SteadmoTheme(dark = false) {
                HomeScreen(Samples.rising(), true, true, {}, {}, {}, {}, fullScreenAllowed = false, onAllowFullScreen = { asked++ }, ticking = false)
            }
        }
        compose.onNodeWithText("Let urgent lows fill the screen").assertIsDisplayed()
        compose.onNodeWithText("Allow").performClick()
        assertEquals(1, asked)
    }

    @Test fun `Meal reminder turns on and off and picks a time`() {
        val toggles = mutableListOf<Boolean>()
        val calendar = mutableListOf<Boolean>()
        var picks = 0
        compose.setContent {
            SteadmoTheme(dark = false) {
                HomeScreen(
                    Samples.rising(), true, true, {}, {}, {}, {},
                    mealReminderOn = true, mealReminderTime = "18:00",
                    onMealReminderOn = { toggles += it }, onPickMealReminderTime = { picks++ },
                    onCalendarMealsOn = { calendar += it }, ticking = false,
                )
            }
        }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Meal reminder").performClick()
        compose.onNodeWithText("Remind me").performClick()
        assertEquals(listOf(false), toggles)
        compose.onNodeWithText("At 18:00").performClick()
        assertEquals(1, picks)
        compose.onNodeWithText("Calendar meals").performClick()
        assertEquals(listOf(false, true), toggles + calendar)
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Remind me").assertDoesNotExist()
    }

    @Test fun `Settings picks mmol per L or mg per dL, and Back returns`() {
        val picked = mutableListOf<GlucoseUnit>()
        var back = 0
        compose.setContent {
            SteadmoTheme(dark = false) { SettingsScreen(GlucoseUnit.MMOL, onSetUnit = { picked += it }, onBack = { back++ }) }
        }
        compose.onNodeWithText("Units").performClick()
        compose.onNodeWithText("mg/dL").performClick()
        assertEquals(listOf(GlucoseUnit.MGDL), picked)
        compose.onNodeWithText("mg/dL").assertDoesNotExist()
        compose.onNodeWithText("Units").performClick()
        compose.onNodeWithText("Close").performClick()
        assertEquals(listOf(GlucoseUnit.MGDL), picked)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, back)
    }

    @Test fun `the menu opens Settings`() {
        var settings = 0
        compose.setContent {
            SteadmoTheme(dark = false) {
                HomeScreen(Samples.rising(), true, true, {}, {}, {}, {}, onSettings = { settings++ }, ticking = false)
            }
        }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Units").assertDoesNotExist()
        compose.onNodeWithText("Settings").performClick()
        assertEquals(1, settings)
    }

    @Test fun `mg per dL shows on the home screen`() {
        show(Samples.rising().copy(unit = GlucoseUnit.MGDL))
        compose.onNodeWithText("169").assertIsDisplayed()
        compose.onNodeWithText("+2.2 mg/dL/min", substring = true).assertIsDisplayed()
        compose.onNodeWithText("mg/dL · ", substring = true).assertIsDisplayed()
    }

    @Test fun `Report a bug asks for a report`() {
        show(Samples.rising())
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Report a bug").performClick()
        assertEquals(1, bugReports)
    }

    @Test fun `a bound shows its sign`() {
        show(Samples.belowSensor())
        compose.onNodeWithText("<2.2").assertIsDisplayed()
    }

    @Test fun `a reading under a minute old is just now`() {
        show(Samples.rising())
        compose.onNodeWithText("mmol/L · just now").assertIsDisplayed()
    }

    @Test fun `no trend until enough readings`() {
        show(Samples.single())
        compose.onNodeWithContentDescription("No trend yet").assertIsDisplayed()
        compose.onNodeWithText("Trend after 5 min of readings").assertIsDisplayed()
    }

    @Test fun `notification details open and copy`() {
        show(Samples.rising())
        compose.onNodeWithText("Show CamAPS FX notification").performScrollTo().performClick()
        compose.onNodeWithContentDescription("CamAPS FX notification contents").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Copy").performScrollTo().performClick()
        val clip = compose.activity.getSystemService(android.content.ClipboardManager::class.java).primaryClip!!
        assertEquals(Samples.dump.describe(), clip.getItemAt(0).text)
        compose.onNodeWithText("Hide CamAPS FX notification").performScrollTo().performClick()
        compose.onNodeWithText("Show CamAPS FX notification").assertIsDisplayed()
    }
}
