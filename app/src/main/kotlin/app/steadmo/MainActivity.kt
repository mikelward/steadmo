package app.steadmo

import android.Manifest
import app.steadmo.core.SteadmoLog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private var listenerAllowed by mutableStateOf(false)
    private var notificationsAllowed by mutableStateOf(false)
    private var disclaimerAccepted by mutableStateOf(false)
    private var showingDisclaimer by mutableStateOf(false)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        disclaimerAccepted = Disclaimer.accepted(this)
        setContent {
            SteadmoTheme {
                if (!disclaimerAccepted) {
                    DisclaimerScreen(
                        onAccept = {
                            Disclaimer.accept(this)
                            disclaimerAccepted = true
                        },
                        onLeave = {
                            SteadmoLog.event("ui: disclaimer declined")
                            finish()
                        },
                    )
                    return@SteadmoTheme
                }
                if (showingDisclaimer) {
                    DisclaimerScreen(onAccept = null, onLeave = { showingDisclaimer = false })
                    return@SteadmoTheme
                }
                val state by repository.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    listenerAllowed = listenerAllowed,
                    notificationsAllowed = notificationsAllowed,
                    onAllowListener = ::openListenerSettings,
                    onAllowNotifications = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onOpenCamaps = { Camaps.open(this, state.camapsPackage) },
                    onMeal = { grams -> Camaps.openForMeal(this, state.camapsPackage, grams) },
                    onReportBug = { Thread { BugReport.share(this) }.start() },
                    onShowDisclaimer = { showingDisclaimer = true },
                    onMinimizeCamaps = { Camaps.openChannelSettings(this, state.camapsPackage, state.camapsChannelId) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        listenerAllowed = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        notificationsAllowed =
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        SteadmoLog.event("ui: resumed; notification access %s, notifications %s", listenerAllowed, notificationsAllowed)
    }

    private fun openListenerSettings() {
        val component = ComponentName(this, CamapsListener::class.java).flattenToString()
        startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component),
        )
    }
}
