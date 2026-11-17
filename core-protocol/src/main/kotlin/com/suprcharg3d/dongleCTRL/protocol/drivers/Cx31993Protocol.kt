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

/**
 * CX31993 Conexant 9-band DSP protocol (WalkPlay Scheme 27, Report ID 0x01, 61-byte packets).
 */
class Cx31993Protocol : DacProtocol {

    override val family: ProtocolFamily = ProtocolFamily.CX31993
    override val displayName: String = "CX31993 Conexant 9-Band (0x01)"

    override fun buildCapabilities(profile: DacProfile): DacCapabilities = DacCapabilities(
        maxBands = profile.bandCount.coerceIn(1, 9),
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

    override suspend fun readDeviceState(transport: DacTransport, profile: DacProfile): DacDeviceState {
        return DacDeviceState(
            bands = DefaultBands.flatBands(profile.bandCount.coerceIn(1, 9)),
            diagnosticNote = "CX31993 9-band ready (Report ID 0x01)"
        )
    }

    override suspend fun writeEqBand(
        transport: DacTransport,
        profile: DacProfile,
        band: EqBand,
        globalGainDb: Double,
        presetId: Int
    ): Boolean {
        val pkt = buildBandPacket(band, sampleRate = 48_000, op = OP_SET_COEFFS)
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
            delay(10)
        }
        return true
    }

    override suspend fun saveToFlash(transport: DacTransport, profile: DacProfile, presetId: Int): Boolean {
        val pkt = buildSavePacket()
        return transport.send(pkt)
    }

    internal fun buildBandPacket(band: EqBand, sampleRate: Int = 48_000, op: Int = OP_SET_COEFFS): ByteArray {
        val coeffs = BiquadCalculator.compute(band, sampleRate)
        val q = BiquadCalculator.quantizeCx31993(coeffs)
        val pkt = ByteArray(62)
        pkt[0] = REPORT_ID.toByte()
        pkt[1] = 0x00
        BytePack.u16Le(pkt, 2, 9) // arg count
        BytePack.u16Le(pkt, 4, 0x40) // cmdId
        BytePack.i32Le(pkt, 6, MAGIC.toInt())
        BytePack.i32Le(pkt, 10, op)
        pkt[14] = SAMPLE_RATE_IDX_48K.toByte()
        pkt[15] = (band.index + 1).coerceIn(1, 9).toByte()
        pkt[16] = (q.gainShift + 2).toByte()
        BytePack.i24Le(pkt, 17, q.b0)
        BytePack.i24Le(pkt, 20, q.b1)
        BytePack.i24Le(pkt, 23, q.b2)
        BytePack.i24Le(pkt, 26, q.negA1)
        BytePack.i24Le(pkt, 29, q.negA2)
        return pkt
    }

    private fun buildSavePacket(): ByteArray {
        val pkt = ByteArray(62)
        pkt[0] = REPORT_ID.toByte()
        pkt[1] = 0x00
        BytePack.u16Le(pkt, 2, 2)
        BytePack.u16Le(pkt, 4, 0x40)
        BytePack.i32Le(pkt, 6, MAGIC.toInt())
        BytePack.i32Le(pkt, 10, OP_SAVE)
        return pkt
    }

    companion object {
        const val REPORT_ID = 0x01
        private const val MAGIC = 0x8E4A0000L
        private const val OP_SET_COEFFS = 2
        private const val OP_SAVE = 3
        private const val SAMPLE_RATE_IDX_48K = 4
    }
}
