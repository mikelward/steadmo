package app.steadmo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Back from every page and dialog returns where it came from, and only home's back leaves. */
@RunWith(RobolectricTestRunner::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun accept() {
        compose.onNodeWithText(AGREEMENT).performScrollTo().performClick()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
    }

    /** Back as the system sends it: to the focused window, a dialog's if one is open. */
    private fun back() {
        androidx.test.espresso.Espresso.pressBackUnconditionally()
        compose.waitForIdle()
    }

    private fun menu(item: String) {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText(item).performClick()
    }

    private fun assertHome() = compose.onNodeWithContentDescription("More options").assertExists()

    @Test fun `back from the disclaimer returns home`() {
        menu("Disclaimer")
        compose.onNodeWithText("Before you use Steadmo").assertExists()
        back()
        assertHome()
        assertFalse(compose.activity.isFinishing)
    }

    @Test fun `back from Settings and its dialog returns step by step`() {
        menu("Settings")
        compose.onNodeWithText("Units").performClick()
        compose.onNodeWithText("mg/dL").assertExists()
        back()
        compose.onNodeWithText("mg/dL").assertDoesNotExist()
        compose.onNodeWithText("Settings").assertExists()
        back()
        assertHome()
        assertFalse(compose.activity.isFinishing)
    }

    @Test fun `Settings stays open across rotation`() {
        menu("Settings")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Back").assertExists()
    }

    @Test fun `back closes About`() {
        menu("About")
        compose.onNodeWithText("Licence").assertExists()
        back()
        compose.onNodeWithText("Licence").assertDoesNotExist()
        assertHome()
    }

    @Test fun `back closes each Settings dialog, then returns home`() {
        menu("Settings")
        for ((item, title) in listOf("Units" to "mg/dL", "Meal reminder" to "Remind me", "CamAPS FX shortcuts" to "Open settings")) {
            compose.onNodeWithText(item).performClick()
            compose.onNodeWithText(title).assertExists()
            back()
            compose.onNodeWithText(title).assertDoesNotExist()
            compose.onNodeWithContentDescription("Back").assertExists()
        }
        back()
        assertHome()
    }
}
