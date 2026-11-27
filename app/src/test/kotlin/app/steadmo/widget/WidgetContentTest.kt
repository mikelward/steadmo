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

    @Test fun `small shows just the reading`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(DpSize(180.dp, 90.dp))
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
}
