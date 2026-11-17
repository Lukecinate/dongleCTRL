package com.suprcharg3d.dongleCTRL.protocol.drivers

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
 * KTMicro 5-band (KT02H20: Schemes 17, 22) and 6-band (KT0231H: Schemes 19, 23) HID protocol.
 * Uses 11-byte packets starting with Report ID 0x4B.
 */
class KtProtocol(
    private val isSixBand: Boolean = false
) : DacProtocol {

    override val family: ProtocolFamily =
        if (isSixBand) ProtocolFamily.KT0231H else ProtocolFamily.KT02H20

    override val displayName: String =
        if (isSixBand) "KT0231H 6-Band DSP (0x4B)" else "KT02H20 5-Band DSP (0x4B)"

    private val bandCount = if (isSixBand) 6 else 5

    override fun buildCapabilities(profile: DacProfile): DacCapabilities = DacCapabilities(
        maxBands = bandCount,
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
        globalGainRange = -18.0..3.0,
        hasDacFilter = false,
        hasMicGain = true,
        micGainRange = -12..12,
        supportsRamRefresh = true,
        supportsFlashSave = true
    )

    override suspend fun readDeviceState(transport: DacTransport, profile: DacProfile): DacDeviceState {
        transport.drainInput()
        val isS19 = profile.schemeNo == "19" || profile.schemeNo == "23" || isSixBand
        val presetReg = if (isS19) 22 else 2
        val presetResp = readReg(transport, presetReg)
        val presetId = presetResp?.getOrNull(7)?.toInt()?.and(0xFF) ?: 0

        val baseReg = if (isS19) 53 else 38
        val bands = (0 until bandCount).map { idx ->
            val r0 = readReg(transport, baseReg + idx * 2)
            val r1 = readReg(transport, baseReg + idx * 2 + 1)
            val fallback = DefaultBands.flatBands(bandCount)[idx]
            if (r0 != null && r0.size >= 11 && r1 != null && r1.size >= 11) {
                val gainRaw = BytePack.getI16Le(r0, 7)
                val freqRaw = BytePack.getU16Le(r0, 9)
                val freqHz = if (isS19) freqRaw else freqRaw * 2
                val qRaw = BytePack.getU16Le(r1, 7)
                val typeCode = r1[9].toInt() and 0x07
                if (freqHz in 10..24000 && qRaw > 0) {
                    EqBand(
                        index = idx,
                        frequencyHz = freqHz,
                        gainDb = (gainRaw / 10.0).coerceIn(-12.0, 12.0),
                        q = (qRaw / 1000.0).coerceIn(0.25, 16.0),
                        filterType = FilterType.fromKtCode(typeCode)
                    )
                } else fallback
            } else fallback
        }
        return DacDeviceState(activePresetId = presetId, bands = bands)
    }

    override suspend fun writeEqBand(
        transport: DacTransport,
        profile: DacProfile,
        band: EqBand,
        globalGainDb: Double,
        presetId: Int
    ): Boolean {
        val isS19 = profile.schemeNo == "19" || profile.schemeNo == "23" || isSixBand
        val baseReg = if (isS19) 53 else 38
        val reg = baseReg + (band.index.coerceIn(0, bandCount - 1) * 2)

        val gain10 = (band.gainDb * 10.0).roundToInt()
        val freqPacked = if (isS19) band.frequencyHz else (band.frequencyHz / 2)
        val pkt0 = buildWriteReg(reg, gain10, freqPacked)
        if (transport.transceive(pkt0, expectedSize = 20, timeoutMs = 450) == null) return false

        val q1000 = (band.q * 1000.0).roundToInt().coerceIn(250, 16000)
        val filterWord = (band.filterType.ktCode and 0x07) or 0x08
        val pkt1 = buildWriteReg(reg + 1, q1000, filterWord)
        return transport.transceive(pkt1, expectedSize = 20, timeoutMs = 450) != null
    }

    override suspend fun writeAllEqBands(
        transport: DacTransport,
        profile: DacProfile,
        bands: List<EqBand>,
        globalGainDb: Double,
        presetId: Int
    ): Boolean {
        for (band in bands.take(bandCount)) {
            if (!writeEqBand(transport, profile, band, globalGainDb, presetId)) return false
            delay(8)
        }
        if (!refreshEqRam(transport, profile)) return false
        val presetReg = if (isSixBand || profile.schemeNo == "19") 22 else 2
        transport.transceive(buildWriteReg(presetReg, presetId, 0), expectedSize = 20, timeoutMs = 450)
        return true
    }

    override suspend fun refreshEqRam(transport: DacTransport, profile: DacProfile): Boolean {
        val refreshReg = if (isSixBand || profile.schemeNo == "19") 52 else 36
        return transport.transceive(buildWriteReg(refreshReg, 1, 0), expectedSize = 20, timeoutMs = 450) != null
    }

    override suspend fun saveToFlash(transport: DacTransport, profile: DacProfile, presetId: Int): Boolean {
        refreshEqRam(transport, profile)
        delay(20)
        val pkt = byteArrayOf(REPORT_ID.toByte(), 0, 0, 0, 0, 'S'.code.toByte(), 0, 0, 0, 0, 0)
        return transport.transceive(pkt, expectedSize = 20, timeoutMs = 600) != null
    }

    override suspend fun resetEq(transport: DacTransport, profile: DacProfile): Boolean {
        val pkt = byteArrayOf(REPORT_ID.toByte(), 0, 0, 0, 0, 'C'.code.toByte(), 0, 0, 0, 0, 0)
        return transport.transceive(pkt, expectedSize = 20, timeoutMs = 600) != null
    }

    override suspend fun setGlobalGain(transport: DacTransport, profile: DacProfile, gainDb: Double): Boolean {
        val isS19 = profile.schemeNo == "19" || isSixBand
        val reg = if (isS19) 87 else 59
        val value = if (isS19) (-gainDb * 10.0).roundToInt() else dbToChipGainIndex(gainDb)
        return transport.transceive(buildWriteReg(reg, value, 0), expectedSize = 20, timeoutMs = 450) != null
    }

    private suspend fun readReg(transport: DacTransport, reg: Int): ByteArray? {
        val pkt = byteArrayOf(REPORT_ID.toByte(), (reg and 0xFF).toByte(), 0, 0, 0, 'R'.code.toByte(), 0, 0, 0, 0, 0)
        return transport.transceive(pkt, expectedSize = 20, timeoutMs = 450)
    }

    private fun buildWriteReg(reg: Int, low16: Int, high16: Int): ByteArray {
        val pkt = ByteArray(11)
        pkt[0] = REPORT_ID.toByte()
        pkt[1] = (reg and 0xFF).toByte()
        pkt[5] = 'W'.code.toByte()
        BytePack.u16Le(pkt, 7, low16)
        BytePack.u16Le(pkt, 9, high16)
        return pkt
    }

    private fun dbToChipGainIndex(db: Double): Int {
        var bestIdx = 13 // 0 dB
        var bestDiff = Double.MAX_VALUE
        for (i in 1 until CHIP_DAC_DB.size) {
            val diff = kotlin.math.abs(CHIP_DAC_DB[i] - db)
            if (diff < bestDiff) {
                bestDiff = diff
                bestIdx = i
            }
        }
        return bestIdx
    }

    companion object {
        const val REPORT_ID = 0x4B
        private val CHIP_DAC_DB = doubleArrayOf(
            -99.0, -18.0, -16.5, -15.0, -13.5, -12.0, -10.5, -9.0, -7.5, -6.0, -4.5, -3.0, -1.5, 0.0, 1.5, 3.0
        )
    }
}
