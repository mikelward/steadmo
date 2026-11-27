package app.steadmo

import android.app.Application
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import app.steadmo.widget.WidgetActionActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LauncherShortcutsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `the app publishes meal and Boost shortcuts that go through the widget's actions`() {
        // SteadmoApp.onCreate has already published them.
        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(app).sortedBy { it.rank }
        assertEquals(listOf("20 g", "40 g", "45 g", "Boost"), shortcuts.map { it.shortLabel.toString() })
        assertEquals("Meal, 20 g", shortcuts[0].longLabel.toString())
        val intent = shortcuts[3].intent
        assertEquals(WidgetActionActivity::class.java.name, intent.component!!.className)
        assertEquals("steadmo://widget/camaps/boost", intent.dataString)
        assertEquals("steadmo://widget/meal/45", shortcuts[2].intent.dataString)
    }
}
