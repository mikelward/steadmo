package app.steadmo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CamapsListenerTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val listener = Robolectric.setupService(CamapsListener::class.java)

    @Before fun setUp() {
        java.io.File(app.filesDir, "readings.csv").delete()
    }

    @Test fun `reads CamAPS FX posts`() {
        listener.onNotificationPosted(camapsPost(app, "11.2", postTime = 1000))
        assertEquals(11.2, app.repository.state.value.latest!!.mmol, 1e-9)
    }

    @Test fun `ignores other apps`() {
        val before = app.repository.state.value.readings.size
        listener.onNotificationPosted(camapsPost(app, "11.2", postTime = 1000, pkg = "com.example.other"))
        assertEquals(before, app.repository.state.value.readings.size)
    }

    @Test fun `a channel change with CamAPS FX's ranking unknown changes nothing`() {
        val map = android.service.notification.NotificationListenerService.RankingMap::class.java
            .getDeclaredConstructor(Array<android.service.notification.NotificationListenerService.Ranking>::class.java)
            .apply { isAccessible = true }
            .newInstance(arrayOf<android.service.notification.NotificationListenerService.Ranking>())
        listener.onNotificationRankingUpdate(map)
        val post = camapsPost(app, "6.0", postTime = 1000)
        app.getSystemService(android.app.NotificationManager::class.java).notify(post.id, post.notification)
        val before = app.repository.state.value.camapsChannelImportance
        listener.onNotificationRankingUpdate(map)
        assertEquals(before, app.repository.state.value.camapsChannelImportance)
    }

    @Test fun `connecting marks the listener connected`() {
        listener.onListenerConnected()
        assertTrue(app.repository.state.value.listenerConnected)
    }

    @Test fun `disconnecting marks it disconnected`() {
        listener.onListenerConnected()
        listener.onListenerDisconnected()
        assertFalse(app.repository.state.value.listenerConnected)
    }
}
