package app.steadmo.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Glucose is stored and computed in mmol/L (SPEC.md §3); these turn it into
 * the user's display unit at the last moment.
 */

/** e.g. "8.9" in mmol/L, "160" in mg/dL. */
fun GlucoseUnit.value(mmol: Double): String = when (this) {
    GlucoseUnit.MMOL -> String.format(Locale.US, "%.1f", mmol)
    GlucoseUnit.MGDL -> (mmol * MGDL_PER_MMOL).roundToInt().toString()
}

/** A reading, with its sign if it is a sensor limit, e.g. "<2.2" or "<40". */
fun GlucoseUnit.reading(r: Reading): String = (r.bound?.symbol ?: "") + value(r.mmol)

/** A signed rate per minute without its unit, e.g. "+0.12" or "−2.2". */
fun GlucoseUnit.rate(mmolPerMinute: Double): String {
    val sign = if (mmolPerMinute >= 0) "+" else "−"
    val magnitude = abs(mmolPerMinute)
    return sign + when (this) {
        GlucoseUnit.MMOL -> String.format(Locale.US, "%.2f", magnitude)
        GlucoseUnit.MGDL -> String.format(Locale.US, "%.1f", magnitude * MGDL_PER_MMOL)
    }
}

/** e.g. "mmol/L/min". */
val GlucoseUnit.rateLabel: String get() = "$label/min"
