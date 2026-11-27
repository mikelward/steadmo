package app.steadmo

import android.app.Application
import android.content.Context
import android.util.Log
import app.steadmo.core.SteadmoLog
import com.mikelward.androidlog.DebugLog
import com.mikelward.androidlog.android.DebugFileSink
import com.mikelward.androidlog.android.LogcatSink
import com.mikelward.androidlog.android.ProcessExits

open class SteadmoApp : Application() {
    lateinit var repository: GlucoseRepository
        private set

    /** The persisted debug log, for bug reports; null if it couldn't be set up. */
    var diagnosticSink: DebugFileSink? = null
        private set

    override fun onCreate() {
        super.onCreate()
        installDiagnosticLog()
        SteadmoLog.pinnedEvent("app: started, version %s", BuildConfig.VERSION_NAME)
        repository = GlucoseRepository(this, StatusNotifier(this))
        logProcessExits()
    }

    /**
     * Logcat plus the persisted file, before anything else runs, so a crash
     * anywhere after onCreate is captured. A failure to set up the log is
     * logged to logcat and swallowed: the log exists to diagnose the app, not
     * to take it down.
     */
    protected open fun installDiagnosticLog() {
        try {
            diagnosticSink = installDiagnosticSinks(SteadmoLog, this)
        } catch (e: Exception) {
            Log.w(TAG, "diagnostic log setup failed: ${e::class.simpleName}")
        }
    }

    /** Why the previous processes ended (ANR, crash, update), off the main thread. */
    protected open fun logProcessExits() {
        Thread {
            try {
                ProcessExits.logRecent(this, SteadmoLog)
            } catch (e: Exception) {
                SteadmoLog.failure(e, "app: process exit collection failed")
            }
        }.start()
    }

    companion object {
        const val TAG = "Steadmo"
    }
}

/** Registers logcat and the persisted file on [log]; the file's rotation runs before this run's writes. */
internal fun installDiagnosticSinks(log: DebugLog, context: Context, installCrashHandler: Boolean = true): DebugFileSink {
    log.addSink(LogcatSink(SteadmoApp.TAG), DebugLog.Destination.DEVICE)
    val fileSink = DebugFileSink(log, context)
    fileSink.start(installCrashHandler)
    log.addSink(fileSink, DebugLog.Destination.DEVICE)
    return fileSink
}

val Context.repository: GlucoseRepository
    get() = (applicationContext as SteadmoApp).repository
