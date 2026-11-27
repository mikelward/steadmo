package app.steadmo

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CamapsEntryPointsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `lists exported screens and the intents they answer`() {
        val main = ActivityInfo().apply { packageName = CAMAPS; name = "$CAMAPS.MainActivity"; exported = true }
        val meal = ActivityInfo().apply { packageName = CAMAPS; name = "$CAMAPS.MealActivity"; exported = false }
        shadowOf(app.packageManager).installPackage(
            PackageInfo().apply {
                packageName = CAMAPS
                versionName = "1.4"
                activities = arrayOf(main, meal)
                applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = CAMAPS }
            },
        )
        shadowOf(app.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).setPackage(CAMAPS),
            android.content.pm.ResolveInfo().apply { activityInfo = main },
        )

        val text = CamapsEntryPoints.describe(app, CAMAPS)
        assertTrue(text, text.startsWith("version: 1.4"))
        assertTrue(text, text.contains("activities: 2, exported: 1"))
        assertTrue(text, text.contains("exported: $CAMAPS.MainActivity"))
        assertTrue(text, text.contains("answers android.intent.action.MAIN: $CAMAPS.MainActivity"))
    }

    @Test fun `says when CamAPS FX isn't there`() {
        assertEquals("CamAPS FX not seen", CamapsEntryPoints.describe(app, null))
        assertEquals("com.example.none not installed or not visible", CamapsEntryPoints.describe(app, "com.example.none"))
    }
}
