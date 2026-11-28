package app.steadmo

import android.app.Application
import android.content.Context
import android.util.Log
import app.steadmo.core.SteadmoLog
import app.steadmo.widget.WidgetUpdates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.mikelward.androidlog.DebugLog
import com.mikelward.androidlog.android.DebugFileSink
import com.mikelward.androidlog.android.LogcatSink
import com.mikelward.androidlog.android.ProcessExits

open class SteadmoApp : Application() {
    lateinit var repository: GlucoseRepository
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    open val updateChecker: UpdateChecker by lazy { createUpdateChecker() }

    /** The persisted debug log, for bug reports; null if it couldn't be set up. */
    var diagnosticSink: DebugFileSink? = null
        private set

    override fun onCreate() {
        super.onCreate()
        installDiagnosticLog()
        SteadmoLog.pinnedEvent("app: started, version %s", BuildConfig.VERSION_NAME)
        repository = GlucoseRepository(this, StatusNotifier(this))
        logProcessExits()
        watchForWidgets()
        watchSignal()
        LauncherShortcuts.publish(this)
        MealReminders.schedule(this)
        MealReminders.restoreSnooze(this)
        CalendarMealReminders.scan(this)
        CalendarMealReminders.watch(this)
        WeeklyReports.schedule(this)
        Recheck.restore(this, repository.state.value.treatments)
    }

    /** Checks GitHub Releases for a newer build on open (debug builds only; SPEC.md §5). */
    protected open fun createUpdateChecker(): UpdateChecker = UpdateChecker(appScope)

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
            // The debug log is what failed, so logcat is the only place left to say so.
            Log.w(TAG, "diagnostic log setup failed", e)
        }
    }

    /** Redraws placed widgets as readings arrive (WidgetUpdates). */
    protected open fun watchForWidgets() {
        appScope.launch {
            repository.state
                // statusTime too: a mode-only repost moves when the mode goes stale.
                .map { listOf(it.latest, it.trend, it.status?.autoMode, it.statusTime, it.unit, it.treatments) }
                .distinctUntilChanged()
                .collect { WidgetUpdates.onState(this@SteadmoApp, repository.state.value) }
        }
    }

    /** Picks up the signal watch after a restart (the alarm doesn't survive a reboot). */
    protected open fun watchSignal() = SignalWatch.run(this)

    /** Redraws widgets now, e.g. once the disclaimer is accepted. */
    fun refreshWidgets() {
        appScope.launch { WidgetUpdates.onState(this@SteadmoApp, repository.state.value) }
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

val Context.updateChecker: UpdateChecker
    get() = (applicationContext as SteadmoApp).updateChecker
