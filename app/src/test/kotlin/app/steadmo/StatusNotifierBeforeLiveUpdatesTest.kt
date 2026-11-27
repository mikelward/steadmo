package app.steadmo

import android.app.Application
import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import app.steadmo.core.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android 15 has no Live Updates: promotion is skipped, the notification still posts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatusNotifierBeforeLiveUpdatesTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `a low still posts, unpromoted`() {
        app.allowNotifications()
        StatusNotifier(app, clock = { MINUTE }).showStatus(GlucoseState(readings = listOf(Reading(0, 3.5))), promote = true)
        val n = app.posted(1)!!
        assertEquals("Low: 3.5 mmol/L", n.extras.getString(Notification.EXTRA_TITLE))
        assertFalse(n.extras.containsKey("android.requestPromotedOngoing"))
    }
}
