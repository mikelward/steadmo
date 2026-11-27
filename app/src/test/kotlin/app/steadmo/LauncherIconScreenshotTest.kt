package app.steadmo

import android.graphics.drawable.AdaptiveIconDrawable
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The launcher icon, its themed (monochrome) layer and the debug build's icon, drawn through the same
 * window path as the other screenshots so the PNG is byte-stable across machines.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun icon() {
        val icon = compose.activity.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertNotNull(icon.monochrome)
        val debug = compose.activity.getDrawable(R.mipmap.ic_launcher_debug) as AdaptiveIconDrawable
        assertNotNull(debug.monochrome)
        capture(this, compose, "launcher-icon.png", dark = false) {
            Surface {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        AndroidView({ ImageView(it).apply { setImageResource(R.mipmap.ic_launcher) } }, Modifier.size(144.dp))
                        AndroidView({ ImageView(it).apply { setImageDrawable(icon.monochrome) } }, Modifier.size(144.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        AndroidView({ ImageView(it).apply { setImageResource(R.mipmap.ic_launcher_debug) } }, Modifier.size(144.dp))
                        AndroidView({ ImageView(it).apply { setImageDrawable(debug.monochrome) } }, Modifier.size(144.dp))
                    }
                }
            }
        }
    }
}
