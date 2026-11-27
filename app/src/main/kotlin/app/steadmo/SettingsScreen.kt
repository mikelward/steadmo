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

/** ⋮ → Settings: Steadmo's own settings. Back, or the arrow, returns home. */
@Composable
fun SettingsScreen(unit: GlucoseUnit, onSetUnit: (GlucoseUnit) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var showingUnits by rememberSaveable { mutableStateOf(false) }
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
            Column(
                modifier = Modifier.fillMaxWidth().clickable { showingUnits = true }.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("Units", style = MaterialTheme.typography.bodyLarge)
                Text(unit.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (showingUnits) {
        UnitsDialog(
            unit = unit,
            onPick = { showingUnits = false; onSetUnit(it) },
            onDismiss = { showingUnits = false },
        )
    }
}
