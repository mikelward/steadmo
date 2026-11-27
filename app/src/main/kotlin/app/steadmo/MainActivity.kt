package app.steadmo

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private var listenerAllowed by mutableStateOf(false)
    private var notificationsAllowed by mutableStateOf(false)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)) {
                val state by repository.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    listenerAllowed = listenerAllowed,
                    notificationsAllowed = notificationsAllowed,
                    onAllowListener = ::openListenerSettings,
                    onAllowNotifications = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onOpenCamaps = { Camaps.open(this, state.camapsPackage) },
                    onMeal = { grams -> Camaps.openForMeal(this, state.camapsPackage, grams) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        listenerAllowed = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        notificationsAllowed =
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun openListenerSettings() {
        val component = ComponentName(this, CamapsListener::class.java).flattenToString()
        startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component),
        )
    }
}
