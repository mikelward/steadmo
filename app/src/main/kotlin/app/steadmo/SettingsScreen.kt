package app.steadmo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.steadmo.core.GlucoseUnit
import app.steadmo.core.MealReminder
import app.steadmo.core.MealReminderRule
import app.steadmo.core.WeeklyReportSchedule
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** ⋮ → Settings: Steadmo's own settings, each opening its dialog. Back, or the arrow, returns home. */
@Composable
fun SettingsScreen(
    unit: GlucoseUnit,
    onSetUnit: (GlucoseUnit) -> Unit,
    onBack: () -> Unit,
    mealReminderRules: List<MealReminderRule> = listOf(MealReminder.defaultRule()),
    formatTime: (LocalTime) -> String = { String.format(Locale.US, "%02d:%02d", it.hour, it.minute) },
    onToggleMealRule: (MealReminderRule, Boolean) -> Unit = { _, _ -> },
    onPickMealRuleTime: (MealReminderRule) -> Unit = {},
    onToggleMealRuleDay: (MealReminderRule, DayOfWeek) -> Unit = { _, _ -> },
    onDeleteMealRule: (MealReminderRule) -> Unit = {},
    onAddMealRule: () -> Unit = {},
    calendarMealsOn: Boolean = false,
    onCalendarMealsOn: (Boolean) -> Unit = {},
    remindersOnTime: Boolean = true,
    onAllowRemindersOnTime: () -> Unit = {},
    weeklyReportOn: Boolean = true,
    weeklyReportDay: DayOfWeek = WeeklyReportSchedule.DEFAULT_DAY,
    weeklyReportTime: String = "11:00",
    onWeeklyReportOn: (Boolean) -> Unit = {},
    onPickWeeklyReportDay: (DayOfWeek) -> Unit = {},
    onPickWeeklyReportTime: () -> Unit = {},
    shortcutsOn: Boolean = false,
    onOpenShortcutsSettings: () -> Unit = {},
    onOpenAppInfo: () -> Unit = {},
    onTestUrgentLow: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    // Saved, so a dialog stays open across rotation; back closes it.
    var showing by rememberSaveable { mutableStateOf<String?>(null) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                }
                Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            SettingRow("Units", unit.label) { showing = UNITS }
            val activeRules = mealReminderRules.filter { it.enabled && it.days.isNotEmpty() }
            val scheduledSummary = when (activeRules.size) {
                0 -> null
                1 -> {
                    val rule = activeRules.single()
                    val time = formatTime(rule.time)
                    if (rule.days.size == DayOfWeek.entries.size) "At $time" else "At $time, ${MealReminder.formatDays(rule.days)}"
                }
                else -> "${activeRules.size} scheduled"
            }
            val mealSummary = listOfNotNull(
                scheduledSummary,
                if (calendarMealsOn) "Calendar meals" else null,
            ).joinToString(" · ").ifEmpty { "Off" }
            SettingRow("Meal reminder", mealSummary) { showing = MEAL_REMINDER }
            val weeklySummary = if (weeklyReportOn) {
                "${weeklyReportDay.getDisplayName(TextStyle.SHORT, Locale.US)} at $weeklyReportTime"
            } else {
                "Off"
            }
            SettingRow("Weekly report", weeklySummary) { showing = WEEKLY_REPORT }
            SettingRow("CamAPS FX shortcuts", if (shortcutsOn) "On" else "Off") { showing = SHORTCUTS }
            SettingRow("Test urgent low alarm", "Check sound, vibration and Do Not Disturb") { onTestUrgentLow() }
        }
    }
    val close = { showing = null }
    when (showing) {
        UNITS -> UnitsDialog(unit = unit, onPick = { close(); onSetUnit(it) }, onDismiss = close)
        MEAL_REMINDER -> MealReminderDialog(
            rules = mealReminderRules,
            formatTime = formatTime,
            onToggleRule = onToggleMealRule,
            onPickRuleTime = onPickMealRuleTime,
            onToggleRuleDay = onToggleMealRuleDay,
            onDeleteRule = onDeleteMealRule,
            onAddRule = onAddMealRule,
            calendarOn = calendarMealsOn,
            onCalendar = onCalendarMealsOn,
            onTime = remindersOnTime,
            onAllowOnTime = onAllowRemindersOnTime,
            onDismiss = close,
        )
        WEEKLY_REPORT -> WeeklyReportDialog(
            on = weeklyReportOn,
            day = weeklyReportDay,
            time = weeklyReportTime,
            onToggle = onWeeklyReportOn,
            onPickDay = onPickWeeklyReportDay,
            onPickTime = onPickWeeklyReportTime,
            onDismiss = close,
        )
        SHORTCUTS -> ShortcutsDialog(
            on = shortcutsOn,
            onOpenSettings = { close(); onOpenShortcutsSettings() },
            onOpenAppInfo = { close(); onOpenAppInfo() },
            onDismiss = close,
        )
    }
}

private const val UNITS = "units"
private const val MEAL_REMINDER = "meal_reminder"
private const val WEEKLY_REPORT = "weekly_report"
private const val SHORTCUTS = "shortcuts"

/** A setting's name and its current value; tapping opens its dialog. */
@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
