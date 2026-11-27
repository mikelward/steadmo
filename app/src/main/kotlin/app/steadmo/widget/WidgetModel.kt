package app.steadmo.widget

import androidx.annotation.DrawableRes
import app.steadmo.GlucoseState
import app.steadmo.drawable
import app.steadmo.core.GlucoseUnit
import app.steadmo.core.rate
import app.steadmo.core.reading
import app.steadmo.core.Reading
import app.steadmo.core.RangeSide
import app.steadmo.core.side
import java.text.DateFormat
import java.util.Date

/** What the home screen widget shows, worked out from the app's state (SPEC.md §4.5). */
sealed interface WidgetModel {
    /** The disclaimer hasn't been accepted: the widget only offers to open the app. */
    data object NeedsDisclaimer : WidgetModel

    /** No reading from CamAPS FX yet. */
    data object Waiting : WidgetModel

    data class Showing(
        val value: String,
        @param:DrawableRes val arrow: Int,
        val arrowLabel: String,
        val side: RangeSide,
        /** Older than [STALE_MILLIS]: greyed out, like the app. */
        val stale: Boolean,
        /** When the reading was taken, e.g. "18:42". */
        val time: String,
        val rate: String?,
        /** e.g. "Auto mode on", or "Auto mode on at 18:20" once that report is stale. */
        val autoMode: String?,
        val readings: List<Reading>,
        val unit: GlucoseUnit = GlucoseUnit.MMOL,
        val treatments: List<Long> = emptyList(),
    ) : WidgetModel

    companion object {
        const val STALE_MILLIS = 10 * 60_000L

        fun of(
            state: GlucoseState,
            disclaimerAccepted: Boolean,
            now: Long,
            timeFormat: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT),
        ): WidgetModel {
            if (!disclaimerAccepted) return NeedsDisclaimer
            val latest = state.latest ?: return Waiting
            val trend = state.trend
            return Showing(
                value = state.unit.reading(latest),
                arrow = trend?.arrow.drawable(),
                arrowLabel = trend?.arrow?.label ?: "No trend yet",
                side = latest.side(),
                stale = now - latest.timeMillis > STALE_MILLIS,
                time = timeFormat.format(Date(latest.timeMillis)),
                rate = trend?.let { "${state.unit.rate(it.mmolPerMinute)}/min" },
                autoMode = state.status?.autoMode?.let { mode ->
                    val at = state.statusTime ?: latest.timeMillis
                    // An old report must not look like the loop is still running.
                    if (now - at > STALE_MILLIS) "Auto mode ${mode.lowercase()} at ${timeFormat.format(Date(at))}"
                    else "Auto mode ${mode.lowercase()}"
                },
                readings = state.readings,
                unit = state.unit,
                treatments = state.treatments,
            )
        }
    }
}
