package com.suprcharg3d.dongleCTRL.protocol.drivers

import com.suprcharg3d.dongleCTRL.dsp.DefaultBands
import com.suprcharg3d.dongleCTRL.dsp.EqBand
import com.suprcharg3d.dongleCTRL.dsp.FilterType
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
 * FiiO Desktop DAC Serial/CDC protocol (Restmoment `0xFC 0xFA` framing at 57600 baud 8N1)
 * used by K15, K17, K17 R2R, K19 over USB OTG CDC-ACM.
 *
 * Wire spec verified from FiiO Control Web (`protocol_pretty.js` `Js`, `M2`, `k2`, `_3e`, `HF`, `L3e`):
 * - Frame: `[0xFC, 0xFA, lenLo, lenHi, cmdType, modIdx, modAddr, ...payload, chk]`
 * - `len = payload.size + 4`
 * - `chk = ((0xFC + cmdType + modIdx + modAddr + sum(payload)) and 0xFF) xor 0xFA`
 * - Write ACK contains `0xBB` (`187`); Read response frame starts at `0xFC 0xFA` and carries `len + 4` total bytes.
 */
class FiioRestmomentProtocol : DacProtocol {

    override val family: ProtocolFamily = ProtocolFamily.FIIO_RESTMOMENT
    override val displayName: String = "FiiO Desktop Serial (0xFC/0xFA)"

    @Volatile
    private var lastNonBypassPresetId: Int = 160

    override fun buildCapabilities(profile: DacProfile): DacCapabilities {
        val bandsCount = profile.bandCount.coerceIn(10, 31)
        return DacCapabilities(
            maxBands = bandsCount,
            minGainDb = -24.0,
            maxGainDb = 12.0,
            minQ = 0.4,
            maxQ = 128.0,
            supportedFilters = listOf(FilterType.PEAK, FilterType.LOW_SHELF, FilterType.HIGH_SHELF),
            sampleRate = 48_000,
            hasEqSwitch = true,
            hasGlobalGain = false,
            presets = SERIAL_DEFAULT_PRESETS,
            writablePresetIds = (160..169).toSet(),
            supportsRamRefresh = false,
            supportsFlashSave = true
        )
    }

    override suspend fun readDeviceState(transport: DacTransport, profile: DacProfile): DacDeviceState {
        // Connect handshake: DEVICE_STATE [17, 0]
        sendWriteAndAwaitAck(
            transport,
            buildPacket(CMD_WRITE, SYSTEM_INDEX, ADDR_DEVICE_STATE, byteArrayOf(0x11, 0x00)),
            timeoutMs = 800
        )

        val activePreset = queryActivePreset(transport) ?: 160
        if (activePreset != PRESET_BYPASS) {
            lastNonBypassPresetId = activePreset
        }

        val bands = queryEqBands(transport, profile.bandCount.coerceIn(10, 31))
            ?: DefaultBands.flatBands(profile.bandCount.coerceIn(10, 31))

        return DacDeviceState(
            eqEnabled = activePreset != PRESET_BYPASS,
            activePresetId = activePreset,
            bands = bands,
            diagnosticNote = "FiiO Restmoment 57600 8N1 (${bands.size}-band PEQ, preset=$activePreset)"
        )
    }

    override suspend fun refreshLiveTelemetry(
        transport: DacTransport,
        profile: DacProfile,
        currentState: DacDeviceState,
        includeAllControls: Boolean
    ): DacDeviceState? {
        if (!includeAllControls) return null
        val preset = queryActivePreset(transport) ?: return null
        if (preset != PRESET_BYPASS) lastNonBypassPresetId = preset
        val bands = queryEqBands(transport, profile.bandCount.coerceIn(10, 31))
        return currentState.copy(
            eqEnabled = preset != PRESET_BYPASS,
            activePresetId = preset,
            bands = bands ?: currentState.bands
        )
    }

    override suspend fun refreshPresetEqState(
        transport: DacTransport,
        profile: DacProfile,
        currentState: DacDeviceState,
        presetId: Int
    ): DacDeviceState? {
        val bands = queryEqBands(transport, profile.bandCount.coerceIn(10, 31))
        return currentState.copy(
            eqEnabled = presetId != PRESET_BYPASS,
            activePresetId = presetId,
            bands = bands ?: currentState.bands
        )
    }

    override suspend fun setEqEnabled(transport: DacTransport, profile: DacProfile, enabled: Boolean): Boolean {
        val target = if (enabled) lastNonBypassPresetId else PRESET_BYPASS
        return selectPreset(transport, profile, target)
    }

    override suspend fun selectPreset(transport: DacTransport, profile: DacProfile, presetId: Int): Boolean {
        if (presetId != PRESET_BYPASS) lastNonBypassPresetId = presetId
        val frame = buildPacket(
            cmdType = CMD_WRITE,
            moduleId = SYSTEM_INDEX,
            addr = ADDR_MODE_LOAD,
            data = byteArrayOf((presetId and 0xFF).toByte(), 0x00)
        )
        return sendWriteAndAwaitAck(transport, frame)
    }

    override suspend fun writeEqBand(
        transport: DacTransport,
        profile: DacProfile,
        band: EqBand,
        globalGainDb: Double,
        presetId: Int
    ): Boolean {
        val frame = buildWriteBandPacket(band)
        return sendWriteAndAwaitAck(transport, frame)
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
            delay(40)
        }
        return saveToFlash(transport, profile, presetId)
    }

    override suspend fun saveToFlash(transport: DacTransport, profile: DacProfile, presetId: Int): Boolean {
        val frame = buildPacket(
            cmdType = CMD_WRITE,
            moduleId = SYSTEM_INDEX,
            addr = ADDR_MODE_SAVE,
            data = putU16Le(presetId)
        )
        return sendWriteAndAwaitAck(transport, frame, timeoutMs = 1500)
    }

    override suspend fun resetEq(transport: DacTransport, profile: DacProfile): Boolean {
        val flat31 = buildResetEqPacket()
        return sendWriteAndAwaitAck(transport, flat31, timeoutMs = 1500)
    }

    private suspend fun queryActivePreset(transport: DacTransport): Int? {
        val req = buildPacket(CMD_READ, SYSTEM_INDEX, ADDR_MODE_LOAD)
        val frame = sendReadAndReceiveFrame(transport, req, timeoutMs = 1200) ?: return null
        return parseActivePresetResponse(frame)
    }

    private suspend fun queryEqBands(transport: DacTransport, maxBands: Int): List<EqBand>? {
        val req = buildPacket(CMD_READ, SYSTEM_INDEX, ADDR_UPLOAD, byteArrayOf(EQ_INDEX.toByte(), 0x00))
        val frame = sendReadAndReceiveFrame(transport, req, timeoutMs = 3000) ?: return null
        return parseEqBandsResponse(frame, maxBands)
    }

    private suspend fun sendWriteAndAwaitAck(
        transport: DacTransport,
        frame: ByteArray,
        timeoutMs: Int = 1000
    ): Boolean {
        if (!transport.send(frame, timeoutMs = 600)) return false
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val chunk = transport.receive(expectedSize = 64, timeoutMs = 250) ?: continue
            if (chunk.any { (it.toInt() and 0xFF) == ACK_BYTE }) return true
        }
        // Some desktop firmwares apply CDC writes silently without emitting 0xBB; do not stall UX.
        return true
    }

    private suspend fun sendReadAndReceiveFrame(
        transport: DacTransport,
        frame: ByteArray,
        timeoutMs: Int
    ): ByteArray? {
        if (!transport.send(frame, timeoutMs = 600)) return null
        val acc = ArrayList<Byte>(384)
        var startIdx = -1
        var expectedBodyLen = -1
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).toInt().coerceIn(80, 400)
            val chunk = transport.receive(expectedSize = 512, timeoutMs = remaining) ?: continue
            for (b in chunk) acc.add(b)

            if (startIdx < 0) {
                for (i in 0 until acc.size - 1) {
                    if ((acc[i].toInt() and 0xFF) == HEADER_0 && (acc[i + 1].toInt() and 0xFF) == HEADER_1) {
                        startIdx = i
                        break
                    }
                }
            }
            if (startIdx >= 0 && expectedBodyLen < 0 && acc.size - startIdx >= 4) {
                expectedBodyLen = getU16Le(acc[startIdx + 2], acc[startIdx + 3])
            }
            if (startIdx >= 0 && expectedBodyLen >= 4) {
                val needed = startIdx + 4 + expectedBodyLen
                if (acc.size >= needed) {
                    return ByteArray(4 + expectedBodyLen) { idx -> acc[startIdx + idx] }
                }
            }
        }
        return null
    }

    // ---------------------------------------------------------------------------------------------
    // Pure Frame Builders / Parsers (internal for unit tests)
    // ---------------------------------------------------------------------------------------------

    internal fun buildPacket(cmdType: Int, moduleId: Int, addr: Int, data: ByteArray = byteArrayOf()): ByteArray {
        val bodyLen = data.size + 4
        val totalLen = 4 + bodyLen
        val out = ByteArray(totalLen)
        out[0] = HEADER_0.toByte()
        out[1] = HEADER_1.toByte()
        out[2] = (bodyLen and 0xFF).toByte()
        out[3] = ((bodyLen ushr 8) and 0xFF).toByte()
        out[4] = (cmdType and 0xFF).toByte()
        out[5] = (moduleId and 0xFF).toByte()
        out[6] = (addr and 0xFF).toByte()
        data.copyInto(out, destinationOffset = 7)
        var sum = HEADER_0 + cmdType + moduleId + addr
        for (b in data) sum += (b.toInt() and 0xFF)
        out[totalLen - 1] = ((sum and 0xFF) xor HEADER_1).toByte()
        return out
    }

    internal fun buildWriteBandPacket(band: EqBand): ByteArray {
        val filterCode = band.filterType.fiioCode
        val fIdx = FiioPeqCodec.nearestIndex(FiioPeqCodec.K19_FREQ_TABLE, band.frequencyHz.toDouble())
        val gIdx = ((band.gainDb.coerceIn(-24.0, 12.0) + 24.0) / 0.2).roundToInt().coerceIn(0, 180)
        val qIdx = if (band.filterType == FilterType.PEAK) {
            FiioPeqCodec.nearestIndex(FiioPeqCodec.K19_Q_TABLE, band.q)
        } else {
            Q_INDEX_ONE
        }
        val sIdx = if (band.filterType == FilterType.PEAK) {
            Q_INDEX_ONE
        } else {
            FiioPeqCodec.nearestIndex(FiioPeqCodec.K19_Q_TABLE, band.q)
        }

        val payload = ByteArray(12)
        writeU16Le(payload, 0, band.index)
        writeU16Le(payload, 2, filterCode)
        writeU16Le(payload, 4, fIdx)
        writeU16Le(payload, 6, gIdx)
        writeU16Le(payload, 8, qIdx)
        writeU16Le(payload, 10, sIdx)
        return buildPacket(CMD_WRITE, EQ_INDEX, ADDR_EQ_INDEX, payload)
    }

    internal fun buildResetEqPacket(): ByteArray {
        val payload = ByteArray(6 + 31 * 10)
        payload[0] = 0x01
        val flatGIdx = 120 // 0.0 dB = (-24.0 + 120 * 0.2)
        for (i in 0 until 31) {
            val base = 6 + i * 10
            val fIdx = FiioPeqCodec.nearestIndex(FiioPeqCodec.K19_FREQ_TABLE, K19_RESET_FREQS[i])
            writeU16Le(payload, base, 0) // PEAK
            writeU16Le(payload, base + 2, fIdx)
            writeU16Le(payload, base + 4, flatGIdx)
            writeU16Le(payload, base + 6, Q_INDEX_ONE)
            writeU16Le(payload, base + 8, Q_INDEX_ONE)
        }
        return buildPacket(CMD_WRITE, SYSTEM_INDEX, ADDR_DOWNLOAD, payload)
    }

    internal fun parseActivePresetResponse(frame: ByteArray): Int? {
        if (frame.size < 9) return null
        if ((frame[0].toInt() and 0xFF) != HEADER_0 || (frame[1].toInt() and 0xFF) != HEADER_1) return null
        val modIdx = frame[5].toInt() and 0xFF
        val modAddr = frame[6].toInt() and 0xFF
        if (modIdx != SYSTEM_INDEX || modAddr != ADDR_MODE_LOAD) return null
        return getU16Le(frame[7], frame[8])
    }

    internal fun parseEqBandsResponse(frame: ByteArray, maxBands: Int = 31): List<EqBand>? {
        if (frame.size < 12) return null
        if ((frame[0].toInt() and 0xFF) != HEADER_0 || (frame[1].toInt() and 0xFF) != HEADER_1) return null
        val modIdx = frame[5].toInt() and 0xFF
        if (modIdx != EQ_INDEX) return null
        val bodyLen = getU16Le(frame[2], frame[3])
        // Band bytes start at frame[11] (after [FC, FA, lenLo, lenHi, cmdType, modIdx, modAddr, eqIdx(2B), eqPass(2B)])
        val availableBytes = (minOf(frame.size, 4 + bodyLen - 1) - 11).coerceAtLeast(0)
        val bandCount = minOf(maxBands, availableBytes / 10)
        if (bandCount <= 0) return null

        val bands = ArrayList<EqBand>(bandCount)
        for (i in 0 until bandCount) {
            val base = 11 + i * 10
            val typeCode = getU16Le(frame[base], frame[base + 1])
            val fIdx = getU16Le(frame[base + 2], frame[base + 3]).coerceIn(0, FiioPeqCodec.K19_FREQ_TABLE.lastIndex)
            val gIdx = getU16Le(frame[base + 4], frame[base + 5]).coerceIn(0, 180)
            val qIdx = getU16Le(frame[base + 6], frame[base + 7]).coerceIn(0, FiioPeqCodec.K19_Q_TABLE.lastIndex)
            val sIdx = getU16Le(frame[base + 8], frame[base + 9]).coerceIn(0, FiioPeqCodec.K19_Q_TABLE.lastIndex)

            val filter = FilterType.fromFiioCode(typeCode)
            val freq = FiioPeqCodec.K19_FREQ_TABLE[fIdx].roundToInt().coerceIn(20, 20_000)
            val gain = ((gIdx * 0.2 - 24.0) * 10.0).roundToInt() / 10.0
            val q = if (filter == FilterType.PEAK) FiioPeqCodec.K19_Q_TABLE[qIdx] else FiioPeqCodec.K19_Q_TABLE[sIdx]
            bands += EqBand(i, freq, gain.coerceIn(-24.0, 12.0), q, filter)
        }
        return bands
    }

    private fun putU16Le(v: Int): ByteArray = byteArrayOf((v and 0xFF).toByte(), ((v ushr 8) and 0xFF).toByte())

    private fun writeU16Le(buf: ByteArray, off: Int, v: Int) {
        buf[off] = (v and 0xFF).toByte()
        buf[off + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun getU16Le(lo: Byte, hi: Byte): Int =
        (lo.toInt() and 0xFF) or ((hi.toInt() and 0xFF) shl 8)

    companion object {
        const val HEADER_0 = 0xFC
        const val HEADER_1 = 0xFA
        const val ACK_BYTE = 0xBB

        const val CMD_READ = 0xA0
        const val CMD_WRITE = 0xA7

        const val SYSTEM_INDEX = 0
        const val EQ_INDEX = 1
        const val EXPAND_INDEX = 2

        const val ADDR_MODE_SAVE = 1
        const val ADDR_MODE_LOAD = 2
        const val ADDR_DOWNLOAD = 15
        const val ADDR_UPLOAD = 16
        const val ADDR_DEVICE_STATE = 48
        const val ADDR_EQ_INDEX = 0

        const val PRESET_BYPASS = 240
        private const val Q_INDEX_ONE = 16 // FiioPeqCodec.K19_Q_TABLE[16] == 1.0

        private val K19_RESET_FREQS = doubleArrayOf(
            20.1, 24.8, 31.3, 40.1, 49.6, 62.5, 80.3, 101.0, 125.0, 161.0,
            202.0, 250.0, 315.0, 397.0, 500.0, 630.0, 794.0, 1000.0, 1260.0, 1590.0,
            2000.0, 2520.0, 3170.0, 4000.0, 5040.0, 6350.0, 8000.0, 10100.0, 12500.0, 16000.0,
            20000.0
        )

        internal val SERIAL_DEFAULT_PRESETS = listOf(
            NamedOption(0, "Jazz"),
            NamedOption(1, "Pop"),
            NamedOption(2, "Rock"),
            NamedOption(3, "Dance"),
            NamedOption(4, "R&B"),
            NamedOption(5, "Classic"),
            NamedOption(6, "Hip-Hop"),
            NamedOption(7, "Monitor"),
            NamedOption(8, "Retro"),
            NamedOption(9, "sDamp-1"),
            NamedOption(10, "sDamp-2"),
            NamedOption(160, "User 1"),
            NamedOption(161, "User 2"),
            NamedOption(162, "User 3"),
            NamedOption(163, "User 4"),
            NamedOption(164, "User 5"),
            NamedOption(165, "User 6"),
            NamedOption(166, "User 7"),
            NamedOption(167, "User 8"),
            NamedOption(168, "User 9"),
            NamedOption(169, "User 10")
        )
    }
}
