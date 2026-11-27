package app.steadmo

import android.content.Context
import android.util.Log
import app.steadmo.core.AlertPolicy
import app.steadmo.core.CamapsStatus
import app.steadmo.core.Reading
import app.steadmo.core.ReadingLog
import app.steadmo.core.Trend
import app.steadmo.core.TrendCalculator
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class GlucoseState(
    val readings: List<Reading> = emptyList(),
    val trend: Trend? = null,
    val status: CamapsStatus? = null,
    val statusTime: Long? = null,
    val camapsPackage: String? = null,
    val lastDump: NotificationDump? = null,
    val listenerConnected: Boolean = false,
) {
    val latest: Reading? get() = readings.lastOrNull()
}

/**
 * The readings and CamAPS status, on the device only: a day of readings in
 * `files/readings.csv`, nothing sent anywhere (SPEC.md §5).
 */
class GlucoseRepository(context: Context, private val notifier: StatusNotifier) {
    private val file = File(context.filesDir, "readings.csv")
    private val writer = Executors.newSingleThreadExecutor()
    private val log = load()
    private val alerts = AlertPolicy()

    private val _state = MutableStateFlow(GlucoseState(readings = log.all, trend = TrendCalculator.trend(log.all)))
    val state: StateFlow<GlucoseState> = _state

    fun setListenerConnected(connected: Boolean) = _state.update { it.copy(listenerConnected = connected) }

    @Synchronized
    fun onCamapsNotification(dump: NotificationDump, status: CamapsStatus?) {
        if (status == null) {
            _state.update { it.copy(lastDump = dump, camapsPackage = dump.packageName) }
            return
        }
        val mmol = status.mmol
        val added = mmol != null && log.add(Reading(dump.postTime, mmol))
        val readings = log.all
        val trend = TrendCalculator.trend(readings)
        _state.update {
            it.copy(
                readings = readings,
                trend = trend,
                status = status,
                statusTime = dump.postTime,
                camapsPackage = dump.packageName,
                lastDump = dump,
            )
        }
        notifier.showStatus(_state.value)
        if (added) {
            save(log.encode())
            log.latest?.let { latest -> alerts.evaluate(latest, trend)?.let(notifier::showAlert) }
        }
    }

    private fun load(): ReadingLog = try {
        if (file.exists()) ReadingLog.decode(file.readText()) else ReadingLog()
    } catch (e: IOException) {
        Log.e(SteadmoApp.TAG, "Couldn't read the saved readings; starting empty", e)
        ReadingLog()
    }

    private fun save(text: String) = writer.execute {
        try {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) Log.e(SteadmoApp.TAG, "Couldn't replace the saved readings")
        } catch (e: IOException) {
            Log.e(SteadmoApp.TAG, "Couldn't save the readings", e)
        }
    }
}
