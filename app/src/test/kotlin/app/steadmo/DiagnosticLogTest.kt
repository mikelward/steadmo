package app.steadmo

import androidx.test.core.app.ApplicationProvider
import com.mikelward.androidlog.DebugLog
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiagnosticLogTest {
    private val app = ApplicationProvider.getApplicationContext<SteadmoApp>()

    @Test fun `log lines reach the persisted file`() {
        val log = DebugLog()
        val sink = installDiagnosticSinks(log, app, installCrashHandler = false)
        log.warning("diagnostic test marker %s", 42)
        sink.awaitIdle()
        val text = app.cacheDir.walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }
        assertTrue(text, text.contains("diagnostic test marker 42"))
    }
}
