package app.steadmo

import app.steadmo.core.CamapsGoal
import android.app.Application
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class CamapsTest {
    @org.junit.After fun clearShortcuts() = CamapsShortcuts.clear()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun installCamaps(pkg: String = CAMAPS) {
        val component = ComponentName(pkg, "$pkg.MainActivity")
        val pm = shadowOf(app.packageManager)
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    @Test fun `says so when CamAPS FX is missing`() {
        assertFalse(Camaps.open(app, null))
        assertNull(shadowOf(app).nextStartedActivity)
        assertEquals("CamAPS FX isn't installed", ShadowToast.getTextOfLatestToast())
    }

    @Test fun `opens CamAPS FX`() {
        installCamaps()
        assertTrue(Camaps.open(app, null))
        assertEquals(CAMAPS, shadowOf(app).nextStartedActivity.component!!.packageName)
    }

    @Test fun `prefers the package it has seen`() {
        installCamaps()
        installCamaps("com.camdiab.fx_alert.hx.mmoll")
        Camaps.open(app, "com.camdiab.fx_alert.hx.mmoll")
        assertEquals("com.camdiab.fx_alert.hx.mmoll", shadowOf(app).nextStartedActivity.component!!.packageName)
    }

    @Test fun `opens the channel's settings`() {
        Camaps.openChannelSettings(app, CAMAPS, "info")
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(CAMAPS, intent.getStringExtra(android.provider.Settings.EXTRA_APP_PACKAGE))
        assertEquals("info", intent.getStringExtra(android.provider.Settings.EXTRA_CHANNEL_ID))
    }

    @Test fun `falls back to the app's notification settings`() {
        shadowOf(app).checkActivities(true)
        val appSettings = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        shadowOf(app.packageManager).addResolveInfoForIntent(
            appSettings,
            android.content.pm.ResolveInfo().apply {
                activityInfo = android.content.pm.ActivityInfo().apply { packageName = "com.android.settings"; name = "Settings" }
            },
        )
        Camaps.openChannelSettings(app, null, null)
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(CAMAPS, intent.getStringExtra(android.provider.Settings.EXTRA_APP_PACKAGE))
    }

    @Test fun `meal copies the grams then opens CamAPS FX`() {
        installCamaps()
        Camaps.openForMeal(app, null, 45)
        val clip = app.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals("45", clip.getItemAt(0).text)
        assertEquals("45 g copied. Enter it in CamAPS FX.", ShadowToast.getTextOfLatestToast())
        assertEquals(CAMAPS, shadowOf(app).nextStartedActivity.component!!.packageName)
    }

    @Test fun `with shortcuts on, a meal also asks for the grams to be typed`() {
        installCamaps()
        android.provider.Settings.Secure.putString(
            app.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ComponentName(app, CamapsShortcutsService::class.java).flattenToString(),
        )
        Camaps.openForMeal(app, null, 40)
        assertEquals(40, (CamapsShortcuts.pending!!.goal as CamapsGoal.Meal).grams)
        assertEquals("Entering 40 g in CamAPS FX…", ShadowToast.getTextOfLatestToast())
        assertEquals("40", app.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text)
    }

    private fun turnOnShortcuts() = android.provider.Settings.Secure.putString(
        app.contentResolver,
        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ComponentName(app, CamapsShortcutsService::class.java).flattenToString(),
    )

    @Test fun `a second meal within a minute opens CamAPS FX but enters nothing`() {
        installCamaps()
        turnOnShortcuts()
        Camaps.openForMeal(app, null, 20)
        CamapsShortcuts.clear()
        Camaps.openForMeal(app, null, 45)
        assertNull(CamapsShortcuts.pending)
        assertEquals(
            "A meal was entered under a minute ago, so Steadmo won't enter this one. 45 g is copied.",
            ShadowToast.getTextOfLatestToast(),
        )
        shadowOf(app).nextStartedActivity
        assertEquals(CAMAPS, shadowOf(app).nextStartedActivity.component!!.packageName)
    }

    @Test fun `a double tap on Boost opens CamAPS FX only, and Ease-off still works`() {
        installCamaps()
        turnOnShortcuts()
        Camaps.openForMode(app, null, CamapsGoal.BOOST)
        CamapsShortcuts.clear()
        Camaps.openForMode(app, null, CamapsGoal.BOOST)
        assertNull(CamapsShortcuts.pending)
        assertEquals("Boost was opened under a minute ago. Opening CamAPS FX only.", ShadowToast.getTextOfLatestToast())
        Camaps.openForMode(app, null, CamapsGoal.EASE_OFF)
        assertEquals(CamapsGoal.EASE_OFF, CamapsShortcuts.pending!!.goal)
    }

    @Test fun `the guard ends after a minute, or at once when nothing was entered`() {
        assertTrue(CamapsShortcuts.begin(app, CamapsGoal.Meal(20), now = 1_000))
        assertFalse(CamapsShortcuts.begin(app, CamapsGoal.Meal(20), now = 30_000))
        assertTrue(CamapsShortcuts.begin(app, CamapsGoal.Meal(20), now = 61_000))
        val failed = CamapsShortcuts.pending!!
        CamapsShortcuts.forget(app, failed)
        assertTrue(CamapsShortcuts.begin(app, CamapsGoal.Meal(20), now = 62_000))
        // Forgetting an older run leaves the newer one's guard in place.
        CamapsShortcuts.forget(app, failed)
        assertFalse(CamapsShortcuts.begin(app, CamapsGoal.Meal(20), now = 63_000))
    }

    @Test fun `Other asks for the meal entry with shortcuts on`() {
        installCamaps()
        Camaps.openForMealEntry(app, null)
        assertNull(CamapsShortcuts.pending)
        turnOnShortcuts()
        Camaps.openForMealEntry(app, null)
        assertEquals(CamapsGoal.MealEntry, CamapsShortcuts.pending!!.goal)
    }

    @Test fun `with no CamAPS FX, Other doesn't wait for it`() {
        turnOnShortcuts()
        Camaps.openForMealEntry(app, null)
        assertNull(CamapsShortcuts.pending)
    }

    @Test fun `Boost asks for its dialog only with shortcuts on`() {
        installCamaps()
        Camaps.openForMode(app, null, CamapsGoal.BOOST)
        assertNull(CamapsShortcuts.pending)
        assertEquals(CAMAPS, shadowOf(app).nextStartedActivity.component!!.packageName)
        android.provider.Settings.Secure.putString(
            app.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ComponentName(app, CamapsShortcutsService::class.java).flattenToString(),
        )
        Camaps.openForMode(app, null, CamapsGoal.BOOST)
        assertEquals(CamapsGoal.BOOST, CamapsShortcuts.pending!!.goal)
    }

    @Test fun `with no CamAPS FX, Boost doesn't wait for it or lock the guard`() {
        turnOnShortcuts()
        Camaps.openForMode(app, null, CamapsGoal.EASE_OFF)
        assertNull(CamapsShortcuts.pending)
        installCamaps()
        Camaps.openForMode(app, null, CamapsGoal.EASE_OFF)
        assertEquals(CamapsGoal.EASE_OFF, CamapsShortcuts.pending!!.goal)
    }

    @Test fun `with no CamAPS FX, a meal doesn't wait for it or lock the guard`() {
        turnOnShortcuts()
        Camaps.openForMeal(app, null, 40)
        assertNull(CamapsShortcuts.pending)
        installCamaps()
        Camaps.openForMeal(app, null, 40)
        assertEquals(40, (CamapsShortcuts.pending!!.goal as CamapsGoal.Meal).grams)
    }
}
