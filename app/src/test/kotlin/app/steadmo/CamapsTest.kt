package app.steadmo

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

    @Test fun `meal copies the grams then opens CamAPS FX`() {
        installCamaps()
        Camaps.openForMeal(app, null, 45)
        val clip = app.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals("45", clip.getItemAt(0).text)
        assertEquals("45 g copied. Enter it in CamAPS FX.", ShadowToast.getTextOfLatestToast())
        assertEquals(CAMAPS, shadowOf(app).nextStartedActivity.component!!.packageName)
    }
}
