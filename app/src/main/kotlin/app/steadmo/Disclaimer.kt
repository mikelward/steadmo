package app.steadmo

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.steadmo.core.SteadmoLog

/**
 * The warning shown before first use, and again whenever its wording changes
 * ([VERSION]). Steadmo can't be used until it is accepted (SPEC.md §1).
 */
object Disclaimer {
    /** Bump when the wording changes, so everyone accepts the new text. */
    const val VERSION = 3

    val points = listOf(
        "Use Steadmo at your own risk. It comes with no warranty of any kind.",
        "It's made for the developer's family. It hasn't been tested for your circumstances, " +
            "your phone or your version of CamAPS FX.",
        "It isn't a medical device and isn't approved by any regulator.",
        "It can be wrong or out of date. It copies what CamAPS FX shows, and can misread it, miss " +
            "readings or stop updating. CamAPS FX or Android updates can break it without warning.",
        "Its alerts can be late or missing. Your phone can stop it in the background, block its " +
            "notifications, or silence them in Do Not Disturb.",
        "Its alerts are designed to be actionable and avoid false alarms rather than warn on every " +
            "reading. That can be unsafe if the thresholds are wrong for you or if you're " +
            "inexperienced managing lows and highs.",
        "Its trend arrows and predictions are its own estimates. They are not CamAPS FX's and can " +
            "disagree with it.",
        "Don't base treatment decisions on it. Check CamAPS FX or your sensor, and keep their alarms on.",
        "If you ever disable or uninstall Steadmo, remember to restore CamAPS FX's notification " +
            "settings and make sure its alerts are on — Android won't remind you.",
        "Steadmo never doses insulin. Its buttons open CamAPS FX, where you check and confirm.",
    )

    private const val PREFS = "steadmo"
    private const val KEY = "disclaimer_accepted_version"
    private const val KEY_AT = "disclaimer_accepted_at"

    fun accepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0) >= VERSION

    /** When the current wording was accepted, as a record; null if it hasn't been. */
    fun acceptedAt(context: Context): Long? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_AT, 0L)
            .takeIf { it > 0 && accepted(context) }

    fun accept(context: Context, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY, VERSION)
            .putLong(KEY_AT, now)
            .apply()
        app.steadmo.widget.WidgetUpdates.bump()
        SteadmoLog.event("ui: disclaimer version %s accepted at %s", VERSION, now)
    }
}

/** The checkbox the user must tick before Continue is enabled. */
const val AGREEMENT = "I have read this and accept the risk"

/**
 * The disclaimer. Before it is accepted, Continue stays disabled until the
 * user ticks [AGREEMENT]; Leave closes the app. Once accepted it can be read
 * again from the home screen, with Close.
 */
@Composable
fun DisclaimerScreen(onAccept: (() -> Unit)?, onLeave: () -> Unit) {
    // Read again from ⋮, back returns home. On first run, back leaves the app, as Leave does.
    if (onAccept == null) androidx.activity.compose.BackHandler(onBack = onLeave)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Before you use Steadmo",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Disclaimer.points.forEach { point ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyLarge)
                    Text(point, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (onAccept != null) {
                var agreed by rememberSaveable { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = agreed, role = Role.Checkbox, onValueChange = { agreed = it }),
                ) {
                    Checkbox(checked = agreed, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(AGREEMENT, style = MaterialTheme.typography.bodyLarge)
                }
                Button(onClick = onAccept, enabled = agreed, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
                OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("Leave") }
            } else {
                Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}
