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
