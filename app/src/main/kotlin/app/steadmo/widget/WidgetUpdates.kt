package app.steadmo.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.steadmo.GlucoseState
import app.steadmo.core.SteadmoLog
import java.util.concurrent.TimeUnit

class SteadmoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = SteadmoWidget()
}

/**
 * Keeps placed widgets current: redrawn on each new reading, and once more
 * when that reading goes stale, so an old value greys out rather than
 * looking current (SPEC.md §4.5).
 */
object WidgetUpdates {
    private const val STALE_WORK = "widget-stale"

    suspend fun onState(context: Context, state: GlucoseState, now: Long = System.currentTimeMillis()) {
        val ids = try {
            GlanceAppWidgetManager(context).getGlanceIds(SteadmoWidget::class.java)
        } catch (e: Exception) {
            SteadmoLog.failure(e, "widget: couldn't list placed widgets")
            return
        }
        if (ids.isEmpty()) return
        SteadmoWidget().updateAll(context)
        scheduleStale(context, state, now)
        SteadmoLog.verbose("widget: %s updated", ids.size)
    }

    /** Redraws once the latest reading goes stale, so it greys out on time. */
    fun scheduleStale(context: Context, state: GlucoseState, now: Long = System.currentTimeMillis()) {
        val latest = state.latest ?: return
        val delay = latest.timeMillis + WidgetModel.STALE_MILLIS + 5_000 - now
        if (delay <= 0) return
        try {
            WorkManager.getInstance(context).enqueueUniqueWork(
                STALE_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<StaleWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
            )
            SteadmoLog.verbose("widget: stale redraw in %s ms", delay)
        } catch (e: IllegalStateException) {
            SteadmoLog.failure(e, "widget: couldn't schedule the stale redraw")
        }
    }

    class StaleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            SteadmoLog.event("widget: reading went stale; redrawing")
            SteadmoWidget().updateAll(applicationContext)
            return Result.success()
        }
    }
}
