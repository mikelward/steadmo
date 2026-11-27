package app.steadmo.widget

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.steadmo.Samples
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetUpdatesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `with no widget placed, a new state does nothing`() = runBlocking {
        WidgetUpdates.onState(context, Samples.rising(), Samples.NOW)
    }

    @Test fun `the stale redraw succeeds`() = runBlocking {
        val worker = TestListenableWorkerBuilder<WidgetUpdates.StaleWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }

    @Test fun `the graph is drawn at the asked size`() {
        val colors = WidgetGraph.Colors(Color.RED, Color.GREEN, Color.YELLOW, Color.LTGRAY, Color.GRAY, Color.BLACK)
        val bitmap = WidgetGraph.draw(Samples.rising().readings, Samples.NOW, 600, 200, 2f, colors)
        assertEquals(600, bitmap.width)
        assertEquals(200, bitmap.height)
        assertEquals(1, WidgetGraph.draw(emptyList(), Samples.NOW, 0, 0, 1f, colors).width)
    }
}
