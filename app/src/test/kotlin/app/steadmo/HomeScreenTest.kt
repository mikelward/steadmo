package app.steadmo

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

    private fun show(state: GlucoseState, listener: Boolean = true, notifications: Boolean = true, now: Long = Samples.NOW) {
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

    @Test fun `Boost and Ease-off open CamAPS FX`() {
        show(Samples.rising())
        compose.onNodeWithText("Boost").performScrollTo().performClick()
        compose.onNodeWithText("Ease-off").performScrollTo().performClick()
        assertEquals(2, opened)
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
            .assertIsDisplayed()
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
