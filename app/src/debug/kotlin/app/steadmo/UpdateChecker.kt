package app.steadmo

import app.steadmo.core.SteadmoLog
import app.steadmo.core.UpdateCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer debug APK on every app open (SPEC.md §4.15, §5).
 *
 * Uses a HEAD request to `/releases/latest` without following redirects, parsing `v<versionCode>`
 * from the `Location` header so there is no API rate limit and no response body to download.
 */
open class UpdateChecker(
    private val scope: CoroutineScope,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    private val fetchLocation: () -> String? = ::fetchLatestReleaseLocation,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _updateAvailable = MutableStateFlow(false)
    open val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    private val lock = Any()
    private var inFlight: Job? = null
    private var skipNext = false

    /** Called when the app comes to the foreground; skips the immediate restart after a config change. */
    open fun onAppOpen() {
        val skipped = synchronized(lock) {
            if (skipNext) {
                skipNext = false
                true
            } else {
                false
            }
        }
        if (!skipped) check()
    }

    /** Marks the next [onAppOpen] as a configuration-change recreation so we don't re-hit the network. */
    open fun skipNextOpen() {
        synchronized(lock) { skipNext = true }
    }

    /** Starts an asynchronous check if one is not already running. */
    open fun check() {
        synchronized(lock) {
            skipNext = false
            if (inFlight?.isActive == true) return
            inFlight = scope.launch(ioDispatcher) { runCheck() }
        }
    }

    internal fun runCheck() {
        val location = try {
            fetchLocation()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SteadmoLog.failure(e, "update: check failed")
            return
        }
        val latest = UpdateCheck.parseVersionCode(location)
        if (latest == null) {
            SteadmoLog.event("update: check returned no version tag")
            return
        }
        val available = UpdateCheck.isUpdateAvailable(currentVersionCode, location)
        _updateAvailable.value = available
        SteadmoLog.event(
            "update: current v%d, latest v%d, available %s",
            currentVersionCode,
            latest,
            available,
        )
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 5_000

        /** Sends a HEAD request without following redirects and returns the `Location` header on 3xx. */
        internal fun fetchLatestReleaseLocation(url: String = UpdateCheck.LATEST_RELEASE_URL): String? {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "HEAD"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("User-Agent", "Steadmo/${BuildConfig.VERSION_NAME}")
                val status = connection.responseCode
                if (status !in 300..399) {
                    SteadmoLog.event("update: unexpected HTTP %d from release check", status)
                    return null
                }
                return connection.getHeaderField("Location")
            } finally {
                connection.disconnect()
            }
        }
    }
}
