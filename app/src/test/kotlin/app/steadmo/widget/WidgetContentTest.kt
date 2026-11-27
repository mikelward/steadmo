package app.steadmo.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import app.steadmo.Samples
import java.text.SimpleDateFormat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetContentTest {
    private val fmt = SimpleDateFormat("HH:mm")
    private fun showing() = WidgetModel.of(Samples.rising(), true, Samples.NOW, fmt)

    @Test fun `full size shows the reading, graph, Boost, Ease-off and meals`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(DpSize(320.dp, 420.dp))
        provideComposable { GlanceTheme { WidgetContent(showing(), Samples.NOW) } }
        onNode(hasText("9.4")).assertExists()
        onNode(hasContentDescription("Rising")).assertExists()
        onNode(hasText("Auto mode on")).assertExists()
        onNode(hasContentDescription("Glucose over the last 3 hours")).assertExists()
        onNode(hasText("Boost")).assertExists()
        onNode(hasText("Ease-off")).assertExists()
        onNode(hasText("45 g")).assertExists()
        onNode(hasText("Other")).assertExists()
    }

    // Through WidgetRoot, as provideGlance renders it: Glance has no lifecycle owner,
    // so a lifecycle-aware collector would throw here.
    @Test fun `the real entry point renders`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        app.steadmo.Disclaimer.accept(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(DpSize(320.dp, 420.dp))
        val states = kotlinx.coroutines.flow.MutableStateFlow(Samples.rising())
        provideComposable { WidgetRoot(states, ApplicationProvider.getApplicationContext(), { Samples.NOW }) }
        onNode(hasText("9.4")).assertExists()
    }

    @Test fun `narrow and tall still shows every meal button`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(DpSize(250.dp, 420.dp))
        provideComposable { GlanceTheme { WidgetContent(showing(), Samples.NOW) } }
        listOf("20 g", "40 g", "45 g", "Other", "Boost", "Ease-off").forEach { onNode(hasText(it)).assertExists() }
    }

    @Test fun `small shows just the reading`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(DpSize(250.dp, 90.dp))
        provideComposable { GlanceTheme { WidgetContent(showing(), Samples.NOW) } }
        onNode(hasText("9.4")).assertExists()
        onNode(hasContentDescription("Glucose over the last 3 hours")).assertDoesNotExist()
        onNode(hasText("Boost")).assertDoesNotExist()
    }

    @Test fun `stale says so`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(DpSize(250.dp, 120.dp))
        val model = WidgetModel.of(Samples.rising(), true, Samples.NOW + 20 * 60_000, fmt)
        provideComposable { GlanceTheme { WidgetContent(model, Samples.NOW + 20 * 60_000) } }
        onNode(hasText("Auto mode on at ${fmt.format(Samples.NOW)} · No new reading for over 10 min")).assertExists()
    }

    @Test fun `before the disclaimer, only a prompt`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable { GlanceTheme { WidgetContent(WidgetModel.NeedsDisclaimer, Samples.NOW) } }
        onNode(hasText("Open Steadmo to accept the disclaimer.")).assertExists()
        onNode(hasText("9.4")).assertDoesNotExist()
    }

    @Test fun `waiting says so`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable { GlanceTheme { WidgetContent(WidgetModel.Waiting, Samples.NOW) } }
        onNode(hasText("Waiting for CamAPS FX.")).assertExists()
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun `widget graph treats 70 and 180 mg per dL as in range`() {
        val colors = WidgetGraph.Colors(
            low = 0xFFFF0000.toInt(), inRange = 0xFF00FF00.toInt(), high = 0xFF0000FF.toInt(),
            band = 0x00000000, grid = 0x00000000, label = 0x00000000,
        )
        val seventy = app.steadmo.core.Reading(Samples.NOW, 70.0 / app.steadmo.core.MGDL_PER_MMOL)
        val bitmap = WidgetGraph.draw(listOf(seventy), Samples.NOW, 200, 100, 1f, colors)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        org.junit.Assert.assertTrue("70 mg/dL should draw in-range green", pixels.any { it == colors.inRange })
        org.junit.Assert.assertFalse("70 mg/dL should not draw low red", pixels.any { it == colors.low })
    }
}
