package app.steadmo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.steadmo.core.GlucoseUnit
import app.steadmo.core.HIGH_MMOL
import app.steadmo.core.LOW_MMOL
import app.steadmo.core.URGENT_LOW_MMOL
import app.steadmo.core.WeeklyReport
import app.steadmo.core.value
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** Weekly glucose trends and recurring issues (SPEC.md §4.16). */
@Composable
fun WeeklyReportScreen(
    summary: WeeklyReport.Summary,
    unit: GlucoseUnit,
    zone: ZoneId = ZoneId.systemDefault(),
    onShare: () -> Unit = {},
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    val periodLabel = "${Instant.ofEpochMilli(summary.periodStart).atZone(zone).format(fmt)} – " +
        Instant.ofEpochMilli(summary.periodEnd).atZone(zone).format(fmt)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                }
                Text(
                    "Weekly report",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (summary.readingCount > 0) {
                    TextButton(onClick = onShare) { Text("Share") }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val subtitle = if (summary.readingCount > 0) {
                    "$periodLabel · ${summary.coveragePercent}% coverage"
                } else {
                    periodLabel
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (summary.readingCount == 0) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No readings in the last 7 days yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Keep Steadmo running alongside CamAPS FX and your weekly report will build up here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    SummaryCard(summary, unit)
                    Text(
                        "Trends and issues",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                    )
                    if (summary.issues.isEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "No recurring issues this week.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        summary.issues.forEach { issue -> IssueCard(issue) }
                    }
                }

                Text(
                    "Not a medical device. Discuss recurring patterns with your diabetes team.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: WeeklyReport.Summary, unit: GlucoseUnit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${summary.inRangePercent}% in range",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = InRangeColor,
                )
                summary.comparison?.let { c ->
                    val sign = if (c.inRangeDeltaPercent >= 0) "+" else "−"
                    Text(
                        "$sign${abs(c.inRangeDeltaPercent)}% vs last week",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            RangeBar(
                lowPercent = summary.lowPercent,
                inRangePercent = summary.inRangePercent,
                highPercent = summary.highPercent,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Low (<${unit.value(LOW_MMOL)})", "${summary.lowPercent}%", LowColor)
                Metric("In range", "${summary.inRangePercent}%", InRangeColor)
                Metric("High (>${unit.value(HIGH_MMOL)})", "${summary.highPercent}%", HighColor)
            }

            val extras = listOfNotNull(
                summary.averageMmol?.let { "Average ${unit.value(it)} ${unit.label}" },
                summary.cvPercent?.let { "CV $it%" },
                if (summary.veryLowPercent > 0) "Very low (<${unit.value(URGENT_LOW_MMOL)}) ${summary.veryLowPercent}%" else null,
                if (summary.treatmentCount > 0) {
                    val word = if (summary.treatmentCount == 1) "treated low" else "treated lows"
                    "${summary.treatmentCount} $word"
                } else {
                    null
                },
            )
            if (extras.isNotEmpty()) {
                Text(
                    extras.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RangeBar(lowPercent: Int, inRangePercent: Int, highPercent: Int) {
    val barDesc = "Low $lowPercent%, in range $inRangePercent%, high $highPercent%"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .semantics { contentDescription = barDesc },
    ) {
        if (lowPercent > 0) {
            Box(Modifier.weight(lowPercent.toFloat()).fillMaxHeight().background(LowColor))
        }
        if (inRangePercent > 0) {
            Box(Modifier.weight(inRangePercent.toFloat()).fillMaxHeight().background(InRangeColor))
        }
        if (highPercent > 0) {
            Box(Modifier.weight(highPercent.toFloat()).fillMaxHeight().background(HighColor))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IssueCard(issue: WeeklyReport.ReportIssue) {
    val titleColor = when (issue.severity) {
        WeeklyReport.IssueSeverity.HIGH -> LowColor
        WeeklyReport.IssueSeverity.MODERATE -> HighColor
        WeeklyReport.IssueSeverity.INFO -> MaterialTheme.colorScheme.onSurface
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(issue.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = titleColor)
            Text(issue.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Settings dialog for the weekly report schedule (SPEC.md §4.16). */
@Composable
internal fun WeeklyReportDialog(
    on: Boolean,
    day: DayOfWeek,
    time: String,
    onToggle: (Boolean) -> Unit,
    onPickDay: (DayOfWeek) -> Unit,
    onPickTime: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weekly report") },
        text = {
            DialogBody(spacing = 12.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = on, role = Role.Switch, onValueChange = onToggle),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Weekly notification", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "A summary of trends and recurring issues once a week.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = on, onCheckedChange = null)
                }
                if (on) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Day", style = MaterialTheme.typography.bodyLarge)
                        DayRow(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY), day, onPickDay)
                        DayRow(listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), day, onPickDay)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Time", style = MaterialTheme.typography.bodyLarge)
                            Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = onPickTime) { Text("Change") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DayRow(days: List<DayOfWeek>, selected: DayOfWeek, onPick: (DayOfWeek) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { d ->
            val short = d.getDisplayName(TextStyle.SHORT, Locale.US)
            val full = d.getDisplayName(TextStyle.FULL, Locale.US)
            FilterChip(
                selected = d == selected,
                onClick = { onPick(d) },
                label = { Text(short) },
                modifier = Modifier.semantics { contentDescription = full },
            )
        }
    }
}
