package app.steadmo.widget

import app.steadmo.CamapsShortcuts
import app.steadmo.CamapsShortcutsService
import app.steadmo.core.CamapsGoal
import android.app.Application
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.steadmo.CAMAPS
import app.steadmo.Disclaimer
import app.steadmo.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WidgetActionActivityTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Before fun installCamaps() {
        val component = ComponentName(CAMAPS, "$CAMAPS.MainActivity")
        shadowOf(app.packageManager).addActivityIfNotPresent(component)
        shadowOf(app.packageManager).addIntentFilterForActivity(
            component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    private fun tap(path: String): WidgetActionActivity {
        val intent = Intent(app, WidgetActionActivity::class.java).setData(Uri.parse("steadmo://widget/$path"))
        return Robolectric.buildActivity(WidgetActionActivity::class.java, intent).create().get()
    }

    @Test fun `a meal button copies the grams and opens CamAPS FX`() {
        Disclaimer.accept(app)
        val activity = tap("meal/45")
        assertEquals("45", app.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text)
        assertEquals(CAMAPS, shadowOf(activity).nextStartedActivity.component!!.packageName)
        assertTrue(activity.isFinishing)
    }

    @Test fun `Boost, Ease-off and Other open CamAPS FX`() {
        Disclaimer.accept(app)
        listOf("camaps/boost", "camaps/ease-off", "camaps/meal", "meal/x").forEach {
            val activity = tap(it)
            assertEquals(it, CAMAPS, shadowOf(activity).nextStartedActivity.component!!.packageName)
        }
    }

    @Test fun `with shortcuts on, Boost and Ease-off ask CamAPS FX for their dialogs`() {
        Disclaimer.accept(app)
        android.provider.Settings.Secure.putString(
            app.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            android.content.ComponentName(app, CamapsShortcutsService::class.java).flattenToString(),
        )
        tap("camaps/boost")
        assertEquals(CamapsGoal.BOOST, CamapsShortcuts.pending!!.goal)
        tap("camaps/ease-off")
        assertEquals(CamapsGoal.EASE_OFF, CamapsShortcuts.pending!!.goal)
        CamapsShortcuts.clear()
    }

    @Test fun `before the disclaimer, every button opens Steadmo`() {
        val activity = tap("meal/45")
        assertEquals(MainActivity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
    }

    @Test fun `an unknown button opens Steadmo`() {
        Disclaimer.accept(app)
        val activity = tap("nonsense")
        assertEquals(MainActivity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
    }
}
