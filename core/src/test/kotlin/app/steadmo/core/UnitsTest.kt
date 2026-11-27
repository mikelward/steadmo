package app.steadmo.core

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test fun `mmol per L shows one decimal`() {
        assertEquals("8.9", GlucoseUnit.MMOL.value(8.94))
        assertEquals("<2.2", GlucoseUnit.MMOL.reading(Reading(0, 2.2, Bound.BELOW)))
        assertEquals("+0.12", GlucoseUnit.MMOL.rate(0.12))
        assertEquals("−0.05", GlucoseUnit.MMOL.rate(-0.05))
        assertEquals("mmol/L/min", GlucoseUnit.MMOL.rateLabel)
    }

    @Test fun `mg per dL shows whole numbers and round-trips CamAPS FX's values`() {
        assertEquals("160", GlucoseUnit.MGDL.value(8.9))
        assertEquals("70", GlucoseUnit.MGDL.value(3.9))
        // CamAPS FX in mg/dL shows "<40"; Steadmo stores 40 / 18.0182 and shows it back.
        assertEquals("<40", GlucoseUnit.MGDL.reading(Reading(0, 40 / MGDL_PER_MMOL, Bound.BELOW)))
        assertEquals(">400", GlucoseUnit.MGDL.reading(Reading(0, 400 / MGDL_PER_MMOL, Bound.ABOVE)))
        assertEquals("+2.2", GlucoseUnit.MGDL.rate(0.12))
        assertEquals("−0.9", GlucoseUnit.MGDL.rate(-0.05))
        assertEquals("mg/dL/min", GlucoseUnit.MGDL.rateLabel)
    }
}
