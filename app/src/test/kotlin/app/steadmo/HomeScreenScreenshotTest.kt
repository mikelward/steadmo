package app.steadmo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The home screen in each state it can be in, light and dark. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun home(state: GlucoseState, listener: Boolean = true, notifications: Boolean = true, now: Long = Samples.NOW): @Composable () -> Unit = {
        HomeScreen(state, listener, notifications, {}, {}, {}, {}, clock = { now }, ticking = false)
    }

    @Test fun rising() = both("home-rising") { home(Samples.rising()) }
    @Test fun fallingFast() = both("home-falling-fast") { home(Samples.fallingFast()) }
    @Test fun stale() = both("home-stale") { home(Samples.rising(), now = Samples.NOW + 20 * MINUTE) }
    @Test fun belowSensor() = both("home-below-sensor") { home(Samples.belowSensor()) }
    @Test fun minimizeCamaps() = both("home-minimize-camaps") {
        home(Samples.rising().copy(camapsChannelId = "info", camapsChannelImportance = android.app.NotificationManager.IMPORTANCE_LOW))
    }
    @Test fun setup() = both("home-setup") { home(GlucoseState(), listener = false, notifications = false) }

    private fun both(name: String, content: () -> @Composable () -> Unit) {
        capture(this, compose, "$name-light.png", dark = false, content())
        capture(this, compose, "$name-dark.png", dark = true, content())
    }
}

/** Renders [content] in the activity's window and writes it under src/test/snapshots/images. */
fun capture(
    @Suppress("UNUSED_PARAMETER") test: Any,
    compose: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, out ComponentActivity>,
    name: String,
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    compose.activity.setContent { SteadmoTheme(dark = dark) { content() } }
    compose.waitForIdle()
    // Always drawn, so every test run exercises the drawing code; Roborazzi
    // writes the PNG only with -Proborazzi.test.record=true.
    val root = compose.activity.window.decorView.rootView
    root.measure(
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
    )
    root.layout(0, 0, 1080, 2400)
    val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
    root.draw(Canvas(bitmap))
    bitmap.captureRoboImage(filePath = "src/test/snapshots/images/$name")
}
