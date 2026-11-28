package app.steadmo

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.steadmo.core.GlucoseUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** On a short screen (a small phone in landscape), every dialog's last line can be scrolled to. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h240dp-land")
class DialogScrollTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun scrollsTo(text: String, dialog: @Composable () -> Unit) {
        compose.setContent { SteadmoTheme(dark = false) { dialog() } }
        compose.onNodeWithText(text, substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun shortcuts() = scrollsTo("allow restricted settings") {
        ShortcutsDialog(on = false, onOpenSettings = {}, onOpenAppInfo = {}, onDismiss = {})
    }

    @Test fun about() = scrollsTo("kotlinx.coroutines") {
        AboutDialog(version = "1.0", onSourceCode = {}, onDismiss = {})
    }

    @Test fun mealReminder() = scrollsTo("restaurant booking") {
        MealReminderDialog(
            rules = listOf(app.steadmo.core.MealReminder.defaultRule()),
            onToggleRule = { _, _ -> }, onPickRuleTime = {}, onToggleRuleDay = { _, _ -> },
            onDeleteRule = {}, onAddRule = {}, onDismiss = {},
        )
    }

    @Test fun units() = scrollsTo(GlucoseUnit.entries.last().label) {
        UnitsDialog(unit = GlucoseUnit.MMOL, onPick = {}, onDismiss = {})
    }
}
