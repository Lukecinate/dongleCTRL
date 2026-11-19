package com.suprcharg3d.dongleCTRL.protocol.drivers

import com.suprcharg3d.dongleCTRL.dsp.BiquadCalculator
import com.suprcharg3d.dongleCTRL.dsp.DefaultBands
import com.suprcharg3d.dongleCTRL.dsp.EqBand
import com.suprcharg3d.dongleCTRL.dsp.FilterType
import com.suprcharg3d.dongleCTRL.protocol.BytePack
import com.suprcharg3d.dongleCTRL.protocol.DacCapabilities
import com.suprcharg3d.dongleCTRL.protocol.DacDeviceState
import com.suprcharg3d.dongleCTRL.protocol.DacProfile
import com.suprcharg3d.dongleCTRL.protocol.DacProtocol
import com.suprcharg3d.dongleCTRL.protocol.DacTransport
import com.suprcharg3d.dongleCTRL.protocol.ProtocolFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * GM55 10-band gaming DAC protocol (VID 0x1024, PID 0x8087), Report ID 0x02, Q5.27 48 kHz.
 */
class Gm55Protocol : DacProtocol {

    override val family: ProtocolFamily = ProtocolFamily.GM55
    override val displayName: String = "GM55 10-Band DSP (0x02)"

    override fun buildCapabilities(profile: DacProfile): DacCapabilities = DacCapabilities(
        maxBands = 10,
        minGainDb = -12.0,
        maxGainDb = 12.0,
        minQ = 0.25,
        maxQ = 16.0,
        supportedFilters = listOf(FilterType.PEAK, FilterType.LOW_SHELF, FilterType.HIGH_SHELF),
        sampleRate = 48_000,
        hasGlobalGain = false,
        supportsRamRefresh = false,
        supportsFlashSave = true
    )

    override suspend fun readDeviceState(transport: DacTransport, profile: DacProfile): DacDeviceState =
        DacDeviceState(
            bands = DefaultBands.flatBands(10),
            diagnosticNote = "GM55 10-band ready (Report ID 0x02)"
        )

    override suspend fun writeEqBand(
        transport: DacTransport,
        profile: DacProfile,
        band: EqBand,
        globalGainDb: Double,
        presetId: Int
    ): Boolean {
        val pkt = buildBandPacket(band)
        return transport.send(pkt)
    }

    override suspend fun writeAllEqBands(
        transport: DacTransport,
        profile: DacProfile,
        bands: List<EqBand>,
        globalGainDb: Double,
        presetId: Int
    ): Boolean {
        for (band in bands) {
            if (!writeEqBand(transport, profile, band, globalGainDb, presetId)) return false
            delay(8)
        }
        return true
    }

    override suspend fun saveToFlash(transport: DacTransport, profile: DacProfile, presetId: Int): Boolean {
        val pkt = ByteArray(30)
        pkt[0] = REPORT_ID.toByte()
        pkt[1] = CMD_SAVE
        pkt[2] = (presetId and 0xFF).toByte()
        return transport.send(pkt)
    }

    override suspend fun resetEq(transport: DacTransport, profile: DacProfile): Boolean {
        return writeAllEqBands(transport, profile, DefaultBands.flatBands(10), 0.0, 0)
    }

    internal fun buildBandPacket(band: EqBand): ByteArray {
        val coeffs = BiquadCalculator.compute(band, 48_000)
        val q27 = BiquadCalculator.quantizeQ5_27(coeffs)
        val pkt = ByteArray(30) // Report ID (0x02) + 29 payload bytes
        pkt[0] = REPORT_ID.toByte()
        pkt[1] = CMD_WRITE_BAND
        pkt[2] = band.index.coerceIn(0, 9).toByte()
        pkt[3] = band.filterType.gm55Code.toByte()
        BytePack.u16Le(pkt, 4, band.frequencyHz.coerceIn(10, 24000))
        BytePack.u16Le(pkt, 6, (band.q * 1000.0).roundToInt().coerceIn(250, 16000))
        BytePack.u16Le(pkt, 8, (band.gainDb * 100.0).roundToInt())
        var off = 10
        for (c in q27) {
            BytePack.i32Le(pkt, off, c)
            off += 4
        }
        return pkt
    }

    companion object {
        const val REPORT_ID = 0x02
        private const val CMD_WRITE_BAND: Byte = 11
        private const val CMD_SAVE: Byte = 12
    }
}
