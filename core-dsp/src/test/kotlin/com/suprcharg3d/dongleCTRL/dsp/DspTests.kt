package com.suprcharg3d.dongleCTRL.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DspTests {

    @Test
    fun `flat peak filter produces unity biquad coefficients`() {
        val band = EqBand(index = 0, frequencyHz = 1000, gainDb = 0.0, q = 1.41, filterType = FilterType.PEAK)
        val c = BiquadCalculator.compute(band, sampleRate = 96_000)
        assertEquals(1.0, c.b0, 1e-9)
        assertEquals(0.0, c.b2 - c.a2, 1e-9)
        assertEquals(0.0, BiquadCalculator.evaluateMagnitudeDb(listOf(band), 1000.0, sampleRate = 96_000), 1e-6)
    }

    @Test
    fun `boosted peak filter hits target gain at center frequency`() {
        val band = EqBand(index = 0, frequencyHz = 1000, gainDb = 6.0, q = 2.0, filterType = FilterType.PEAK)
        val dbAtCenter = BiquadCalculator.evaluateMagnitudeDb(listOf(band), 1000.0, sampleRate = 96_000)
        val dbFar = BiquadCalculator.evaluateMagnitudeDb(listOf(band), 20.0, sampleRate = 96_000)
        assertEquals(6.0, dbAtCenter, 0.05)
        assertTrue("Expected minimal boost at 20 Hz, got $dbFar", abs(dbFar) < 0.1)
    }

    @Test
    fun `Q2_30 quantization scales unity filter b0 to 2 pow 30`() {
        val q30 = BiquadCalculator.quantizeCbQ2_30(BiquadCoeffs.BYPASS)
        assertEquals(1 shl 30, q30[0])
        assertEquals(0, q30[1])
        assertEquals(0, q30[2])
        assertEquals(0, q30[3])
        assertEquals(0, q30[4])
    }

    @Test
    fun `CX31993 24-bit quantizer fits within signed 24-bit bounds`() {
        val band = EqBand(index = 0, frequencyHz = 8000, gainDb = 12.0, q = 0.7, filterType = FilterType.HIGH_SHELF)
        val q = BiquadCalculator.quantizeCx31993(BiquadCalculator.compute(band, 48_000))
        val min24 = -8388608
        val max24 = 8388607
        assertTrue(q.b0 in min24..max24)
        assertTrue(q.b1 in min24..max24)
        assertTrue(q.b2 in min24..max24)
        assertTrue(q.negA1 in min24..max24)
        assertTrue(q.negA2 in min24..max24)
    }

    @Test
    fun `CRCs match known vectors`() {
        // CRC8-Maxim of ASCII "123456789" = 0xA1
        val vec = "123456789".encodeToByteArray()
        assertEquals(0xA1, CrcUtils.crc8Maxim(vec))
        // CRC16/CCITT-FALSE of ASCII "123456789" = 0x29B1
        assertEquals(0x29B1, CrcUtils.crc16Ccitt(vec))
        // CRC16/MODBUS of ASCII "123456789" = 0x4B37
        assertEquals(0x4B37, CrcUtils.crc16Ibm(vec))
    }

    @Test
    fun `AutoEQ parser roundtrips parametric bands and preamp`() {
        val sample = """
            Preamp: -5.4 dB
            Filter 1: ON LSC Fc 105 Hz Gain 4.5 dB Q 0.71
            Filter 2: ON PK Fc 3200 Hz Gain -3.0 dB Q 2.20
            Filter 3: OFF HSC Fc 10000 Hz Gain 2.0 dB Q 0.70
        """.trimIndent()
        val parsed = PeqTextParser.parse(sample)
        assertEquals(-5.4, parsed.preampDb, 1e-6)
        assertEquals(3, parsed.bands.size)
        assertEquals(FilterType.LOW_SHELF, parsed.bands[0].filterType)
        assertEquals(105, parsed.bands[0].frequencyHz)
        assertEquals(4.5, parsed.bands[0].gainDb, 1e-6)
        assertEquals(0.71, parsed.bands[0].q, 1e-6)
        assertTrue(!parsed.bands[2].enabled)

        val exported = PeqTextParser.exportAutoEq(parsed.bands, parsed.preampDb)
        val reparsed = PeqTextParser.parse(exported)
        assertEquals(parsed.bands.size, reparsed.bands.size)
        assertEquals(3200, reparsed.bands[1].frequencyHz)
    }
}
