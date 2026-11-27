package app.steadmo

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.DrawableRes
import app.steadmo.core.SteadmoLog
import app.steadmo.core.reading
import app.steadmo.widget.WidgetActionActivity

/** What the glucose tile shows (SPEC.md §4.11), worked out from the app's state. */
internal data class GlucoseTileContent(val label: String, val subtitle: String, @param:DrawableRes val icon: Int, val state: Int) {
    companion object {
        /** Older than this, the tile shows no value: like the app, it greys out rather than mislead. */
        const val STALE_MILLIS = 10 * 60_000L

        fun of(state: GlucoseState, now: Long): GlucoseTileContent {
            val latest = state.latest
                ?: return GlucoseTileContent("Glucose", "No reading yet", (null as app.steadmo.core.TrendArrow?).drawable(), Tile.STATE_INACTIVE)
            val minutes = (now - latest.timeMillis) / 60_000
            if (now - latest.timeMillis > STALE_MILLIS) {
                return GlucoseTileContent("Glucose", "No reading for $minutes min", (null as app.steadmo.core.TrendArrow?).drawable(), Tile.STATE_INACTIVE)
            }
            val age = if (minutes < 1) "just now" else "$minutes min ago"
            return GlucoseTileContent(
                label = "${state.unit.reading(latest)} ${state.unit.label}",
                subtitle = listOfNotNull(state.trend?.arrow?.label, age).joinToString(" · "),
                icon = state.trend?.arrow.drawable(),
                state = Tile.STATE_ACTIVE,
            )
        }
    }
}

/**
 * The glucose tile: value, unit, trend and age, redrawn each time the shade
 * opens (so the age is current); tapping opens Steadmo.
 */
class GlucoseTileService : TileService() {
    override fun onStartListening() {
        val content = GlucoseTileContent.of(repository.state.value, System.currentTimeMillis())
        qsTile?.apply {
            label = content.label
            subtitle = content.subtitle
            icon = Icon.createWithResource(this@GlucoseTileService, content.icon)
            state = content.state
            updateTile()
        }
        SteadmoLog.verbose("tile: glucose %s, %s", content.label, content.subtitle)
    }

    override fun onClick() {
        SteadmoLog.event("tile: glucose tapped")
        startActivityAndCollapse(target())
    }

    internal fun target(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )
}

/** The meal tile: like Other, CamAPS FX's carbs box for the user to type into. */
class MealTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        SteadmoLog.event("tile: meal tapped")
        startActivityAndCollapse(target())
    }

    /** The widget's Other: copy nothing, open CamAPS FX's carbs box (with shortcuts on). */
    internal fun target(): PendingIntent = PendingIntent.getActivity(
        this, 1,
        Intent(this, WidgetActionActivity::class.java)
            .setData(Uri.parse("${WidgetActionActivity.SCHEME}://widget/camaps/meal"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
