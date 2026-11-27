package app.steadmo

import android.app.Notification
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationDumpTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `reads visible text in layout order and skips hidden views`() {
        val dump = NotificationDump.of(context, camapsPost(context, "11.2", postTime = 1000))
        assertEquals(listOf("CamAPS FX", "Auto mode", "On", "11.2", "mmol/L"), dump.viewTexts)
        assertEquals(CAMAPS, dump.packageName)
        assertEquals(1000L, dump.postTime)
    }

    @Test
    fun `describes images so CamAPS's arrow can be mapped`() {
        val dump = NotificationDump.of(context, camapsPost(context, "11.2", postTime = 0))
        assertEquals(1, dump.images.size)
        assertTrue(dump.images[0], dump.images[0].startsWith("arrow rotation=0.0"))
        assertTrue(dump.images[0], dump.images[0].contains("pixels="))
        assertTrue(dump.describe().contains("views: CamAPS FX | Auto mode | On | 11.2 | mmol/L"))
    }

    @Test
    fun `falls back to extras when there is no custom layout`() {
        val notification = Notification.Builder(context, "test")
            .setSmallIcon(R.drawable.ic_trend_flat)
            .setContentTitle("6.4 mmol/L")
            .setContentText("Auto mode: Off")
            .setStyle(Notification.InboxStyle().addLine("line one"))
            .build()
        val sbn = StatusBarNotification(CAMAPS, CAMAPS, 2, null, 0, 0, 0, notification, Process.myUserHandle(), 0)
        val dump = NotificationDump.of(context, sbn)
        assertEquals(emptyList<String>(), dump.viewTexts)
        assertTrue(dump.extrasTexts.containsAll(listOf("6.4 mmol/L", "Auto mode: Off", "line one")))
    }
}
