package app.steadmo.core

/**
 * What CamAPS FX's "My diabetes info" notification says. The notification is
 * a custom layout, so the app hands over the text of every view in it, in
 * layout order, e.g. `["CamAPS FX", "Auto mode", "On", "11.2", "mmol/L"]`.
 */
data class CamapsStatus(
    /** Glucose in mmol/L, or null when the notification shows none. */
    val mmol: Double?,
    /** Set when CamAPS shows a sensor limit such as "<2.2" rather than a value. */
    val bound: Bound?,
    val unit: GlucoseUnit,
    /** The text after "Auto mode", e.g. "On", "Off", "Boost". Null if absent. */
    val autoMode: String?,
)

enum class GlucoseUnit(val label: String) {
    MMOL("mmol/L"),
    MGDL("mg/dL"),
}

object CamapsParser {
    /** CamAPS FX ships one package per unit and market; all share this prefix. */
    const val PACKAGE_PREFIX = "com.camdiab.fx_alert"

    private val value = Regex("""^\s*([<>])?\s*(\d{1,3}(?:[.,]\d)?)\s*(mmol/L|mg/dL)?\s*$""", RegexOption.IGNORE_CASE)
    private val autoModeInline = Regex("""^\s*auto\s*mode\s*[:\-]?\s*(.+?)\s*$""", RegexOption.IGNORE_CASE)

    fun parse(texts: List<String>): CamapsStatus? {
        val cleaned = texts.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return null

        val unit = when {
            cleaned.any { it.contains("mg/dL", ignoreCase = true) } -> GlucoseUnit.MGDL
            else -> GlucoseUnit.MMOL
        }

        var mmol: Double? = null
        var bound: Bound? = null
        for (text in cleaned) {
            val match = value.matchEntire(text) ?: continue
            val number = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: continue
            val asMmol = if (unit == GlucoseUnit.MGDL) number / MGDL_PER_MMOL else number
            // Rejects stray numbers such as an IOB or a time.
            if (asMmol !in 1.0..35.0) continue
            if (unit == GlucoseUnit.MMOL && !match.groupValues[2].contains('.') && !match.groupValues[2].contains(',')) {
                // mmol/L values always carry one decimal; a bare integer is something else.
                continue
            }
            mmol = asMmol
            bound = when (match.groupValues[1]) {
                "<" -> Bound.BELOW
                ">" -> Bound.ABOVE
                else -> null
            }
            break
        }

        val autoMode = autoModeOf(cleaned)
        if (mmol == null && autoMode == null) return null
        return CamapsStatus(mmol, bound, unit, autoMode)
    }

    private fun autoModeOf(texts: List<String>): String? {
        for ((i, text) in texts.withIndex()) {
            if (text.equals("Auto mode", ignoreCase = true)) {
                return texts.getOrNull(i + 1)?.takeIf { value.matchEntire(it) == null && !isUnit(it) }
            }
            autoModeInline.matchEntire(text)?.let { return it.groupValues[1] }
        }
        return null
    }

    private fun isUnit(text: String) =
        text.equals("mmol/L", ignoreCase = true) || text.equals("mg/dL", ignoreCase = true)
}
