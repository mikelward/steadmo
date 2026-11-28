package app.steadmo

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.steadmo.core.GlucoseUnit
import app.steadmo.core.MealReminder
import app.steadmo.core.MealReminderRule
import app.steadmo.core.WeeklyReport
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * At Android's maximum accessibility font scale (2.0x) on a compact phone width,
 * short labels, chips, pills and action buttons wrap onto new rows rather than
 * breaking mid-word or clipping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LargeFontTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun setLargeFontContent(fontScale: Float = 2.0f, content: @Composable () -> Unit) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                SteadmoTheme(dark = false) { content() }
            }
        }
    }

    private fun SemanticsNodeInteraction.assertSingleLine(label: String) {
        val node = fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        val action = node.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue("$label has no text layout result", action != null && action(results) && results.isNotEmpty())
        val layout = results.first()
        assertEquals("$label wrapped onto ${layout.lineCount} lines", 1, layout.lineCount)
        val visibleWidth = minOf(layout.size.width.toFloat(), node.boundsInRoot.width)
        assertTrue(
            "$label clipped horizontally (${layout.getLineRight(0)} > $visibleWidth)",
            layout.getLineRight(0) <= visibleWidth + 1f,
        )
    }

    @Test fun weeklyReportDialog() {
        setLargeFontContent {
            WeeklyReportDialog(
                on = true,
                day = DayOfWeek.SATURDAY,
                time = "11:00",
                onToggle = {},
                onPickDay = {},
                onPickTime = {},
                onDismiss = {},
            )
        }
        DayOfWeek.entries.forEach { day ->
            val short = day.getDisplayName(TextStyle.SHORT, Locale.US)
            val full = day.getDisplayName(TextStyle.FULL, Locale.US)
            compose.onNodeWithContentDescription(full).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(short, useUnmergedTree = true).assertSingleLine(short)
        }
        compose.onNodeWithText("Change").performScrollTo().assertSingleLine("Change")
    }

    @Test fun homeScreen() {
        setLargeFontContent {
            HomeScreen(
                state = Samples.rising(),
                listenerAllowed = false,
                notificationsAllowed = true,
                onAllowListener = {},
                onAllowNotifications = {},
                onOpenCamaps = {},
                onMeal = {},
                clock = { Samples.NOW },
                ticking = false,
            )
        }
        compose.onNodeWithText("Open App info").performScrollTo().assertSingleLine("Open App info")
        compose.onNodeWithText("9.4").performScrollTo().assertSingleLine("9.4")
        compose.onNodeWithText("Boost").performScrollTo().assertSingleLine("Boost")
        compose.onNodeWithText("Ease-off").performScrollTo().assertSingleLine("Ease-off")
        listOf("20 g", "40 g", "45 g", "Other").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().assertSingleLine(label)
        }
    }

    @Test fun homeScreenCompactWidthDefaultFont() {
        setLargeFontContent(fontScale = 1.0f) {
            HomeScreen(
                state = Samples.rising(),
                listenerAllowed = true,
                notificationsAllowed = true,
                onAllowListener = {},
                onAllowNotifications = {},
                onOpenCamaps = {},
                onMeal = {},
                clock = { Samples.NOW },
                ticking = false,
            )
        }
        compose.onNodeWithText("Boost", useUnmergedTree = true).performScrollTo().assertSingleLine("Boost")
        compose.onNodeWithText("Ease-off", useUnmergedTree = true).performScrollTo().assertSingleLine("Ease-off")
        val horizontalPaddingPx = with(compose.density) { 48.dp.toPx() }
        listOf("20 g", "40 g", "45 g", "Other").forEach { label ->
            val textNode = compose.onNodeWithText(label, useUnmergedTree = true).performScrollTo()
            textNode.assertSingleLine(label)
            val buttonWidth = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.width
            val textWidth = textNode.fetchSemanticsNode().boundsInRoot.width
            assertTrue(
                "$label button ($buttonWidth px) too narrow for text ($textWidth px) + padding ($horizontalPaddingPx px)",
                buttonWidth >= textWidth + horizontalPaddingPx - 1f,
            )
        }
    }

    @Test fun mealReminderDialog() {
        val rules = listOf(
            MealReminder.defaultRule(),
            MealReminderRule(
                id = 2L,
                time = LocalTime.of(12, 30),
                days = setOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY,
                ),
            ),
        )
        setLargeFontContent {
            MealReminderDialog(
                rules = rules,
                onToggleRule = { _, _ -> },
                onPickRuleTime = {},
                onToggleRuleDay = { _, _ -> },
                onDeleteRule = {},
                onAddRule = {},
                onDismiss = {},
            )
        }
        compose.onNodeWithText("18:00").performScrollTo().assertSingleLine("18:00")
        compose.onNodeWithText("Every day").performScrollTo().assertSingleLine("Every day")
        compose.onNodeWithText("12:30").performScrollTo().assertSingleLine("12:30")
        compose.onNodeWithText("Weekdays").performScrollTo().assertSingleLine("Weekdays")
        // Verify day pills grow to fit their letter without clipping vertically.
        val wed = compose.onAllNodesWithText("W", useUnmergedTree = true).onFirst()
        wed.assertSingleLine("W")
        val pillBounds = compose.onAllNodesWithContentDescription("Wednesday").onFirst().fetchSemanticsNode().boundsInRoot
        val textBounds = wed.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Day pill height (${pillBounds.height}) is smaller than text height (${textBounds.height})",
            pillBounds.height >= textBounds.height,
        )
    }

    @Test fun weeklyReportScreen() {
        val zone = ZoneId.of("UTC")
        val end = ZonedDateTime.of(2026, 11, 28, 11, 0, 0, 0, zone).toInstant().toEpochMilli()
        val summary = WeeklyReport.Summary(
            periodStart = end - WeeklyReport.WINDOW_MILLIS,
            periodEnd = end,
            readingCount = 1940,
            coveragePercent = 96,
            averageMmol = 7.4,
            lowPercent = 6,
            veryLowPercent = 1,
            inRangePercent = 74,
            highPercent = 20,
            cvPercent = 33,
            treatmentCount = 2,
            comparison = WeeklyReport.WeekComparison(
                priorInRangePercent = 69,
                inRangeDeltaPercent = 5,
                averageDeltaMmol = -0.3,
                lowDeltaPercent = 1,
                highDeltaPercent = -6,
            ),
            issues = emptyList(),
        )
        setLargeFontContent {
            WeeklyReportScreen(summary = summary, unit = GlucoseUnit.MMOL, zone = zone, onShare = {}, onBack = {})
        }
        compose.onNodeWithText("74% in range").performScrollTo().assertSingleLine("74% in range")
        compose.onNodeWithText("+5% vs last week").performScrollTo().assertSingleLine("+5% vs last week")
        compose.onNodeWithText("Low (<3.9)").performScrollTo().assertSingleLine("Low (<3.9)")
        compose.onNodeWithText("In range").performScrollTo().assertSingleLine("In range")
        compose.onNodeWithText("High (>10.0)").performScrollTo().assertSingleLine("High (>10.0)")
    }

    @Test fun urgentLowScreen() {
        setLargeFontContent {
            UrgentLowScreen("2.8 mmol/L", "just now", stale = false, onTreated = {}, onOk = {})
        }
        compose.onNodeWithText("2.8 mmol/L").performScrollTo().assertSingleLine("2.8 mmol/L")
    }

    @Test fun aboutDialogButtons() {
        setLargeFontContent {
            AboutDialog(version = "48+abc1234", onSourceCode = {}, onDismiss = {})
        }
        compose.onNodeWithText("Licence").assertSingleLine("Licence")
        compose.onNodeWithText("Source code").assertSingleLine("Source code")
        compose.onNodeWithText("Close").assertSingleLine("Close")
    }

    @Test fun shortcutsDialogButtons() {
        setLargeFontContent {
            ShortcutsDialog(on = false, onOpenSettings = {}, onOpenAppInfo = {}, onDismiss = {})
        }
        compose.onNodeWithText("App info").assertSingleLine("App info")
        compose.onNodeWithText("Close").assertSingleLine("Close")
        compose.onNodeWithText("Open settings").assertSingleLine("Open settings")
    }
}
