package app.steadmo

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.steadmo.core.AlertPolicy
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Readings older than this are shown greyed out (SPEC.md §4.1). */
private const val STALE_MILLIS = 10 * 60_000L

@Composable
fun HomeScreen(
    state: GlucoseState,
    listenerAllowed: Boolean,
    notificationsAllowed: Boolean,
    onAllowListener: () -> Unit,
    onAllowNotifications: () -> Unit,
    onOpenCamaps: () -> Unit,
    onMeal: (Int) -> Unit,
    onMinimizeCamaps: () -> Unit = {},
    onReportBug: () -> Unit = {},
    onShowDisclaimer: () -> Unit = {},
    onOpenAppInfo: () -> Unit = {},
    onGetLatest: () -> Unit = {},
    onKnownIssues: () -> Unit = {},
    clock: () -> Long = System::currentTimeMillis,
    ticking: Boolean = true,
) {
    var now by remember { mutableLongStateOf(clock()) }
    if (ticking) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000)
                now = clock()
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TopBar(
                onGetLatest = onGetLatest,
                onKnownIssues = onKnownIssues,
                onReportBug = onReportBug,
                onShowDisclaimer = onShowDisclaimer,
            )
            if (!listenerAllowed) {
                SetupCard(
                    "Allow notification access",
                    "Steadmo reads your glucose from CamAPS FX's notification.",
                    "Allow",
                    onAllowListener,
                    note = "If Android says it's controlled by a restricted setting: open App info, " +
                        "tap ⋮, choose Allow restricted settings, then come back and tap Allow.",
                    secondaryAction = "Open App info",
                    onSecondary = onOpenAppInfo,
                )
            }
            if (!notificationsAllowed) {
                SetupCard(
                    "Allow notifications",
                    "For the trend arrow in the status bar and fast rise and fall alerts.",
                    "Allow",
                    onAllowNotifications,
                )
            }

            val importance = state.camapsChannelImportance
            if (state.camapsChannelId != null && importance != null && importance > NotificationManager.IMPORTANCE_MIN) {
                SetupCard(
                    "Minimize CamAPS FX's notification",
                    "Steadmo now shows your glucose. Set CamAPS FX's My diabetes info to Silent, " +
                        "then Minimize. Leave it on: Steadmo reads it.",
                    "Open settings",
                    onMinimizeCamaps,
                )
            }

            val latest = state.latest
            if (latest == null) {
                Text(
                    "Waiting for CamAPS FX. In CamAPS FX's notification settings, turn on My diabetes info.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                val stale = now - latest.timeMillis > STALE_MILLIS
                Reading(state, now, stale)
                Card(modifier = Modifier.fillMaxWidth()) {
                    GlucoseGraph(
                        readings = state.readings,
                        now = now,
                        modifier = Modifier.fillMaxWidth().height(180.dp).padding(8.dp),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenCamaps, modifier = Modifier.weight(1f)) { Text("Boost") }
                OutlinedButton(onClick = onOpenCamaps, modifier = Modifier.weight(1f)) { Text("Ease-off") }
            }
            Text("Meal", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(20, 40, 45).forEach { grams ->
                    FilledTonalButton(onClick = { onMeal(grams) }, modifier = Modifier.weight(1f)) { Text("$grams g") }
                }
                FilledTonalButton(onClick = onOpenCamaps, modifier = Modifier.weight(1f)) { Text("Other") }
            }
            Text(
                "These open CamAPS FX, where you check and confirm the dose.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.lastDump?.let { NotificationDetails(it) }
            Text(
                "Not a medical device. Use at your own risk.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Reading(state: GlucoseState, now: Long, stale: Boolean) {
    val latest = state.latest ?: return
    val trend = state.trend
    val color = if (stale) MaterialTheme.colorScheme.outline else rangeColor(latest.mmol)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            latest.display,
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color,
        )
        Icon(
            painter = painterResource(trend?.arrow.drawable()),
            contentDescription = trend?.arrow?.label ?: "No trend yet",
            tint = color,
            modifier = Modifier.size(64.dp),
        )
    }
    val rate = trend?.mmolPerMinute?.let { "${if (it >= 0) "+" else "−"}${AlertPolicy.fmt2(abs(it))} mmol/L/min" }
    Text(
        listOfNotNull(trend?.arrow?.label ?: "Trend after 5 min of readings", rate).joinToString(" · "),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        "mmol/L · " + if (now - latest.timeMillis < DateUtils.MINUTE_IN_MILLIS) {
            "just now"
        } else {
            DateUtils.getRelativeTimeSpanString(latest.timeMillis, now, DateUtils.MINUTE_IN_MILLIS)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.status?.autoMode?.let { mode ->
        val statusTime = state.statusTime ?: latest.timeMillis
        val modeStale = now - statusTime > STALE_MILLIS
        // An old Auto mode report must not look like confirmation the loop is running.
        val label = if (modeStale) {
            "Auto mode ${mode.lowercase()}, " +
                DateUtils.getRelativeTimeSpanString(statusTime, now, DateUtils.MINUTE_IN_MILLIS)
        } else {
            "Auto mode ${mode.lowercase()}"
        }
        AssistChip(onClick = {}, enabled = !modeStale, label = { Text(label) })
    }
    if (stale) {
        Text("No new reading from CamAPS FX for over 10 minutes.", color = MaterialTheme.colorScheme.error)
    }
}

/** The app name, and a ⋮ menu for the things used rarely. */
@Composable
private fun TopBar(
    onGetLatest: () -> Unit,
    onKnownIssues: () -> Unit,
    onReportBug: () -> Unit,
    onShowDisclaimer: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Steadmo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { open = true }) {
                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More options")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Get latest version") }, onClick = { open = false; onGetLatest() })
                DropdownMenuItem(text = { Text("Known issues") }, onClick = { open = false; onKnownIssues() })
                DropdownMenuItem(text = { Text("Report a bug") }, onClick = { open = false; onReportBug() })
                DropdownMenuItem(text = { Text("Disclaimer") }, onClick = { open = false; onShowDisclaimer() })
            }
        }
    }
}

@Composable
private fun SetupCard(
    title: String,
    text: String,
    action: String,
    onClick: () -> Unit,
    note: String? = null,
    secondaryAction: String? = null,
    onSecondary: () -> Unit = {},
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onClick) { Text(action) }
                secondaryAction?.let { OutlinedButton(onClick = onSecondary) { Text(it) } }
            }
        }
    }
}

/** What Steadmo read from CamAPS FX, to check the parsing on a new phone or CamAPS version. */
@Composable
private fun NotificationDetails(dump: NotificationDump) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { open = !open }) {
        Text(if (open) "Hide CamAPS FX notification" else "Show CamAPS FX notification")
    }
    if (open) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    dump.describe(),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { contentDescription = "CamAPS FX notification contents" },
                )
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("CamAPS FX notification", dump.describe()))
                }) { Text("Copy") }
            }
        }
    }
}
