package app.steadmo

import app.steadmo.core.SteadmoLog
import android.content.Context
import app.steadmo.core.AlertKind
import app.steadmo.core.AlertPolicy
import app.steadmo.core.RangeSide
import app.steadmo.core.side
import app.steadmo.core.CamapsStatus
import app.steadmo.core.GlucoseUnit
import app.steadmo.core.Reading
import app.steadmo.core.ReadingLog
import app.steadmo.core.Trend
import app.steadmo.core.TrendCalculator
import app.steadmo.core.UrgentLow
import app.steadmo.core.Treatment
import app.steadmo.core.isLow
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor
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
    /** The channel of CamAPS FX's "My diabetes info", once a reading has come from it. */
    val camapsChannelId: String? = null,
    val camapsChannelImportance: Int? = null,
    val listenerConnected: Boolean = false,
    /** "Treated" times, oldest first, for the last day (SPEC.md §4.10). */
    val treatments: List<Long> = emptyList(),
    /** How glucose is shown; readings are kept in mmol/L whatever this is. */
    val unit: GlucoseUnit = GlucoseUnit.MMOL,
) {
    val latest: Reading? get() = readings.lastOrNull()
}

/**
 * The readings and CamAPS status, on the device only: a day of readings in
 * `files/readings.csv`, nothing sent anywhere (SPEC.md §5).
 */
class GlucoseRepository(
    context: Context,
    private val notifier: StatusNotifier,
    private val writer: Executor = Executors.newSingleThreadExecutor(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val file = File(context.filesDir, "readings.csv")
    private val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val log = load()
    private val alertPrefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
    /** When each kind last alerted, kept across restarts so a restart can't repeat one early. */
    private val lastAlerted: MutableMap<AlertKind, Long> = AlertKind.entries
        .mapNotNull { kind -> alertPrefs.getLong(kind.name, 0L).takeIf { it > 0 }?.let { kind to it } }
        .toMap(mutableMapOf())
    private val alerts = AlertPolicy(lastAlerted)
    private var shownAlert: AlertKind? = null
    /** When the reading behind [shownAlert] was taken, so it can be redrawn with the same expiry. */
    private var shownAlertTime: Long? = null

    private val _state = MutableStateFlow(
        GlucoseState(readings = log.all, trend = TrendCalculator.trend(log.all), unit = loadUnit(), treatments = loadTreatments()),
    )
    val state: StateFlow<GlucoseState> = _state

    /** Shows glucose in [unit] from now on, and redraws the status notification in it. */
    @Synchronized
    fun setUnit(unit: GlucoseUnit) {
        SteadmoLog.event("settings: units %s", unit.label)
        settings.edit().putString(KEY_UNIT, unit.name).apply()
        _state.update { it.copy(unit = unit) }
        val latest = _state.value.latest ?: return
        notifier.showStatus(_state.value, promote())
        // Redraw a shown alert in the new unit, quietly and with its original expiry.
        val time = shownAlertTime
        val redrawn = alerts.applying(latest, _state.value.trend, unit)
        // Only one still on screen: a dismissed alert must not come back.
        if (redrawn != null && time != null && redrawn.kind == shownAlert && notifier.alertShowing()) {
            notifier.showAlert(redrawn, time, quiet = true)
        }
        // Likewise the urgent low alarm, without restarting its sound.
        if (notifier.urgentLowShowing()) notifier.showUrgentLow(latest, unit, quiet = true)
    }

    /** The urgent low alarm for a fresh reading (SPEC.md §4.8). */
    private fun urgentLow(latest: Reading, fresh: Boolean) {
        when (UrgentLow.decide(latest, UrgentLowAck.get(appContext))) {
            // Only a reading posted just now sounds; a replay may still clear one.
            UrgentLow.Decision.ALERT ->
                if (fresh) {
                    UrgentLowAck.startAlerting(appContext, latest.timeMillis)
                    val takeOver = UrgentLow.escalates(UrgentLowAck.alertingSince(appContext), latest.timeMillis)
                    notifier.showUrgentLow(latest, _state.value.unit, fullScreen = takeOver)
                } else {
                    SteadmoLog.event("urgent low: replayed reading; not sounding")
                }
            UrgentLow.Decision.SNOOZED -> SteadmoLog.event("urgent low: acknowledged recently; not sounding")
            UrgentLow.Decision.CLEAR -> {
                notifier.cancelUrgentLow()
                UrgentLowAck.clear(appContext)
            }
        }
    }

    /**
     * Records a hypo treatment at [at]: on the graph, holding off the rise it
     * explains, quieting the low alerts it answers, and a recheck in 15 minutes.
     */
    @Synchronized
    fun recordTreatment(at: Long) {
        val kept = (_state.value.treatments + at).filter { at - it < Treatment.RETENTION_MILLIS }
        SteadmoLog.event("treated: at %s (%s in the last day)", at, kept.size)
        settings.edit().putString(KEY_TREATMENTS, kept.joinToString(",")).apply()
        _state.update { it.copy(treatments = kept) }
        // From the notification too: a restarted process doesn't remember shownAlert.
        if (shownAlert?.isLow == true || notifier.shownAlertKind()?.isLow == true) {
            notifier.cancelAlert()
            shownAlert = null
            shownAlertTime = null
        }
        notifier.cancelUrgentLow()
        UrgentLowAck.acknowledge(appContext, at)
        Recheck.schedule(appContext, at)
    }

    private fun loadTreatments(): List<Long> = settings.getString(KEY_TREATMENTS, null)
        ?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()
        .filter { clock() - it < Treatment.RETENTION_MILLIS }

    /** Drops treatments older than a day, as readings go by. */
    private fun pruneTreatments() {
        val kept = _state.value.treatments.filter { clock() - it < Treatment.RETENTION_MILLIS }
        if (kept.size == _state.value.treatments.size) return
        settings.edit().putString(KEY_TREATMENTS, kept.joinToString(",")).apply()
        _state.update { it.copy(treatments = kept) }
    }

    private fun loadUnit(): GlucoseUnit {
        val name = settings.getString(KEY_UNIT, null) ?: return GlucoseUnit.MMOL
        return GlucoseUnit.entries.firstOrNull { it.name == name } ?: run {
            SteadmoLog.warning("settings: unknown units %s; using mmol/L", name)
            GlucoseUnit.MMOL
        }
    }

    /** A Live Update while out of range, or while a low is on the way. */
    private fun promote(): Boolean {
        val current = _state.value.latest
        val activeAlert = shownAlert ?: notifier.shownAlertKind()
        return current != null && (current.side() != RangeSide.IN_RANGE || activeAlert == AlertKind.LOW_SOON)
    }

    fun setListenerConnected(connected: Boolean) = _state.update { it.copy(listenerConnected = connected) }

    /** CamAPS FX's channel settings changed: the minimize card follows at once. */
    fun onCamapsChannel(channelId: String?, importance: Int) {
        val now = _state.value
        if (now.camapsChannelId == channelId && now.camapsChannelImportance == importance) return
        SteadmoLog.event("listener: CamAPS FX channel %s importance now %s", channelId, importance)
        _state.update { it.copy(camapsChannelId = channelId, camapsChannelImportance = importance) }
    }

    @Synchronized
    fun onCamapsNotification(dump: NotificationDump, status: CamapsStatus?) {
        if (status == null) {
            _state.update {
                it.copy(
                    lastDump = dump, camapsPackage = dump.packageName,
                    camapsChannelId = dump.channelId, camapsChannelImportance = dump.channelImportance,
                )
            }
            return
        }
        val mmol = status.mmol
        val added = mmol != null && log.add(Reading(dump.postTime, mmol, status.bound))
        val readings = log.all
        val trend = TrendCalculator.trend(readings)
        SteadmoLog.event(
            "readings: %s at %s (%s readings), trend %s %s/min",
            if (added) "added" else "unchanged", dump.postTime, readings.size, trend?.arrow, trend?.mmolPerMinute,
        )
        _state.update {
            it.copy(
                readings = readings,
                trend = trend,
                status = status,
                statusTime = dump.postTime,
                camapsPackage = dump.packageName,
                lastDump = dump,
                camapsChannelId = dump.channelId,
                camapsChannelImportance = dump.channelImportance,
            )
        }
        if (added) {
            save(log.encode())
            pruneTreatments()
            val latest = log.latest
            latest?.let { SignalWatch.onReading(appContext, it.timeMillis, notifier, clock()) }
            // A newer reading that no longer calls for the shown alert clears it.
            val activeAlert = shownAlert ?: notifier.shownAlertKind()
            if (activeAlert != null && latest != null && alerts.applying(latest, trend, _state.value.unit)?.kind != activeAlert) {
                SteadmoLog.event("alerts: %s no longer applies; cleared", activeAlert)
                notifier.cancelAlert()
                shownAlert = null
                shownAlertTime = null
            }
            // Only a reading posted just now alerts. A replay (e.g. on reconnect)
            // is history: its projection was about a moment that has passed.
            val age = latest?.let { clock() - it.timeMillis }
            if (age != null && age >= ALERT_MAX_AGE_MILLIS) {
                SteadmoLog.event("alerts: reading is %s ms old; a replay doesn't alert", age)
            }
            latest?.let { urgentLow(it, fresh = clock() - it.timeMillis < ALERT_MAX_AGE_MILLIS) }
            latest?.takeIf { clock() - it.timeMillis < ALERT_MAX_AGE_MILLIS }?.let {
                alerts.evaluate(it, trend, _state.value.unit, _state.value.treatments.lastOrNull())?.let { alert ->
                    SteadmoLog.event("alerts: %s (%s)", alert.kind, alert.title)
                    notifier.showAlert(alert, it.timeMillis)
                    shownAlert = alert.kind
                    shownAlertTime = it.timeMillis
                    alertPrefs.edit().putLong(alert.kind.name, lastAlerted.getValue(alert.kind)).apply()
                }
            }
        }
        notifier.showStatus(_state.value, promote())
    }

    companion object {
        /** CamAPS posts as a reading arrives; anything older is a replay. */
        const val ALERT_MAX_AGE_MILLIS = 2 * 60_000L
        private const val KEY_UNIT = "unit"
        private const val KEY_TREATMENTS = "treatments"
    }

    private fun load(): ReadingLog = try {
        if (file.exists()) ReadingLog.decode(file.readText()) else ReadingLog()
    } catch (e: IOException) {
        SteadmoLog.failure(e, "readings: couldn't read the saved readings; starting empty")
        ReadingLog()
    }

    private fun save(text: String) = writer.execute {
        try {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) SteadmoLog.error("readings: couldn't replace the saved readings")
        } catch (e: IOException) {
            SteadmoLog.failure(e, "readings: couldn't save the readings")
        }
    }
}
