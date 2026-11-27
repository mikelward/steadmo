package app.steadmo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Unit tests run the debug variant: it must say so in its package, label and icon. */
@RunWith(RobolectricTestRunner::class)
class DebugBuildTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `debug builds are app_steadmo_debug, labelled and badged`() {
        assertEquals("app.steadmo.debug", context.packageName)
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals("Steadmo Debug", info.loadLabel(context.packageManager).toString())
        assertEquals(R.mipmap.ic_launcher_debug, info.icon)
    }
}
