package app.steadmo

import android.app.Activity
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.steadmo.core.SteadmoLog
import com.mikelward.androidlog.DebugLog
import com.mikelward.androidlog.android.ShareOutcome
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BugReportTest {
    private val app = ApplicationProvider.getApplicationContext<SteadmoApp>()

    private fun facts(state: GlucoseState) = BugReport.Facts(
        version = "21+abc", device = "Google Pixel 9", sdk = 36,
        listenerAllowed = true, notificationsAllowed = false, state = state, now = Samples.NOW + 30_000,
    )

    @Test fun `the section states versions, access, CamAPS, readings and the last notification`() {
        val state = Samples.rising().copy(camapsChannelId = "info", camapsChannelImportance = NotificationManager.IMPORTANCE_LOW)
        val text = BugReport.section(facts(state), listOf("line one", "line two"))
        assertTrue(text, text.startsWith("Steadmo 21+abc on Google Pixel 9, Android SDK 36"))
        assertTrue(text, text.contains("Notification access: yes; notifications: no; listener connected: yes"))
        assertTrue(text, text.contains("CamAPS FX: $CAMAPS; channel info, importance silent"))
        assertTrue(text, text.contains("latest 9.4 mmol/L, 30s old"))
        assertTrue(text, text.contains("Trend: UP, 0.12 mmol/L/min"))
        assertTrue(text, text.contains("Auto mode: On"))
        assertTrue(text, text.contains("--- last CamAPS FX notification ---\npackage: $CAMAPS"))
        assertTrue(text, text.endsWith("--- log ---\nline one\nline two"))
    }

    @Test fun `CamAPS FX's screens are listed when known`() {
        val text = BugReport.section(facts(GlucoseState()).copy(camapsEntryPoints = "exported: x.Main"), emptyList())
        assertTrue(text, text.contains("--- CamAPS FX screens other apps can open ---\nexported: x.Main"))
    }

    @Test fun `an empty state says so`() {
        val text = BugReport.section(facts(GlucoseState()), emptyList())
        assertTrue(text, text.contains("CamAPS FX: not seen; channel unknown, importance unknown"))
        assertTrue(text, text.contains("Readings: none"))
        assertTrue(text, text.contains("Trend: none"))
        assertFalse(text, text.contains("last CamAPS FX notification"))
    }

    @Test fun `importance names`() {
        listOf(
            NotificationManager.IMPORTANCE_NONE to "off",
            NotificationManager.IMPORTANCE_MIN to "minimized",
            NotificationManager.IMPORTANCE_DEFAULT to "default",
            NotificationManager.IMPORTANCE_HIGH to "high",
            42 to "42",
        ).forEach { (value, name) ->
            val text = BugReport.section(facts(GlucoseState(camapsChannelImportance = value)), emptyList())
            assertTrue(text, text.contains("importance $name"))
        }
    }

    @Test fun `facts read the app's permissions`() {
        val f = BugReport.facts(app, GlucoseState(), now = 5)
        assertFalse(f.listenerAllowed)
        assertFalse(f.notificationsAllowed)
        assertEquals(5L, f.now)
    }

    @Test fun `sharing opens the share sheet with the report, and copies it`() {
        SteadmoLog.event("report test marker")
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val outcome = BugReport.share(activity)
        assertEquals(ShareOutcome.SHARED, outcome)
        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        val text = send.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(text, text.contains("Steadmo "))
        assertTrue(text, text.contains("report test marker"))
        assertEquals("Steadmo bug report", send.getStringExtra(Intent.EXTRA_SUBJECT))
        val clip = app.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals(text, clip.getItemAt(0).text.toString())
    }

    @Test fun `a screenshot the provider refuses is dropped, not the report`() {
        val file = File.createTempFile("shot", ".png")
        val uri = BugReport.screenshotUri(file, DebugLog()) { throw IllegalArgumentException("not ours") }
        assertNull(uri)
        assertFalse(file.exists())
        val ok = Uri.parse("content://x/y")
        assertEquals(ok, BugReport.screenshotUri(File.createTempFile("shot", ".png"), DebugLog()) { ok })
    }
}
