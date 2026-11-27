package app.steadmo

import android.app.Application
import android.app.Notification
import android.content.Intent
import android.os.Process
import android.service.notification.StatusBarNotification
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import app.steadmo.widget.WidgetActionActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DeliveryPromptsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val listener = Robolectric.setupService(CamapsListener::class.java)

    @Before fun setUp() = app.allowNotifications()

    private fun post(pkg: String, title: String, text: String, postTime: Long = System.currentTimeMillis()) {
        val notification = Notification.Builder(app, "test")
            .setSmallIcon(R.drawable.ic_trend_flat)
            .setContentTitle(title)
            .setContentText(text)
            .build()
        listener.onNotificationPosted(StatusBarNotification(pkg, pkg, 7, null, 0, 0, 0, notification, Process.myUserHandle(), postTime))
    }

    @Test fun `a rider nearby suggests a pre-bolus that opens the carbs box`() {
        post("com.deliveroo.orderapp", "Your order is on its way", "Sam is heading to you")
        assertNull(app.posted(6))
        post("com.deliveroo.orderapp", "Almost there!", "Sam is around the corner")
        val n = app.posted(6)!!
        assertEquals("Food nearly here", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Deliveroo says your rider is nearby. Bolus now?", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(StatusNotifier.CHANNEL_REMINDERS, n.channelId)
        assertEquals("Bolus", n.actions.single().title)
        val bolusIntent = shadowOf(n.actions.single().actionIntent).savedIntent
        assertEquals("steadmo://widget/camaps/meal", bolusIntent.dataString)
        assertTrue(bolusIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        Robolectric.buildActivity(WidgetActionActivity::class.java, bolusIntent).create()
        assertNull(app.posted(6))
        assertTrue(DeliveryPrompts.lastSeen!!.startsWith("Deliveroo: "))
    }

    @Test fun `delivery notifications read extras without inflating custom layouts`() {
        val notification = Notification.Builder(app, "test")
            .setSmallIcon(R.drawable.ic_trend_flat)
            .setContentTitle("Almost there!")
            .setContentText("Your rider is around the corner")
            .build()
        @Suppress("DEPRECATION")
        notification.contentView = object : RemoteViews(app.packageName, R.layout.test_camaps_notification) {
            override fun apply(context: android.content.Context?, parent: android.view.ViewGroup?): android.view.View {
                throw AssertionError("delivery notification layout must not be inflated")
            }
        }
        val sbn = StatusBarNotification(
            "com.deliveroo.orderapp", "com.deliveroo.orderapp", 8, null, 0, 0, 0,
            notification, Process.myUserHandle(), System.currentTimeMillis(),
        )
        listener.onNotificationPosted(sbn)
        assertEquals("Food nearly here", app.posted(6)!!.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test fun `once per order, and not before it leaves`() {
        post("com.ubercab.eats", "Preparing your order", "The restaurant is making your food")
        assertNull(app.posted(6))
        post("com.ubercab.eats", "Your driver is 3 mins away", "")
        app.getSystemService(android.app.NotificationManager::class.java).cancel(6)
        post("com.ubercab.eats", "Almost there!", "")
        assertNull(app.posted(6))
    }

    @Test fun `an old post replayed on reconnect doesn't prompt`() {
        post("com.dd.doordash", "Your Dasher is approaching", "", postTime = System.currentTimeMillis() - 3 * 60 * MINUTE)
        assertNull(app.posted(6))
    }

    @Test fun `other apps are ignored`() {
        post("com.example.chat", "Your rider is nearby", "")
        assertNull(app.posted(6))
    }
}
