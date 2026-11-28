package app.steadmo

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

@RunWith(RobolectricTestRunner::class)
class UpdateCheckerTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestSteadmoApp>()

    @After fun tearDown() {
        app.testUpdateChecker = null
    }

    @Test fun `fetchLatestReleaseLocation sends HEAD without following redirects and reads Location`() {
        val method = AtomicReference<String>()
        val userAgent = AtomicReference<String>()
        val hits = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/releases/latest") { exchange ->
                hits.incrementAndGet()
                method.set(exchange.requestMethod)
                userAgent.set(exchange.requestHeaders.getFirst("User-Agent"))
                exchange.responseHeaders.add("Location", "https://github.com/mikelward/steadmo/releases/tag/v105")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            server.createContext("/unexpected") { exchange ->
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
            server.start()
            val base = "http://127.0.0.1:${server.address.port}"

            val location = UpdateChecker.fetchLatestReleaseLocation("$base/releases/latest")
            assertEquals("HEAD", method.get())
            assertTrue(userAgent.get().orEmpty().startsWith("Steadmo/"))
            assertEquals(1, hits.get())
            assertEquals("https://github.com/mikelward/steadmo/releases/tag/v105", location)

            assertNull(UpdateChecker.fetchLatestReleaseLocation("$base/unexpected"))
        } finally {
            server.stop(0)
        }
    }

    @Test fun `sets updateAvailable based on latest tag and preserves state on failures`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var nextLocation: () -> String? = { "https://github.com/mikelward/steadmo/releases/tag/v105" }
        try {
            val checker = UpdateChecker(
                scope = scope,
                currentVersionCode = 100,
                fetchLocation = { nextLocation() },
                ioDispatcher = Dispatchers.Unconfined,
            )
            assertFalse(checker.updateAvailable.value)

            checker.check()
            assertTrue(checker.updateAvailable.value)

            // Network error keeps the previously known state instead of flickering off.
            nextLocation = { throw IOException("offline") }
            checker.check()
            assertTrue(checker.updateAvailable.value)

            // Missing tag also leaves the previously known state alone.
            nextLocation = { null }
            checker.check()
            assertTrue(checker.updateAvailable.value)

            // A valid response at or below current clears the indicator.
            nextLocation = { "https://github.com/mikelward/steadmo/releases/tag/v100" }
            checker.check()
            assertFalse(checker.updateAvailable.value)
        } finally {
            scope.cancel()
        }
    }

    @Test fun `skipNextOpen skips only the next open and check deduplicates in-flight calls`() {
        val queued = ArrayDeque<Runnable>()
        val manualDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                queued.addLast(block)
            }
        }
        val scope = CoroutineScope(SupervisorJob())
        val calls = AtomicInteger()
        try {
            val checker = UpdateChecker(
                scope = scope,
                currentVersionCode = 100,
                fetchLocation = {
                    calls.incrementAndGet()
                    "https://github.com/mikelward/steadmo/releases/tag/v105"
                },
                ioDispatcher = manualDispatcher,
            )

            checker.skipNextOpen()
            checker.onAppOpen()
            assertEquals(0, queued.size)

            // The subsequent open runs, and a concurrent check while it's in flight is ignored.
            checker.onAppOpen()
            checker.check()
            assertEquals(1, queued.size)

            queued.removeFirst().run()
            assertEquals(1, calls.get())
            assertTrue(checker.updateAvailable.value)

            // Once finished, another open starts a new check.
            checker.onAppOpen()
            assertEquals(1, queued.size)
            queued.removeFirst().run()
            assertEquals(2, calls.get())
        } finally {
            scope.cancel()
        }
    }

    @Test fun `MainActivity checks on every app open, skips rotation, and badges the overflow button`() {
        Disclaimer.accept(app)
        val checks = AtomicInteger()
        var location = "https://github.com/mikelward/steadmo/releases/tag/v100"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        app.testUpdateChecker = UpdateChecker(
            scope = scope,
            currentVersionCode = 100,
            fetchLocation = {
                checks.incrementAndGet()
                location
            },
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitForIdle()
                assertEquals(1, checks.get())
                compose.onNodeWithContentDescription("More options").assertIsDisplayed()

                // A new release is published while backgrounded; returning to the app checks again.
                location = "https://github.com/mikelward/steadmo/releases/tag/v101"
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                compose.waitForIdle()
                assertEquals(2, checks.get())
                compose.onNodeWithContentDescription("More options, update available").assertIsDisplayed()

                // Rotating recreates the activity without hitting the network again and keeps the badge.
                scenario.recreate()
                compose.waitForIdle()
                assertEquals(2, checks.get())
                compose.onNodeWithContentDescription("More options, update available").performClick()
                compose.onNodeWithContentDescription("Update available").assertIsDisplayed()
                compose.onNodeWithText("Get latest version").performClick()
            }
        } finally {
            scope.cancel()
        }
    }

    @Test fun `MainActivity waits until the disclaimer is accepted before checking for updates`() {
        val checks = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        app.testUpdateChecker = UpdateChecker(
            scope = scope,
            currentVersionCode = 100,
            fetchLocation = {
                checks.incrementAndGet()
                "https://github.com/mikelward/steadmo/releases/tag/v101"
            },
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use {
                compose.waitForIdle()
                assertEquals(0, checks.get())

                compose.onNodeWithText(AGREEMENT).performScrollTo().performClick()
                compose.onNodeWithText("Continue").performScrollTo().performClick()
                compose.waitForIdle()
                assertEquals(1, checks.get())
                compose.onNodeWithContentDescription("More options, update available").assertIsDisplayed()
            }
        } finally {
            scope.cancel()
        }
    }
}
