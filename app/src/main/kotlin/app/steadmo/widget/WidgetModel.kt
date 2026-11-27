package app.steadmo.widget

import androidx.annotation.DrawableRes
import app.steadmo.GlucoseState
import app.steadmo.drawable
import app.steadmo.core.AlertPolicy
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
        val autoMode: String?,
        val readings: List<Reading>,
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
                value = latest.display,
                arrow = trend?.arrow.drawable(),
                arrowLabel = trend?.arrow?.label ?: "No trend yet",
                side = latest.side(),
                stale = now - latest.timeMillis > STALE_MILLIS,
                time = timeFormat.format(Date(latest.timeMillis)),
                rate = trend?.let { "${if (it.mmolPerMinute >= 0) "+" else "−"}${AlertPolicy.fmt2(kotlin.math.abs(it.mmolPerMinute))}/min" },
                autoMode = state.status?.autoMode,
                readings = state.readings,
            )
        }
    }
}
