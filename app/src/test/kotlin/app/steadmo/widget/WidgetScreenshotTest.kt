package app.steadmo.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import app.steadmo.Samples
import com.github.takahirom.roborazzi.captureRoboImage
import java.text.SimpleDateFormat
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The widget at a small, medium and full-page size, light and dark. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetScreenshotTest {
    private val fmt = SimpleDateFormat("HH:mm").apply { timeZone = TimeZone.getTimeZone("UTC") }

    @Test fun sizes() {
        val model = WidgetModel.of(Samples.rising(), true, Samples.NOW, fmt)
        listOf(false, true).forEach { dark ->
            val theme = if (dark) "dark" else "light"
            // The minimum size in steadmo_widget_info.xml.
            capture("widget-small-$theme.png", DpSize(250.dp, 90.dp), dark) { WidgetContent(model, Samples.NOW) }
            capture("widget-medium-$theme.png", DpSize(320.dp, 240.dp), dark) { WidgetContent(model, Samples.NOW) }
            capture("widget-full-$theme.png", DpSize(360.dp, 480.dp), dark) { WidgetContent(model, Samples.NOW) }
            // The narrowest width at full height: meal buttons in two rows.
            capture("widget-narrow-$theme.png", DpSize(250.dp, 480.dp), dark) { WidgetContent(model, Samples.NOW) }
        }
    }

    private fun capture(name: String, size: DpSize, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
        val context = base.createConfigurationContext(config)
        val views = runBlocking {
            GlanceRemoteViews().compose(context, size) { GlanceTheme { content() } }.remoteViews
        }
        val density = context.resources.displayMetrics.density
        val w = (size.width.value * density).toInt()
        val h = (size.height.value * density).toInt()
        val root = FrameLayout(context)
        val view = views.apply(context, root)
        root.addView(view)
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        bitmap.captureRoboImage(filePath = "src/test/snapshots/images/$name")
    }
}
