package app.steadmo

import android.graphics.drawable.AdaptiveIconDrawable
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
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
 * The launcher icon and its themed (monochrome) layer, drawn through the same
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
        capture(this, compose, "launcher-icon.png", dark = false) {
            Surface {
                Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    AndroidView({ ImageView(it).apply { setImageResource(R.mipmap.ic_launcher) } }, Modifier.size(144.dp))
                    AndroidView({ ImageView(it).apply { setImageDrawable(icon.monochrome) } }, Modifier.size(144.dp))
                }
            }
        }
    }
}
