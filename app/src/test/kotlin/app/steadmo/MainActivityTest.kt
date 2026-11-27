package app.steadmo

import android.app.Application
import android.provider.Settings
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    // Accepted before the activity starts; DisclaimerFlowTest covers first run.
    @get:Rule(order = 0) val accepted = object : org.junit.rules.ExternalResource() {
        override fun before() = Disclaimer.accept(ApplicationProvider.getApplicationContext())
    }

    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `Allow opens Steadmo's notification access page`() {
        compose.onNodeWithText("Allow notification access").assertExists()
        compose.onAllNodesWithTextFirst("Allow").performClick()
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, intent.action)
        assertEquals(
            "app.steadmo/app.steadmo.CamapsListener",
            intent.getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME)!!.replace(".debug/", "/"),
        )
    }

    @Test fun `Open App info opens Steadmo's app details`() {
        compose.onNodeWithText("Open App info").performClick()
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${app.packageName}", intent.dataString)
    }

    @Test fun `Get latest version opens the GitHub release`() {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Get latest version").performClick()
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_VIEW, intent.action)
        // Debug builds download the newest debug APK directly, to install over this one.
        assertEquals("https://github.com/mikelward/steadmo/releases/latest/download/steadmo-debug.apk", intent.dataString)
    }

    @Test fun `CamAPS FX shortcuts opens Accessibility settings`() {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("CamAPS FX shortcuts").performClick()
        compose.onNodeWithText("Open settings").performClick()
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, shadowOf(app).nextStartedActivity.action)
    }

    @Test fun `silenced alerts open their channel settings`() {
        val manager = app.getSystemService(android.app.NotificationManager::class.java)
        manager.deleteNotificationChannel(StatusNotifier.CHANNEL_ALERTS)
        manager.createNotificationChannel(
            android.app.NotificationChannel(StatusNotifier.CHANNEL_ALERTS, "Alerts", android.app.NotificationManager.IMPORTANCE_LOW),
        )
        app.allowNotifications()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.onNodeWithText("Alerts are silenced").assertExists()
        compose.onNodeWithText("Open settings").performScrollTo().performClick()
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(StatusNotifier.CHANNEL_ALERTS, intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }

    @Test fun `About links to the source code`() {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("About").performClick()
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertExists()
        compose.onNodeWithText("Source code").performClick()
        assertEquals(MainActivity.SOURCE_URL, shadowOf(app).nextStartedActivity.dataString)
    }

    @Test fun `Not now hides the shortcuts suggestion for good`() {
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithText("Go straight to CamAPS FX's screens").assertDoesNotExist()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.onNodeWithText("Go straight to CamAPS FX's screens").assertDoesNotExist()
    }

    @Test fun `Known issues opens the GitHub issues`() {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Known issues").performClick()
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_VIEW, intent.action)
        assertEquals(MainActivity.KNOWN_ISSUES_URL, intent.dataString)
    }

    @Test fun `Other with no CamAPS FX installed starts nothing`() {
        compose.onNodeWithText("Other").performClick()
        assertEquals(null, shadowOf(app).nextStartedActivity)
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextFirst(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text) and androidx.compose.ui.test.hasClickAction())[0]
}
