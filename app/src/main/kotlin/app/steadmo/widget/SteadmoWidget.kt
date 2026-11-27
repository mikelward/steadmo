package app.steadmo.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.layout.ColumnScope
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.runtime.collectAsState
import app.steadmo.GlucoseState
import kotlinx.coroutines.flow.StateFlow
import app.steadmo.Disclaimer
import app.steadmo.HighColor
import app.steadmo.InRangeColor
import app.steadmo.LowColor
import app.steadmo.MainActivity
import app.steadmo.repository
import app.steadmo.core.RangeSide

/**
 * The home screen widget (SPEC.md §4.5): the reading, trend arrow and time at
 * every size; with room, the 3-hour graph; at full size, Boost, Ease-off and
 * the meal presets, which open CamAPS FX like the app's buttons do.
 */
class SteadmoWidget(private val clock: () -> Long = System::currentTimeMillis) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.repository
        // A widget placed after the last reading still greys out on time.
        WidgetUpdates.scheduleStale(context, repository.state.value, clock())
        provideContent { WidgetRoot(repository.state, context, clock) }
    }
}

/**
 * The widget's live content. Plain collectAsState: Glance's composition has
 * no lifecycle owner, so the lifecycle-aware collector would throw.
 */
@Composable
fun WidgetRoot(states: StateFlow<GlucoseState>, context: Context, clock: () -> Long) {
    val state by states.collectAsState()
    val model = WidgetModel.of(state, Disclaimer.accepted(context), clock())
    GlanceTheme { WidgetContent(model, clock()) }
}

private val StaleColor = Color(0xFF8A8A8A)

/** The narrowest widget that fits all four meal buttons in one row. */
internal val WIDE_MEAL_ROW = 280.dp

/**
 * What fits at [size]: buttons from 320 dp tall, and the graph in whatever
 * height is left over (up to 120 dp, and not at all below 48 dp), so the
 * button rows are never pushed off the bottom.
 */
internal data class WidgetLayout(val buttons: Boolean, val mealRows: Int, val graph: androidx.compose.ui.unit.Dp?) {
    companion object {
        private val PADDING = 24.dp
        private val HEADER = 90.dp
        private val BUTTON_ROW = 52.dp
        private val GAP = 8.dp

        fun of(size: DpSize): WidgetLayout {
            val buttons = size.height >= 320.dp
            val mealRows = if (size.width >= WIDE_MEAL_ROW) 1 else 2
            val buttonSpace = if (buttons) BUTTON_ROW * (1 + mealRows) + GAP else 0.dp
            val left = size.height - PADDING - HEADER - buttonSpace - GAP
            val graph = minOf(left, 120.dp).takeIf { it >= 48.dp && size.height >= 200.dp }
            return WidgetLayout(buttons, mealRows, graph)
        }
    }
}

private fun color(side: RangeSide, stale: Boolean) = when {
    stale -> StaleColor
    side == RangeSide.LOW -> LowColor
    side == RangeSide.HIGH -> HighColor
    else -> InRangeColor
}

/** Opens [WidgetActionActivity] for [path]: each button has its own URI, so their PendingIntents differ. */
internal fun widgetAction(context: Context, path: String): Action =
    actionStartActivity(
        Intent(context, WidgetActionActivity::class.java)
            .setData(Uri.parse("${WidgetActionActivity.SCHEME}://widget/$path"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

@Composable
fun WidgetContent(model: WidgetModel, now: Long) {
    val context = LocalContext.current
    val size = LocalSize.current
    val openApp: Action = actionStartActivity<MainActivity>()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
    ) {
        when (model) {
            WidgetModel.NeedsDisclaimer -> Message("Steadmo", "Open Steadmo to accept the disclaimer.", openApp)
            WidgetModel.Waiting -> Message("Steadmo", "Waiting for CamAPS FX.", openApp)
            is WidgetModel.Showing -> Showing(model, now, size, openApp, context)
        }
    }
}

@Composable
private fun Message(title: String, text: String, onClick: Action) {
    Column(modifier = GlanceModifier.fillMaxSize().clickable(onClick)) {
        Text(title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp))
        Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp))
    }
}

@Composable
private fun ColumnScope.Showing(model: WidgetModel.Showing, now: Long, size: DpSize, openApp: Action, context: Context) {
    val tint = color(model.side, model.stale)
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(openApp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            model.value,
            style = TextStyle(color = ColorProvider(tint), fontSize = if (size.height < 110.dp) 32.sp else 44.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.width(6.dp))
        Image(
            provider = ImageProvider(model.arrow),
            contentDescription = model.arrowLabel,
            colorFilter = ColorFilter.tint(ColorProvider(tint)),
            modifier = GlanceModifier.size(if (size.height < 110.dp) 28.dp else 40.dp),
        )
        Spacer(GlanceModifier.defaultWeight())
        Column(horizontalAlignment = Alignment.End) {
            Text(model.time, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
            model.rate?.let { Text(it, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp)) }
        }
    }
    val status = listOfNotNull(model.autoMode, if (model.stale) "No new reading for over 10 min" else null).joinToString(" · ")
    if (status.isNotEmpty()) {
        Text(status, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
    }
    val layout = WidgetLayout.of(size)
    if (layout.graph != null) {
        Spacer(GlanceModifier.height(8.dp))
        val density = context.resources.displayMetrics.density
        val graphHeight = layout.graph
        val widthPx = ((size.width - 24.dp).value * density).toInt()
        val heightPx = (graphHeight.value * density).toInt()
        val bitmap = WidgetGraph.draw(
            model.readings, now, widthPx, heightPx, density,
            WidgetGraph.Colors(
                low = LowColor.toArgb(), inRange = InRangeColor.toArgb(), high = HighColor.toArgb(),
                band = InRangeColor.copy(alpha = 0.15f).toArgb(),
                grid = StaleColor.copy(alpha = 0.6f).toArgb(), label = StaleColor.toArgb(),
            ),
        )
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "Glucose over the last 3 hours",
            modifier = GlanceModifier.fillMaxWidth().height(graphHeight).clickable(openApp),
        )
    }
    if (layout.buttons) {
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Button("Boost", widgetAction(context, "camaps/boost"), modifier = GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            Button("Ease-off", widgetAction(context, "camaps/ease-off"), modifier = GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.height(8.dp))
        val meals = listOf("20 g" to "meal/20", "40 g" to "meal/40", "45 g" to "meal/45", "Other" to "camaps/meal")
        // Four across needs room for each label; narrower widgets get two rows of two.
        val rows = if (layout.mealRows == 1) listOf(meals) else meals.chunked(2)
        rows.forEachIndexed { i, row ->
            if (i > 0) Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                row.forEachIndexed { j, (label, path) ->
                    if (j > 0) Spacer(GlanceModifier.width(6.dp))
                    Button(label, widgetAction(context, path), modifier = GlanceModifier.defaultWeight())
                }
            }
        }
    }
}
