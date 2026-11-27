package app.steadmo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CamapsParserTest {
    @Test fun myDiabetesInfoAsShownOnAPixel() {
        val status = CamapsParser.parse(listOf("CamAPS FX", "Auto mode", "On", "11.2", "mmol/L"))!!
        assertEquals(11.2, status.mmol!!, 1e-9)
        assertEquals("On", status.autoMode)
        assertEquals(GlucoseUnit.MMOL, status.unit)
        assertFalse(status.outOfRange)
    }

    @Test fun valueAndUnitInOneView() {
        val status = CamapsParser.parse(listOf("Auto mode: Off", "6.4 mmol/L"))!!
        assertEquals(6.4, status.mmol!!, 1e-9)
        assertEquals("Off", status.autoMode)
    }

    @Test fun commaDecimal() {
        assertEquals(5.3, CamapsParser.parse(listOf("Auto mode", "On", "5,3", "mmol/L"))!!.mmol!!, 1e-9)
    }

    @Test fun mgdlConvertsToMmol() {
        val status = CamapsParser.parse(listOf("Auto mode", "On", "180", "mg/dL"))!!
        assertEquals(GlucoseUnit.MGDL, status.unit)
        assertEquals(9.99, status.mmol!!, 0.01)
    }

    @Test fun boundIsMarkedOutOfRange() {
        val status = CamapsParser.parse(listOf("Auto mode", "On", "<2.2", "mmol/L"))!!
        assertEquals(2.2, status.mmol!!, 1e-9)
        assertTrue(status.outOfRange)
    }

    @Test fun bareIntegersAreNotMmolValues() {
        val status = CamapsParser.parse(listOf("CamAPS FX", "Auto mode", "Off", "12"))!!
        assertNull(status.mmol)
        assertEquals("Off", status.autoMode)
    }

    @Test fun autoModeWithoutValue() {
        val status = CamapsParser.parse(listOf("CamAPS FX", "Auto mode", "Starting"))!!
        assertNull(status.mmol)
        assertEquals("Starting", status.autoMode)
    }

    @Test fun unrelatedNotificationsAreIgnored() {
        assertNull(CamapsParser.parse(emptyList()))
        assertNull(CamapsParser.parse(listOf("CamAPS FX", "Sensor warm-up")))
    }
}
