package app.steadmo

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import app.steadmo.core.CamapsGoal
import app.steadmo.core.CamapsTask
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class CamapsShortcutsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val service = Robolectric.setupService(CamapsShortcutsService::class.java)

    @After fun clear() = CamapsShortcuts.clear()

    private fun node(
        text: String? = null,
        hint: String? = null,
        clickable: Boolean = false,
        editable: Boolean = false,
        vararg children: AccessibilityNodeInfo,
    ): AccessibilityNodeInfo = AccessibilityNodeInfo.obtain().apply {
        this.text = text
        hintText = hint
        isClickable = clickable
        isEditable = editable
        isVisibleToUser = true
        packageName = CAMAPS
        children.forEach { shadowOf(this).addChild(it) }
    }

    private fun event(pkg: String = CAMAPS, type: Int = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) =
        AccessibilityEvent(type).apply { packageName = pkg }

    private fun showing(root: AccessibilityNodeInfo?) = shadowOf(service).setRootInActiveWindow(root)

    private fun turnOn() = Settings.Secure.putString(
        app.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        "com.example/.Other:" + ComponentName(app, CamapsShortcutsService::class.java).flattenToString(),
    )

    @Test fun `is on only when the user turned it on`() {
        assertFalse(CamapsShortcuts.enabled(app))
        turnOn()
        assertTrue(CamapsShortcuts.enabled(app))
    }

    @Test fun `taps to the meal screen, types the grams and presses nothing else`() {
        val meal = node(text = "Meal", clickable = true)
        showing(node(null, null, false, false, node(text = "Boost", clickable = true), meal))
        CamapsShortcuts.start(CamapsGoal.Meal(45))
        service.onAccessibilityEvent(event())
        assertEquals(listOf(AccessibilityNodeInfo.ACTION_CLICK), shadowOf(meal).performedActions)

        val carbs = node(hint = "Carbs (g)", editable = true)
        val confirm = node(text = "Confirm", clickable = true)
        val deliver = node(text = "Deliver", clickable = true)
        showing(node(null, null, false, false, carbs, confirm, deliver))
        service.onAccessibilityEvent(event())

        val typed = shadowOf(carbs).performedActionsWithArgs.last()
        assertEquals(AccessibilityNodeInfo.ACTION_SET_TEXT, typed.first)
        assertEquals("45", typed.second.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE))
        // Prefill only: Confirm stays with the user.
        assertNull(CamapsShortcuts.pending)
        val shown = node(hint = "Carbs (g)", editable = true).apply { text = "45" }
        showing(node(null, null, false, false, shown, confirm, deliver))
        service.onAccessibilityEvent(event(type = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
        assertTrue(shadowOf(confirm).performedActions.isEmpty())
        assertTrue(shadowOf(deliver).performedActions.isEmpty())
        assertEquals("45 g entered. Check it, then confirm in CamAPS FX.", ShadowToast.getTextOfLatestToast())
    }

    @Test fun `once the grams are typed, the same meal can't be sent again for a minute`() {
        val carbs = node(hint = "Carbs", editable = true)
        showing(node(null, null, false, false, carbs, node(text = "Confirm", clickable = true)))
        assertTrue(CamapsShortcuts.begin(app, CamapsGoal.Meal(20)))
        service.onAccessibilityEvent(event())
        assertNull(CamapsShortcuts.pending)
        assertFalse(CamapsShortcuts.begin(app, CamapsGoal.Meal(20)))
    }

    @Test fun `when typing fails, the meal can be tried again`() {
        val carbs = node(hint = "Carbs", editable = true)
        shadowOf(carbs).setOnPerformActionListener { _, _ -> false }
        showing(node(null, null, false, false, carbs))
        assertTrue(CamapsShortcuts.begin(app, CamapsGoal.Meal(20)))
        service.onAccessibilityEvent(event())
        assertNull(CamapsShortcuts.pending)
        assertTrue(CamapsShortcuts.begin(app, CamapsGoal.Meal(20)))
    }

    @Test fun `Boost taps CamAPS FX's Boost and leaves its dialog to the user`() {
        val boost = node(text = "Boost", clickable = true)
        showing(node(null, null, false, false, boost, node(text = "Ease-off", clickable = true)))
        CamapsShortcuts.start(CamapsGoal.BOOST)
        service.onAccessibilityEvent(event())
        assertEquals(listOf(AccessibilityNodeInfo.ACTION_CLICK), shadowOf(boost).performedActions)
        assertNull(CamapsShortcuts.pending)
        assertEquals("Set Boost in CamAPS FX, then confirm.", ShadowToast.getTextOfLatestToast())
    }

    @Test fun `says so when Ease-off can't be found`() {
        showing(node(null, null, false, false, node(hint = "Value", editable = true)))
        CamapsShortcuts.start(CamapsGoal.EASE_OFF)
        service.onAccessibilityEvent(event())
        assertEquals("Couldn't find Ease-off in CamAPS FX.", ShadowToast.getTextOfLatestToast())
    }

    @Test fun `Other taps through to the carbs box and leaves the typing to the user`() {
        val carbs = node(hint = "Carbs", editable = true)
        showing(node(null, null, false, false, carbs))
        CamapsShortcuts.start(CamapsGoal.MealEntry)
        service.onAccessibilityEvent(event())
        assertTrue(shadowOf(carbs).performedActions.isEmpty())
        assertNull(CamapsShortcuts.pending)
        assertEquals("Enter the grams in CamAPS FX, then confirm.", ShadowToast.getTextOfLatestToast())
    }

    @Test fun `says so when the meal entry can't be found for Other`() {
        showing(node(null, null, false, false, node(hint = "Insulin", editable = true)))
        CamapsShortcuts.start(CamapsGoal.MealEntry)
        service.onAccessibilityEvent(event())
        assertEquals("Couldn't find CamAPS FX's meal entry.", ShadowToast.getTextOfLatestToast())
    }

    @Test fun `ignores other apps and does nothing without a meal`() {
        val meal = node(text = "Meal", clickable = true)
        showing(node(null, null, false, false, meal))
        service.onAccessibilityEvent(event())
        CamapsShortcuts.start(CamapsGoal.Meal(20))
        service.onAccessibilityEvent(event(pkg = "com.example.other"))
        assertTrue(shadowOf(meal).performedActions.isEmpty())
    }

    @Test fun `says so when it gives up`() {
        showing(node(null, null, false, false, node(hint = "Insulin", editable = true)))
        CamapsShortcuts.start(CamapsGoal.Meal(40))
        service.onAccessibilityEvent(event())
        assertNull(CamapsShortcuts.pending)
        assertEquals("Couldn't find CamAPS FX's meal entry. 40 g is copied to paste.", ShadowToast.getTextOfLatestToast())
    }

    @Test fun `gives up on time when the screen stops changing`() {
        showing(node(null, null, false, false, node(text = "Graph")))
        CamapsShortcuts.start(CamapsGoal.Meal(20))
        service.onAccessibilityEvent(event(type = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
        assertTrue(CamapsShortcuts.pending != null)
        ShadowLooper.idleMainLooper(CamapsTask.TIMEOUT_MILLIS + 1, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertNull(CamapsShortcuts.pending)
    }

    @Test fun `never reads or taps another app in front`() {
        val other = node(text = "Meal", clickable = true).apply { packageName = "com.example.chat" }
        showing(other)
        CamapsShortcuts.start(CamapsGoal.Meal(20), now = 0)
        val task = CamapsShortcuts.pending!!
        service.step(task, newScreen = true, now = 1)
        assertTrue(shadowOf(other).performedActions.isEmpty())
        assertSame(task, CamapsShortcuts.pending)
        service.step(task, newScreen = true, now = CamapsTask.TIMEOUT_MILLIS + 1)
        assertNull(CamapsShortcuts.pending)
        assertTrue(shadowOf(other).performedActions.isEmpty())
    }

    @Test fun `gives up when the screen can't be read`() {
        showing(null)
        CamapsShortcuts.start(CamapsGoal.Meal(20), now = 0)
        val fill = CamapsShortcuts.pending!!
        service.step(fill, newScreen = true, now = 1)
        assertSame(fill, CamapsShortcuts.pending)
        service.step(fill, newScreen = true, now = CamapsTask.TIMEOUT_MILLIS + 1)
        assertNull(CamapsShortcuts.pending)
    }

    @Test fun `says so when a tap or the typing fails`() {
        val meal = node(text = "Meal", clickable = true)
        shadowOf(meal).setOnPerformActionListener { _, _ -> false }
        showing(node(null, null, false, false, meal))
        CamapsShortcuts.start(CamapsGoal.Meal(20))
        service.onAccessibilityEvent(event())
        assertNull(CamapsShortcuts.pending)

        val carbs = node(hint = "Carbs", editable = true)
        shadowOf(carbs).setOnPerformActionListener { _, _ -> false }
        showing(node(null, null, false, false, carbs))
        CamapsShortcuts.start(CamapsGoal.Meal(20))
        service.onAccessibilityEvent(event())
        assertNull(CamapsShortcuts.pending)
        assertEquals("Couldn't find CamAPS FX's meal entry. 20 g is copied to paste.", ShadowToast.getTextOfLatestToast())
    }

    @Test fun `a newer meal replaces an older one`() {
        CamapsShortcuts.start(CamapsGoal.Meal(20))
        val old = CamapsShortcuts.pending!!
        CamapsShortcuts.start(CamapsGoal.Meal(40))
        CamapsShortcuts.clear(old)
        assertEquals(40, (CamapsShortcuts.pending!!.goal as CamapsGoal.Meal).grams)
        service.step(old, newScreen = true)
        assertEquals(40, (CamapsShortcuts.pending!!.goal as CamapsGoal.Meal).grams)
    }

    @Test fun `skips views off screen`() {
        val offscreen = node(text = "Meal", clickable = true).apply { isVisibleToUser = false }
        val root = node(null, null, false, false, offscreen, node(text = "x"))
        assertEquals("x", CamapsShortcuts.read(root).children.single().text)
        assertEquals("x", CamapsShortcuts.find(root, listOf(0), CamapsShortcuts.read(root))!!.text)
    }

    @Test fun `finds views by the path it read`() {
        val leaf = node(text = "x")
        val root = node(null, null, false, false, node(null, null, false, false, leaf))
        val screen = CamapsShortcuts.read(root)
        assertEquals("x", CamapsShortcuts.find(root, listOf(0, 0), screen)!!.text)
        assertNull(CamapsShortcuts.find(root, listOf(1), screen))
        assertEquals("x", CamapsShortcuts.read(root).children[0].children[0].text)
    }

    @Test fun `never acts on a view that changed after it was read`() {
        val leaf = node(text = "Meal", clickable = true)
        val root = node(null, null, false, false, leaf)
        val screen = CamapsShortcuts.read(root)
        leaf.text = "Deliver"
        assertNull(CamapsShortcuts.find(root, listOf(0), screen))
    }

    @Test fun `an empty field's hint isn't read as what's typed`() {
        val empty = node(text = "Value", hint = "Value", editable = true).apply { isShowingHintText = true }
        assertNull(CamapsShortcuts.read(empty).text)
        assertEquals("Value", CamapsShortcuts.read(empty).hint)
    }

    @Test fun `logs its lifecycle`() {
        service.onServiceConnected()
        service.onInterrupt()
        assertFalse(service.onUnbind(null))
    }
}
