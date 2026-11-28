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

    @Test fun `with no page for one app, Allow opens the list of all`() {
        shadowOf(app).checkActivities(true)
        val fallback = android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        shadowOf(app.packageManager).addResolveInfoForIntent(
            fallback,
            android.content.pm.ResolveInfo().apply {
                activityInfo = android.content.pm.ActivityInfo().apply { packageName = "com.android.settings"; name = "Listeners" }
            },
        )
        compose.onAllNodesWithTextFirst("Allow").performClick()
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, shadowOf(app).nextStartedActivity.action)
    }

    @Test fun `with no browser, a link says so instead of crashing`() {
        shadowOf(app).checkActivities(true)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Known issues").performClick()
        assertEquals("Couldn't open known issues on this phone.", org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
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
        compose.onNodeWithText("Settings").performClick()
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
        compose.onNodeWithText("Other").performScrollTo().performClick()
        assertEquals(null, shadowOf(app).nextStartedActivity)
    }

    @Test fun `reliability cards open their Android settings pages`() {
        val manager = app.getSystemService(android.app.NotificationManager::class.java)
        manager.deleteNotificationChannel(StatusNotifier.CHANNEL_URGENT)
        manager.createNotificationChannel(
            android.app.NotificationChannel(StatusNotifier.CHANNEL_URGENT, "Urgent low", android.app.NotificationManager.IMPORTANCE_LOW),
        )
        app.getSystemService(android.media.AudioManager::class.java)
            .setStreamVolume(android.media.AudioManager.STREAM_ALARM, 0, 0)
        shadowOf(app.getSystemService(android.os.PowerManager::class.java))
            .setIgnoringBatteryOptimizations(app.packageName, false)
        shadowOf(app.packageManager).setAutoRevokeWhitelisted(false)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        manager.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_NONE)
        android.provider.Settings.Secure.putString(
            app.contentResolver,
            "enabled_notification_listeners",
            android.content.ComponentName(app, CamapsListener::class.java).flattenToString(),
        )
        try {
            app.repository.setListenerConnected(false)
            app.allowNotifications()

            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)

            compose.onNodeWithText("Review").performScrollTo().performClick()

            compose.onNodeWithText("Reconnect").performScrollTo().performClick()
            assertEquals("Reconnecting to CamAPS FX…", org.robolectric.shadows.ShadowToast.getTextOfLatestToast())

            compose.onNodeWithText("Urgent low settings").performScrollTo().performClick()
            val urgent = shadowOf(app).nextStartedActivity
            assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, urgent.action)
            assertEquals(StatusNotifier.CHANNEL_URGENT, urgent.getStringExtra(Settings.EXTRA_CHANNEL_ID))

            compose.onNodeWithText("Sound settings").performScrollTo().performClick()
            assertEquals(Settings.ACTION_SOUND_SETTINGS, shadowOf(app).nextStartedActivity.action)

            compose.onNodeWithText("Do Not Disturb").performScrollTo().performClick()
            assertEquals(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, shadowOf(app).nextStartedActivity.action)

            compose.onNodeWithText("Allow alarms").performScrollTo().performClick()
            assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, shadowOf(app).nextStartedActivity.action)

            compose.onNodeWithText("Battery settings").performScrollTo().performClick()
            assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, shadowOf(app).nextStartedActivity.action)

            compose.onNodeWithText("App settings").performScrollTo().performClick()
            assertEquals(android.content.Intent.ACTION_AUTO_REVOKE_PERMISSIONS, shadowOf(app).nextStartedActivity.action)

            compose.onNodeWithText("Live Update settings").performScrollTo().performClick()
            assertEquals(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS, shadowOf(app).nextStartedActivity.action)
        } finally {
            android.provider.Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners", "")
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(app)
        }
    }

    @Test fun `Test urgent low alarm in Settings posts a test notification or warns when notifications are off`() {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Test urgent low alarm").performClick()
        assertEquals(
            "Allow notifications first to test the alarm.",
            org.robolectric.shadows.ShadowToast.getTextOfLatestToast(),
        )

        app.allowNotifications()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.onNodeWithText("Test urgent low alarm").performClick()
        assertEquals(StatusNotifier.CHANNEL_URGENT, app.posted(StatusNotifier.ID_URGENT_TEST)!!.channelId)
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextFirst(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text) and androidx.compose.ui.test.hasClickAction())[0]
}
