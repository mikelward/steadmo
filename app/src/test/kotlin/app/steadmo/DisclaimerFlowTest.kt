package app.steadmo

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** First run: Steadmo can't be used until the disclaimer is ticked and accepted. */
@RunWith(RobolectricTestRunner::class)
class DisclaimerFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `Continue needs the box ticked, then opens the app and remembers`() {
        compose.onNodeWithText("Before you use Steadmo").assertExists()
        compose.onNodeWithText("Allow notification access").assertDoesNotExist()
        compose.onNodeWithText("Continue").performScrollTo().assertIsNotEnabled().performClick()
        assertFalse(Disclaimer.accepted(context))

        compose.onNodeWithText(AGREEMENT).performScrollTo().performClick()
        compose.onNodeWithText("Continue").assertIsEnabled().performClick()

        assertTrue(Disclaimer.accepted(context))
        compose.onNodeWithText("Allow notification access").assertExists()
    }

    @Test fun `unticking disables Continue again`() {
        compose.onNodeWithText(AGREEMENT).performScrollTo().performClick()
        compose.onNodeWithText(AGREEMENT).performClick()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test fun `Leave closes the app without accepting`() {
        compose.onNodeWithText("Leave").performScrollTo().performClick()
        assertTrue(compose.activity.isFinishing)
        assertFalse(Disclaimer.accepted(context))
    }

    @Test fun `once accepted it can be read again and closed`() {
        compose.onNodeWithText(AGREEMENT).performScrollTo().performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Disclaimer").performScrollTo().performClick()
        compose.onNodeWithText("Before you use Steadmo").assertExists()
        compose.onNodeWithText(AGREEMENT).assertDoesNotExist()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.onNodeWithText("Allow notification access").assertExists()
    }

    @Test fun `a newer wording asks again`() {
        context.getSharedPreferences("steadmo", Context.MODE_PRIVATE).edit()
            .putInt("disclaimer_accepted_version", Disclaimer.VERSION - 1).commit()
        assertFalse(Disclaimer.accepted(context))
    }
}
