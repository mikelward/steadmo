package app.steadmo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import app.steadmo.core.urgentLow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.steadmo.core.SteadmoLog
import app.steadmo.core.reading

/**
 * The urgent low alarm, full screen and over the lock screen, once the
 * notification has been ignored for 5 minutes (SPEC.md §4.8).
 */
class UrgentLowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        SteadmoLog.warning("urgent low: full screen shown")
        setContent {
            SteadmoTheme {
                val state by repository.state.collectAsStateWithLifecycle()
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(30_000)
                        now = System.currentTimeMillis()
                    }
                }
                val latest = state.latest
                // Recovered (or nothing to show): this alarm no longer applies.
                LaunchedEffect(latest) {
                    if (latest?.urgentLow() != true) {
                        SteadmoLog.event("urgent low: full screen closed; no longer urgent")
                        finish()
                    }
                }
                if (latest == null) return@SteadmoTheme
                UrgentLowScreen(
                    value = "${state.unit.reading(latest)} ${state.unit.label}",
                    age = age(now - latest.timeMillis),
                    stale = now - latest.timeMillis > STALE_MILLIS,
                    onTreated = {
                        SteadmoLog.event("urgent low: Treated from full screen")
                        repository.recordTreatment(System.currentTimeMillis())
                        finish()
                    },
                    onOk = {
                        SteadmoLog.event("urgent low: OK from full screen")
                        UrgentLowAck.acknowledge(this, System.currentTimeMillis())
                        StatusNotifier(this).cancelUrgentLow()
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        /** Like the home screen: older than this, the value greys out. */
        const val STALE_MILLIS = 10 * 60_000L

        fun age(millis: Long): String = (millis / 60_000).let { if (it < 1) "just now" else "$it min ago" }
    }
}

@Composable
internal fun UrgentLowScreen(value: String, age: String, stale: Boolean, onTreated: () -> Unit, onOk: () -> Unit) {
    val valueColor = if (stale) MaterialTheme.colorScheme.outline else LowColor
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      // Scrolls on short screens (landscape), so Treated and OK are always reachable.
      BoxWithConstraints(modifier = Modifier.safeDrawingPadding()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Urgent low", style = MaterialTheme.typography.headlineMedium, color = LowColor)
            Text(value, fontSize = 64.sp, fontWeight = FontWeight.Bold, color = valueColor)
            Text(
                if (stale) "Taken $age, with nothing since. Check your glucose." else age,
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Treat it now.", style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = onTreated,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = LowColor, contentColor = androidx.compose.ui.graphics.Color.White),
            ) { Text("Treated", style = MaterialTheme.typography.titleMedium) }
            OutlinedButton(onClick = onOk, modifier = Modifier.fillMaxWidth()) { Text("OK") }
        }
      }
    }
}
