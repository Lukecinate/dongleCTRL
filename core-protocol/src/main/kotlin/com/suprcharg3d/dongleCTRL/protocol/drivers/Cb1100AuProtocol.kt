package com.suprcharg3d.dongleCTRL.protocol.drivers

import com.suprcharg3d.dongleCTRL.dsp.BiquadCalculator
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
import com.suprcharg3d.dongleCTRL.protocol.ProtocolFamily
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * CB1100AU Extended DSP (WalkPlay Scheme 24) using 0xCB multi-frame "EQ1#" Q5.27 payloads + CRC16-CCITT.
 */
class Cb1100AuProtocol : DacProtocol {

    override val family: ProtocolFamily = ProtocolFamily.CB1100AU
    override val displayName: String = "CB1100AU DSP (0xCB)"

    @Volatile
    private var cachedBands: List<EqBand> = emptyList()

    @Volatile
    private var cachedGlobalGainDb: Double = 0.0

    @Volatile
    private var lastPresetId: Int = 0

    override fun buildCapabilities(profile: DacProfile): DacCapabilities = DacCapabilities(
        maxBands = profile.bandCount.coerceIn(1, 10),
        minGainDb = -12.0,
        maxGainDb = 12.0,
        minQ = 0.25,
        maxQ = 16.0,
        supportedFilters = listOf(FilterType.PEAK, FilterType.LOW_SHELF, FilterType.HIGH_SHELF),
        sampleRate = 48_000,
        hasGlobalGain = true,
        globalGainRange = -12.0..12.0,
        supportsRamRefresh = true,
        supportsFlashSave = true
    )

    override suspend fun readDeviceState(transport: DacTransport, profile: DacProfile): DacDeviceState {
        val count = profile.bandCount.coerceIn(1, 10)
        val initial = if (cachedBands.size == count) cachedBands else DefaultBands.flatBands(count).also {
            cachedBands = it
        }
        return DacDeviceState(
            globalGainDb = cachedGlobalGainDb,
            bands = initial,
            diagnosticNote = "CB1100AU write-ready (0xCB 48kHz Q5.27)"
        )
    }

    override suspend fun writeEqBand(
        transport: DacTransport,
        profile: DacProfile,
        band: EqBand,
        globalGainDb: Double,
        presetId: Int
    ): Boolean {
        val count = profile.bandCount.coerceIn(1, 10)
        val base = if (cachedBands.size == count) cachedBands else DefaultBands.flatBands(count)
        val merged = base.mapIndexed { idx, existing ->
            if (idx == band.index) band else existing
        }
        return writeAllEqBands(transport, profile, merged, globalGainDb, presetId)
    }

    override suspend fun writeAllEqBands(
        transport: DacTransport,
        profile: DacProfile,
        bands: List<EqBand>,
        globalGainDb: Double,
        presetId: Int
    ): Boolean {
        val count = profile.bandCount.coerceIn(1, 10)
        val normalized = if (bands.size >= count) bands.take(count) else {
            val flat = DefaultBands.flatBands(count)
            List(count) { idx -> bands.getOrNull(idx) ?: flat[idx] }
        }
        cachedBands = normalized
        cachedGlobalGainDb = globalGainDb
        lastPresetId = presetId
        val chunks = buildEqPackets(normalized, globalGainDb)
        for (pkt in chunks) {
            if (!transport.send(pkt)) return false
            delay(8)
        }
        return sendApplyControl(transport, presetId, saveFlash = false)
    }

    override suspend fun setGlobalGain(transport: DacTransport, profile: DacProfile, gainDb: Double): Boolean {
        val count = profile.bandCount.coerceIn(1, 10)
        val bands = if (cachedBands.size == count) cachedBands else DefaultBands.flatBands(count)
        return writeAllEqBands(transport, profile, bands, gainDb, lastPresetId)
    }

    override suspend fun resetEq(transport: DacTransport, profile: DacProfile): Boolean {
        val count = profile.bandCount.coerceIn(1, 10)
        return writeAllEqBands(transport, profile, DefaultBands.flatBands(count), 0.0, lastPresetId)
    }

    override suspend fun saveToFlash(transport: DacTransport, profile: DacProfile, presetId: Int): Boolean =
        sendApplyControl(transport, presetId, saveFlash = true)

    private suspend fun sendApplyControl(transport: DacTransport, presetId: Int, saveFlash: Boolean): Boolean {
        val ctrl = ByteArray(32)
        ctrl[0] = REPORT_ID.toByte()
        ctrl[1] = 0x01
        ctrl[2] = (presetId and 0xFF).toByte()
        ctrl[3] = 0x00
        ctrl[4] = if (saveFlash) 0x01 else 0x00
        ctrl[5] = 0xFF.toByte()
        return transport.send(ctrl)
    }

    internal fun buildEqPackets(bands: List<EqBand>, globalGainDb: Double): List<ByteArray> {
        val bandCount = bands.size.coerceIn(1, 10)
        val innerSize = 6 + 8 + 4 + bandCount * 20 + 2
        val inner = ByteArray(innerSize)

        // Header "EQ1#" + uint16 LE total length
        inner[0] = 'E'.code.toByte()
        inner[1] = 'Q'.code.toByte()
        inner[2] = '1'.code.toByte()
        inner[3] = '#'.code.toByte()
        BytePack.u16Le(inner, 4, innerSize)

        // Descriptor block
        inner[6] = 0
        inner[7] = bandCount.toByte()
        inner[8] = 4
        inner[9] = 5
        inner[10] = 1
        inner[11] = bandCount.toByte()
        inner[12] = 0
        inner[13] = 0

        // Linear preamp scaled by 2^23
        val preampLinear = (10.0.pow(globalGainDb / 20.0) * (1 shl 23)).roundToInt()
        BytePack.i32Le(inner, 14, preampLinear)

        var off = 18
        for (i in 0 until bandCount) {
            val coeffs = BiquadCalculator.compute(bands[i], 48_000)
            val q27 = BiquadCalculator.quantizeQ5_27(coeffs)
            for (v in q27) {
                BytePack.i32Le(inner, off, v)
                off += 4
            }
        }

        val crc = CrcUtils.crc16Ccitt(inner, 6, off - 6)
        BytePack.u16Be(inner, off, crc)

        // Chunk into 32-byte HID frames [0xCB, seq, ...30 bytes]
        val packets = ArrayList<ByteArray>()
        var cursor = 0
        var seq = 0
        while (cursor < inner.size) {
            val pkt = ByteArray(32)
            pkt[0] = REPORT_ID.toByte()
            pkt[1] = (seq and 0xFF).toByte()
            val copyLen = (inner.size - cursor).coerceAtMost(30)
            inner.copyInto(pkt, destinationOffset = 2, startIndex = cursor, endIndex = cursor + copyLen)
            packets.add(pkt)
            cursor += copyLen
            seq++
        }
        return packets
    }

    companion object {
        const val REPORT_ID = 0xCB
    }
}
