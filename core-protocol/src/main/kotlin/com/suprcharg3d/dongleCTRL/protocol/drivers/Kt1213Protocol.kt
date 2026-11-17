package com.suprcharg3d.dongleCTRL.protocol.drivers

import com.suprcharg3d.dongleCTRL.dsp.CrcUtils
import com.suprcharg3d.dongleCTRL.dsp.DefaultBands
import com.suprcharg3d.dongleCTRL.dsp.EqBand
import com.suprcharg3d.dongleCTRL.dsp.FilterType
import com.suprcharg3d.dongleCTRL.protocol.BytePack
import com.suprcharg3d.dongleCTRL.protocol.DacCapabilities
import com.suprcharg3d.dongleCTRL.protocol.DacDeviceState
import com.suprcharg3d.dongleCTRL.protocol.DacProfile
import com.suprcharg3d.dongleCTRL.protocol.DacProtocol
import com.suprcharg3d.dongleCTRL.protocol.DacTransport
import com.suprcharg3d.dongleCTRL.protocol.NamedOption
import com.suprcharg3d.dongleCTRL.protocol.ProtocolFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * KT1213 (Scheme 26) & MT2000 (Schemes 28 / 2801) 15-band DSP protocol using Report ID 0x57.
 */
class Kt1213Protocol : DacProtocol {

    override val family: ProtocolFamily = ProtocolFamily.KT1213
    override val displayName: String = "KT1213 / MT2000 15-Band (0x57)"

    private var sequence = 0

    override fun buildCapabilities(profile: DacProfile): DacCapabilities = DacCapabilities(
        maxBands = profile.bandCount.coerceIn(1, 15),
        minGainDb = -12.0,
        maxGainDb = 12.0,
        minQ = 0.25,
        maxQ = 16.0,
        supportedFilters = listOf(
            FilterType.PEAK,
            FilterType.LOW_SHELF,
            FilterType.HIGH_SHELF,
            FilterType.LOW_PASS,
            FilterType.HIGH_PASS
        ),
        sampleRate = 48_000,
        hasGlobalGain = true,
        globalGainRange = -12.0..12.0,
        presets = listOf(
            NamedOption(0, "Preset 1"),
            NamedOption(1, "Preset 2"),
            NamedOption(2, "Preset 3"),
            NamedOption(3, "Preset 4")
        ),
        supportsRamRefresh = true,
        supportsFlashSave = true
    )

    override suspend fun readDeviceState(transport: DacTransport, profile: DacProfile): DacDeviceState {
        return DacDeviceState(
            bands = DefaultBands.flatBands(profile.bandCount.coerceIn(1, 15)),
            diagnosticNote = "KT1213 / MT2000 15-band 0x57 ready"
        )
    }

    override suspend fun writeEqBand(
        transport: DacTransport,
        profile: DacProfile,
        band: EqBand,
        globalGainDb: Double,
        presetId: Int
    ): Boolean {
        // KT1213 module 72 (EQ): per-band payload = freq*10 (u32 LE), Q*1000 (u16 LE), gain*10 (i16 LE), type (u8), enabled (u8)
        val data = ByteArray(10)
        BytePack.i32Le(data, 0, band.frequencyHz.coerceIn(10, 24000) * 10)
        BytePack.u16Le(data, 4, (band.q * 1000.0).roundToInt().coerceIn(100, 16000))
        BytePack.u16Le(data, 6, (band.gainDb * 10.0).roundToInt())
        data[8] = band.filterType.ktCode.toByte()
        data[9] = if (band.enabled) 1 else 0

        val offset = band.index * 10
        val frame = buildExtendedWriteFrame(moduleId = MODULE_EQ, offset = offset, payload = data)
        return transport.send(frame)
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
        return refreshEqRam(transport, profile)
    }

    override suspend fun refreshEqRam(transport: DacTransport, profile: DacProfile): Boolean {
        val pkt = BytePack.fixed64(0x4B, 5, 1, 1, 0, 255)
        return transport.send(pkt)
    }

    override suspend fun saveToFlash(transport: DacTransport, profile: DacProfile, presetId: Int): Boolean {
        refreshEqRam(transport, profile)
        delay(25)
        val pkt = BytePack.fixed64(0x4B, 6, 1, 3, 0, presetId and 0xFF, 255)
        return transport.send(pkt)
    }

    override suspend fun selectPreset(transport: DacTransport, profile: DacProfile, presetId: Int): Boolean {
        val pkt = BytePack.fixed64(0x4B, 5, 1, 0, presetId and 0xFF, 255)
        return transport.send(pkt)
    }

    internal fun buildExtendedWriteFrame(moduleId: Int, offset: Int, payload: ByteArray): ByteArray {
        val seq = (sequence++ and 0x0F)
        val totalLen = 6 + payload.size // mod + rw + offLo + offHi + data + crc2? Check framing length
        val frame = ByteArray(64)
        frame[0] = REPORT_ID.toByte()
        frame[1] = 0x3F
        frame[2] = 0x5A
        frame[3] = 0xA5.toByte()
        frame[4] = ((seq shl 4) or 0x02).toByte()
        frame[5] = totalLen.toByte()
        frame[6] = moduleId.toByte()
        frame[7] = 0x80.toByte() // write bit
        BytePack.u16Le(frame, 8, offset)
        payload.copyInto(frame, destinationOffset = 10)
        val crcEnd = 10 + payload.size
        val crc = CrcUtils.crc16Ibm(frame, 1, crcEnd - 1)
        BytePack.u16Le(frame, crcEnd, crc)
        return frame
    }

    companion object {
        const val REPORT_ID = 0x57
        private const val MODULE_EQ = 72
    }
}
