package app.steadmo

import app.steadmo.core.Bound
import app.steadmo.core.CamapsStatus
import app.steadmo.core.GlucoseUnit
import app.steadmo.core.Reading
import app.steadmo.core.TrendCalculator

/** Fixed states for UI tests and screenshots, ending at [NOW]. */
object Samples {
    const val NOW = 1_764_250_000_000L

    val dump = NotificationDump(
        postTime = NOW,
        packageName = CAMAPS,
        channelId = "my_diabetes_info",
        viewTexts = listOf("CamAPS FX", "Auto mode", "On", "9.4", "mmol/L"),
        extrasTexts = emptyList(),
        images = listOf("arrow rotation=0.0 description=null pixels=1a2b3c"),
    )

    private val threeHours = listOf(
        6.1, 6.0, 5.9, 5.8, 5.8, 5.9, 6.2, 6.8, 7.6, 8.4, 9.0, 9.3, 9.2, 8.9, 8.5, 8.0, 7.5, 7.1,
        6.8, 6.6, 6.5, 6.6, 6.8, 7.0, 7.6, 8.2, 8.8, 9.4,
    )

    private fun state(values: List<Double>, mode: String = "On", lastBound: Bound? = null): GlucoseState {
        val readings = values.mapIndexed { i, v ->
            Reading(NOW - (values.size - 1 - i) * 5 * MINUTE, v, if (i == values.lastIndex) lastBound else null)
        }
        return GlucoseState(
            readings = readings,
            trend = TrendCalculator.trend(readings),
            status = CamapsStatus(values.last(), lastBound, GlucoseUnit.MMOL, mode),
            statusTime = NOW,
            camapsPackage = CAMAPS,
            lastDump = dump,
            listenerConnected = true,
        )
    }

    fun rising() = state(threeHours)
    fun fallingFast() = state(listOf(10.6, 9.6, 8.6, 7.6, 6.6, 5.6))
    fun single() = state(listOf(6.4))
    fun belowSensor() = state(listOf(3.4, 2.8, 2.2), lastBound = Bound.BELOW)
}
