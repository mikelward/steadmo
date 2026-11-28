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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.shape.CircleShape
import app.steadmo.core.AlertPolicy
import app.steadmo.core.CamapsGoal
import app.steadmo.core.GlucoseUnit
import app.steadmo.core.MealReminder
import app.steadmo.core.MealReminderRule
import app.steadmo.core.rate
import app.steadmo.core.rateLabel
import app.steadmo.core.reading
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
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
    onMode: (CamapsGoal.Mode) -> Unit = { onOpenCamaps() },
    onOtherMeal: () -> Unit = onOpenCamaps,
    suggestShortcuts: Boolean = false,
    onDismissShortcuts: () -> Unit = {},
    version: String = "",
    onSourceCode: () -> Unit = {},
    onMinimizeCamaps: () -> Unit = {},
    onReportBug: () -> Unit = {},
    onShowDisclaimer: () -> Unit = {},
    onOpenAppInfo: () -> Unit = {},
    onGetLatest: () -> Unit = {},
    onKnownIssues: () -> Unit = {},
    shortcutsOn: Boolean = false,
    onOpenShortcutsSettings: () -> Unit = {},
    onWeeklyReport: () -> Unit = {},
    onSettings: () -> Unit = {},
    alertsSilenced: Boolean = false,
    urgentLowSilenced: Boolean = false,
    alarmVolumeMuted: Boolean = false,
    fullScreenAllowed: Boolean = true,
    exactAlarmsAllowed: Boolean = true,
    batteryUnrestricted: Boolean = true,
    unusedAppRestrictionsDisabled: Boolean = true,
    onAllowFullScreen: () -> Unit = {},
    onOpenAlertSettings: () -> Unit = {},
    onOpenUrgentLowSettings: () -> Unit = {},
    onOpenSoundSettings: () -> Unit = {},
    onAllowExactAlarms: () -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
    onOpenUnusedAppSettings: () -> Unit = {},
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
            // Saved, so a dialog stays open across rotation; back closes it.
            var showingShortcuts by rememberSaveable { mutableStateOf(false) }
            var showingAbout by rememberSaveable { mutableStateOf(false) }
            TopBar(
                onWeeklyReport = onWeeklyReport,
                onGetLatest = onGetLatest,
                onKnownIssues = onKnownIssues,
                onReportBug = onReportBug,
                onShowDisclaimer = onShowDisclaimer,
                onSettings = onSettings,
                onAbout = { showingAbout = true },
            )
            if (showingAbout) {
                AboutDialog(version = version, onSourceCode = onSourceCode, onDismiss = { showingAbout = false })
            }
            if (suggestShortcuts) {
                SetupCard(
                    "Go straight to CamAPS FX's screens",
                    "Steadmo's meal, Boost and Ease-off buttons can open the right screen in CamAPS FX " +
                        "and enter the grams for you to check.",
                    "Set up",
                    { showingShortcuts = true },
                    secondaryAction = "Not now",
                    onSecondary = onDismissShortcuts,
                )
            }
            if (showingShortcuts) {
                ShortcutsDialog(
                    on = shortcutsOn,
                    onOpenSettings = { showingShortcuts = false; onOpenShortcutsSettings() },
                    onOpenAppInfo = { showingShortcuts = false; onOpenAppInfo() },
                    onDismiss = { showingShortcuts = false },
                )
            }
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
            } else if (alertsSilenced) {
                SetupCard(
                    "Alerts are silenced",
                    "Steadmo's alerts won't sound, including when glucose stops coming. Set Alerts to Default.",
                    "Open settings",
                    onOpenAlertSettings,
                )
            }
            if (notificationsAllowed && !fullScreenAllowed) {
                SetupCard(
                    "Let urgent lows fill the screen",
                    "If an urgent low alarm goes unanswered for 5 minutes, Steadmo shows it full screen, even on the lock screen.",
                    "Allow",
                    onAllowFullScreen,
                )
            }
            val reliabilityIssues = buildList {
                if (notificationsAllowed && urgentLowSilenced) {
                    add(
                        ReliabilityIssue(
                            title = "Urgent low alarm is silenced",
                            body = "Steadmo's urgent low alarm won't sound. Set Urgent low to High.",
                            action = "Urgent low settings",
                            onAction = onOpenUrgentLowSettings,
                            urgent = true,
                        ),
                    )
                }
                if (notificationsAllowed && alarmVolumeMuted) {
                    add(
                        ReliabilityIssue(
                            title = "Alarm volume is muted",
                            body = "Urgent low alarms use the alarm volume and won't be heard while it's at zero.",
                            action = "Sound settings",
                            onAction = onOpenSoundSettings,
                            urgent = true,
                        ),
                    )
                }
                if (!exactAlarmsAllowed) {
                    add(
                        ReliabilityIssue(
                            title = "Allow exact alarms",
                            body = "Without Alarms & reminders, signal-loss alerts, hypo rechecks and meal reminders can be up to an hour late.",
                            action = "Allow alarms",
                            onAction = onAllowExactAlarms,
                        ),
                    )
                }
                if (!batteryUnrestricted) {
                    add(
                        ReliabilityIssue(
                            title = "Keep Steadmo running",
                            body = "Battery optimization can delay alerts and reminders. Set Battery to Unrestricted.",
                            action = "Battery settings",
                            onAction = onOpenBatterySettings,
                        ),
                    )
                }
                if (!unusedAppRestrictionsDisabled) {
                    add(
                        ReliabilityIssue(
                            title = "Keep Steadmo active when unused",
                            body = "Android pauses apps you rarely open and revokes their permissions. Turn off Pause app activity if unused.",
                            action = "App settings",
                            onAction = onOpenUnusedAppSettings,
                        ),
                    )
                }
            }
            if (reliabilityIssues.isNotEmpty()) {
                ReliabilityBanner(reliabilityIssues)
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
                        unit = state.unit,
                        treatments = state.treatments,
                        modifier = Modifier.fillMaxWidth().height(180.dp).padding(8.dp),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onMode(CamapsGoal.BOOST) }, modifier = Modifier.weight(1f)) { Text("Boost") }
                OutlinedButton(onClick = { onMode(CamapsGoal.EASE_OFF) }, modifier = Modifier.weight(1f)) { Text("Ease-off") }
            }
            Text("Meal", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(20, 40, 45).forEach { grams ->
                    FilledTonalButton(onClick = { onMeal(grams) }, modifier = Modifier.weight(1f)) { Text("$grams g") }
                }
                FilledTonalButton(onClick = onOtherMeal, modifier = Modifier.weight(1f)) { Text("Other") }
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
            state.unit.reading(latest),
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
    val rate = trend?.mmolPerMinute?.let { "${state.unit.rate(it)} ${state.unit.rateLabel}" }
    Text(
        listOfNotNull(trend?.arrow?.label ?: "Trend after 5 min of readings", rate).joinToString(" · "),
        style = MaterialTheme.typography.titleMedium,
        // An old trend greys out with its value: it's not how glucose is moving now.
        color = if (stale) MaterialTheme.colorScheme.outline else androidx.compose.ui.graphics.Color.Unspecified,
    )
    Text(
        "${state.unit.label} · " + if (now - latest.timeMillis < DateUtils.MINUTE_IN_MILLIS) {
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
    onWeeklyReport: () -> Unit,
    onGetLatest: () -> Unit,
    onKnownIssues: () -> Unit,
    onReportBug: () -> Unit,
    onShowDisclaimer: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Steadmo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { open = true }) {
                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More options")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Weekly report") }, onClick = { open = false; onWeeklyReport() })
                DropdownMenuItem(text = { Text("Get latest version") }, onClick = { open = false; onGetLatest() })
                DropdownMenuItem(text = { Text("Known issues") }, onClick = { open = false; onKnownIssues() })
                DropdownMenuItem(text = { Text("Report a bug") }, onClick = { open = false; onReportBug() })
                DropdownMenuItem(text = { Text("Settings") }, onClick = { open = false; onSettings() })
                DropdownMenuItem(text = { Text("Disclaimer") }, onClick = { open = false; onShowDisclaimer() })
                DropdownMenuItem(text = { Text("About") }, onClick = { open = false; onAbout() })
            }
        }
    }
}

/**
 * What CamAPS FX shortcuts do and risk, shown before the user turns it on in
 * Android's Accessibility settings (SPEC.md §4.6).
 */
@Composable
internal fun ShortcutsDialog(on: Boolean, onOpenSettings: () -> Unit, onOpenAppInfo: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CamAPS FX shortcuts") },
        text = {
            DialogBody {
                Text(
                    "Steadmo's meal, Boost and Ease-off buttons go straight to that screen in CamAPS FX. " +
                        "Meal buttons type the carbs in. You confirm them, check the bolus and deliver it in " +
                        "CamAPS FX, and confirm Boost and Ease-off there. Steadmo never delivers insulin.",
                )
                Text(
                    "It uses Android accessibility, which lets it see and tap CamAPS FX's screens, and no other app's.",
                )
                Text(
                    "A CamAPS FX update can stop it working, or make it tap or type in the wrong place, so " +
                        "check every time. Meal grams are copied too, to paste instead.",
                )
                Text(
                    if (on) "They're on." else "They're off. Turn on Steadmo CamAPS FX shortcuts in Accessibility. If it's " +
                        "greyed out, allow restricted settings in App info first.",
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text("Open settings") } },
        dismissButton = {
            Row {
                if (!on) TextButton(onClick = onOpenAppInfo) { Text("App info") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

/**
 * The version, and the licences: Steadmo's and its libraries' (all Apache 2.0),
 * with the licence text itself one tap away.
 */
@Composable
internal fun AboutDialog(version: String, onSourceCode: () -> Unit, onDismiss: () -> Unit) {
    var showingLicence by rememberSaveable { mutableStateOf(false) }
    if (showingLicence) {
        val context = LocalContext.current
        val text = remember { context.resources.openRawResource(R.raw.license).bufferedReader().use { it.readText() } }
        AlertDialog(
            onDismissRequest = { showingLicence = false },
            title = { Text("Apache License 2.0") },
            text = { DialogBody { Text(text, style = MaterialTheme.typography.bodySmall) } },
            confirmButton = { TextButton(onClick = { showingLicence = false }) { Text("Close") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Steadmo") },
        text = {
            DialogBody {
                Text("Version $version", fontWeight = FontWeight.Bold)
                Text("A companion display for CamAPS FX. Not a medical device.")
                Text(
                    "Steadmo is licensed under the Apache License 2.0, as are the libraries it uses: " +
                        "AndroidX, Jetpack Compose and Glance, Kotlin, kotlinx.coroutines and androidlog.",
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            Row {
                TextButton(onClick = { showingLicence = true }) { Text("Licence") }
                TextButton(onClick = onSourceCode) { Text("Source code") }
            }
        },
    )
}

/**
 * A dialog's content, scrolling when it's taller than the dialog can be (a
 * small phone, landscape, or large text), so nothing is cut off. Every
 * dialog's `text` uses it.
 */
@Composable
internal fun DialogBody(spacing: androidx.compose.ui.unit.Dp = 8.dp, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Scheduled meal reminders (each with time and days of the week) and calendar meals (SPEC.md §4.13). */
@Composable
internal fun MealReminderDialog(
    rules: List<MealReminderRule>,
    onToggleRule: (MealReminderRule, Boolean) -> Unit,
    onPickRuleTime: (MealReminderRule) -> Unit,
    onToggleRuleDay: (MealReminderRule, DayOfWeek) -> Unit,
    onDeleteRule: (MealReminderRule) -> Unit,
    onAddRule: () -> Unit,
    onDismiss: () -> Unit,
    formatTime: (LocalTime) -> String = { String.format(Locale.US, "%02d:%02d", it.hour, it.minute) },
    calendarOn: Boolean = false,
    onCalendar: (Boolean) -> Unit = {},
    onTime: Boolean = true,
    onAllowOnTime: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meal reminder") },
        text = {
            DialogBody(spacing = 12.dp) {
                Text("Reminders to bolus before meals, with Bolus and remind me in 30 or 60 minutes.")
                rules.forEach { rule ->
                    val timeText = formatTime(rule.time)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            OutlinedButton(onClick = { onPickRuleTime(rule) }) { Text(timeText) }
                            Text(
                                MealReminder.formatDays(rule.days),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            )
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { onToggleRule(rule, it) },
                                modifier = Modifier.semantics { contentDescription = "Remind at $timeText" },
                            )
                            IconButton(onClick = { onDeleteRule(rule) }) {
                                Icon(
                                    painterResource(R.drawable.ic_delete),
                                    contentDescription = "Remove reminder $timeText",
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            DayOfWeek.entries.forEach { day ->
                                val selected = day in rule.days
                                Surface(
                                    shape = CircleShape,
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .toggleable(
                                            value = selected,
                                            role = Role.Checkbox,
                                            onValueChange = { onToggleRuleDay(rule, day) },
                                        )
                                        .semantics {
                                            contentDescription = day.getDisplayName(TextStyle.FULL, Locale.US)
                                        },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            day.getDisplayName(TextStyle.NARROW, Locale.US),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = onAddRule) { Text("Add reminder") }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .toggleable(value = calendarOn, role = Role.Switch, onValueChange = onCalendar)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Calendar meals", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "When events like \"Dinner with Sam\" or a restaurant booking start.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = calendarOn, onCheckedChange = null)
                }
                if (!onTime && (rules.any { it.enabled && it.days.isNotEmpty() } || calendarOn)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("On time", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Without Alarms & reminders, Android may remind you up to an hour late.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onAllowOnTime) { Text("Allow") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Picks mmol/L or mg/dL for every value Steadmo shows. */
@Composable
internal fun UnitsDialog(unit: GlucoseUnit, onPick: (GlucoseUnit) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Units") },
        text = {
            DialogBody(spacing = 0.dp) {
                GlucoseUnit.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == unit, role = Role.RadioButton, onClick = { onPick(option) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RadioButton(selected = option == unit, onClick = null)
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
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

private data class ReliabilityIssue(
    val title: String,
    val body: String,
    val action: String,
    val onAction: () -> Unit,
    val urgent: Boolean = false,
)

/**
 * Surfaces reliability issues at the top of the home screen with a warning border
 * and icon. One issue shows directly; two or more collapse into an expandable
 * banner so warnings stay first without pushing the glucose reading below the fold
 * (SPEC.md §4.17).
 */
@Composable
private fun ReliabilityBanner(issues: List<ReliabilityIssue>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, HighColor),
    ) {
        if (issues.size == 1) {
            val issue = issues.single()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_warning),
                        contentDescription = "Warning",
                        tint = HighColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(issue.title, style = MaterialTheme.typography.titleMedium)
                }
                Text(issue.body, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = issue.onAction) { Text(issue.action) }
            }
            return@Card
        }
        val heading = if (issues.any { it.urgent }) {
            "${issues.size} alert & reliability issues"
        } else {
            "${issues.size} reliability settings to check"
        }
        val preview = issues.take(2).joinToString(" · ") { it.title } +
            if (issues.size > 2) " +${issues.size - 2} more" else ""
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_warning),
                            contentDescription = "Warning",
                            tint = HighColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(heading, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Review") }
            }
            if (expanded) {
                androidx.compose.material3.HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    issues.forEach { issue ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(issue.title, style = MaterialTheme.typography.titleSmall)
                            Text(issue.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = issue.onAction) { Text(issue.action) }
                        }
                    }
                }
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
