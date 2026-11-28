package app.steadmo

import app.steadmo.core.CamapsParser
import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UrgentLowActivityTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    // An urgent low before the activity starts: it closes itself otherwise.
    @get:Rule(order = 0) val urgent = object : org.junit.rules.ExternalResource() {
        override fun before() = post("2.8")
    }

    @get:Rule(order = 1) val compose = createAndroidComposeRule<UrgentLowActivity>()

    private fun post(value: String) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dump = NotificationDump.of(app, camapsPost(app, value, System.currentTimeMillis()))
        app.repository.onCamapsNotification(dump, CamapsParser.parse(dump.viewTexts))
    }

    @Test fun `shows the value and its age, and closes once glucose recovers`() {
        compose.onNodeWithText("2.8 mmol/L").assertExists()
        compose.onNodeWithText("just now").assertExists()
        compose.runOnUiThread {
            val dump = NotificationDump.of(app, camapsPost(app, "3.6", System.currentTimeMillis() + 5 * MINUTE))
            app.repository.onCamapsNotification(dump, CamapsParser.parse(dump.viewTexts))
        }
        compose.waitForIdle()
        assertTrue(compose.activity.isFinishing)
    }

    @Test @org.robolectric.annotation.Config(qualifiers = "w800dp-h360dp-land")
    fun `on a short landscape screen, Treated and OK are still reachable`() {
        compose.onNodeWithText("OK").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Treated").performScrollTo().assertIsDisplayed()
    }

    @Test fun `ages are minutes`() {
        assertEquals("just now", UrgentLowActivity.age(30_000))
        assertEquals("12 min ago", UrgentLowActivity.age(12 * MINUTE))
    }

    @Test fun `OK acknowledges and closes`() {
        compose.onNodeWithText("Urgent low").assertExists()
        compose.onNodeWithText("OK").performClick()
        assertNotNull(UrgentLowAck.get(app))
        assertTrue(compose.activity.isFinishing)
    }

    @Test fun `Treated records the treatment and closes`() {
        val before = app.repository.state.value.treatments.size
        compose.onNodeWithText("Treated").performClick()
        assertEquals(before + 1, app.repository.state.value.treatments.size)
        assertTrue(compose.activity.isFinishing)
    }

    @Test fun `UrgentLowScreen shows predictive copy when heading below threshold soon`() {
        val base = System.currentTimeMillis()
        compose.runOnUiThread {
            // Outside the 20-minute trend window of the initial 2.8 reading, so the slope is -0.06 mmol/L/min.
            val first = NotificationDump.of(app, camapsPost(app, "3.8", base + 25 * MINUTE))
            app.repository.onCamapsNotification(first, CamapsParser.parse(first.viewTexts))
            val second = NotificationDump.of(app, camapsPost(app, "3.5", base + 30 * MINUTE))
            app.repository.onCamapsNotification(second, CamapsParser.parse(second.viewTexts))
        }
        compose.waitForIdle()
        assertTrue(!compose.activity.isFinishing)
        compose.onNodeWithText("Urgent low soon").assertExists()
        compose.onNodeWithText("Heading below 3.0 in about", substring = true).assertExists()
    }
}
