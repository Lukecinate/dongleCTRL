package com.suprcharg3d.dongleCTRL.protocol.drivers

import com.suprcharg3d.dongleCTRL.dsp.DefaultBands
import com.suprcharg3d.dongleCTRL.dsp.EqBand
import com.suprcharg3d.dongleCTRL.dsp.FilterType
import com.suprcharg3d.dongleCTRL.protocol.BytePack
import com.suprcharg3d.dongleCTRL.protocol.FiioDeviceSchema
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure-Kotlin FiiO / JadeAudio / SNOWSKY PEQ payload encoder & decoder achieving 100% parity
 * with official FiiO Control (`ScPeqBandHelper`, `ScPeqSavePayloadBuilder`, `ScPeqResetPayloadBuilder`,
 * and `EQSettingUtil`) across every `parseRulerType` (0, 1, 2, 3, 4, 8, 9, 10, 11, 12).
 */
internal object FiioPeqCodec {

    private val GRAPHIC_FREQS_10 = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    private val GRAPHIC_FREQS_5 = intArrayOf(62, 250, 1000, 4000, 16000)

    internal val K19_FREQ_TABLE = doubleArrayOf(
        19.7, 20.1, 20.5, 20.9, 21.3, 21.7, 22.1, 22.5, 23.0, 23.4, 23.9, 24.3, 24.8, 25.3, 25.8,
        26.3, 26.8, 27.3, 27.8, 28.4, 28.9, 29.5, 30.1, 30.7, 31.3, 31.9, 32.5, 33.1, 33.8, 34.4,
        35.1, 35.8, 36.5, 37.2, 37.9, 38.6, 39.4, 40.1, 40.9, 41.7, 42.5, 43.4, 44.2, 45.1, 45.9,
        46.8, 47.7, 48.7, 49.6, 50.6, 51.6, 52.6, 53.6, 54.6, 55.7, 56.8, 57.9, 59.0, 60.1, 61.3,
        62.5, 63.7, 65.0, 66.2, 67.5, 68.8, 70.2, 71.5, 72.9, 74.3, 75.8, 77.2, 78.7, 80.3, 81.8,
        83.4, 85.0, 86.7, 88.4, 90.1, 91.9, 94.0, 95.0, 97.0, 99.0, 101.0, 103.0, 105.0, 107.0,
        109.0, 111.0, 114.0, 116.0, 118.0, 120.0, 123.0, 125.0, 127.0, 130.0, 132.0, 135.0, 138.0,
        140.0, 143.0, 146.0, 149.0, 152.0, 154.0, 157.0, 161.0, 164.0, 167.0, 170.0, 173.0, 177.0,
        180.0, 184.0, 187.0, 191.0, 195.0, 198.0, 202.0, 206.0, 210.0, 214.0, 218.0, 223.0, 227.0,
        231.0, 236.0, 241.0, 245.0, 250.0, 255.0, 260.0, 265.0, 270.0, 275.0, 281.0, 286.0, 292.0,
        297.0, 303.0, 309.0, 315.0, 321.0, 327.0, 334.0, 340.0, 347.0, 354.0, 360.0, 367.0, 375.0,
        382.0, 389.0, 397.0, 405.0, 412.0, 420.0, 429.0, 437.0, 445.0, 454.0, 463.0, 472.0, 481.0,
        490.0, 500.0, 510.0, 520.0, 530.0, 540.0, 551.0, 561.0, 572.0, 583.0, 595.0, 606.0, 618.0,
        630.0, 642.0, 655.0, 667.0, 680.0, 694.0, 707.0, 721.0, 735.0, 749.0, 764.0, 779.0, 794.0,
        809.0, 825.0, 841.0, 857.0, 874.0, 891.0, 908.0, 926.0, 944.0, 962.0, 981.0, 1000.0,
        1020.0, 1040.0, 1060.0, 1080.0, 1100.0, 1120.0, 1140.0, 1170.0, 1190.0, 1210.0, 1240.0,
        1260.0, 1280.0, 1310.0, 1330.0, 1360.0, 1390.0, 1410.0, 1440.0, 1470.0, 1500.0, 1530.0,
        1560.0, 1590.0, 1620.0, 1650.0, 1680.0, 1710.0, 1750.0, 1780.0, 1820.0, 1850.0, 1890.0,
        1920.0, 1960.0, 2000.0, 2040.0, 2080.0, 2120.0, 2160.0, 2200.0, 2240.0, 2290.0, 2330.0,
        2380.0, 2420.0, 2470.0, 2520.0, 2570.0, 2620.0, 2670.0, 2720.0, 2770.0, 2830.0, 2880.0,
        2940.0, 3000.0, 3050.0, 3110.0, 3170.0, 3240.0, 3300.0, 3360.0, 3430.0, 3500.0, 3560.0,
        3630.0, 3700.0, 3780.0, 3850.0, 3920.0, 4000.0, 4080.0, 4160.0, 4240.0, 4320.0, 4400.0,
        4490.0, 4580.0, 4670.0, 4760.0, 4850.0, 4940.0, 5040.0, 5140.0, 5240.0, 5340.0, 5440.0,
        5550.0, 5660.0, 5770.0, 5880.0, 5990.0, 6110.0, 6230.0, 6350.0, 6470.0, 6600.0, 6730.0,
        6860.0, 6990.0, 7130.0, 7270.0, 7410.0, 7550.0, 7700.0, 7850.0, 8000.0, 8160.0, 8310.0,
        8480.0, 8640.0, 8810.0, 8980.0, 9150.0, 9330.0, 9510.0, 9700.0, 9890.0, 10100.0, 10300.0,
        10500.0, 10700.0, 10900.0, 11100.0, 11300.0, 11500.0, 11800.0, 12000.0, 12200.0, 12500.0,
        12700.0, 12900.0, 13200.0, 13500.0, 13700.0, 14000.0, 14300.0, 14500.0, 14800.0, 15100.0,
        15400.0, 15700.0, 16000.0, 16300.0, 16600.0, 17000.0, 17300.0, 17600.0, 18000.0, 18300.0,
        18700.0, 19000.0, 19400.0, 19800.0, 20200.0, 20600.0
    )

    internal val K19_Q_TABLE = doubleArrayOf(
        0.4, 0.42, 0.45, 0.47, 0.5, 0.53, 0.56, 0.59, 0.63, 0.67, 0.71, 0.75, 0.79, 0.84, 0.89,
        0.94, 1.0, 1.06, 1.12, 1.19, 1.26, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0, 2.1, 2.2, 2.4,
        2.5, 2.7, 2.8, 3.0, 3.2, 3.4, 3.6, 3.8, 4.0, 4.2, 4.5, 4.8, 5.0, 5.3, 5.7, 6.0, 6.3, 6.7,
        7.1, 7.6, 8.0, 8.5, 9.0, 9.5, 10.1, 10.7, 11.3, 12.0, 12.7, 13.5, 14.0, 15.0, 16.0, 17.0,
        18.0, 19.0, 20.0, 21.0, 23.0, 24.0, 25.0, 27.0, 29.0, 30.0, 32.0, 34.0, 36.0, 38.0, 40.0,
        43.0, 45.0, 48.0, 51.0, 54.0, 57.0, 60.0, 64.0, 68.0, 72.0, 76.0, 81.0, 85.0, 91.0, 96.0,
        102.0, 108.0, 114.0, 121.0, 128.0
    )

    fun isGraphicRuler(parseRulerType: Int): Boolean =
        parseRulerType == 1 || parseRulerType == 4 || parseRulerType == 10

    fun graphicFreqs(parseRulerType: Int): IntArray =
        if (parseRulerType == 4 || parseRulerType == 10) GRAPHIC_FREQS_5 else GRAPHIC_FREQS_10

    /** Maps a user preset ID (`eqValue`) to wire slot index per `EQSettingUtil::getSendIndexFromUseIndex`. */
    fun getSendIndexFromUseIndex(deviceType: Int, eqValue: Int): Int {
        return when (deviceType) {
            19, 23 -> when (eqValue) {
                3 -> 0
                4 -> 1
                10 -> 2
                else -> -1
            }
            12, 13, 15, 18, 20, 21, 22, 24, 26, 27, 28 -> when (eqValue) {
                4 -> 0
                8 -> 1
                9 -> 2
                else -> -1
            }
            29, 31, 32 -> if (eqValue > 6) eqValue - 7 else -1
            38 -> if (eqValue in 160..239) eqValue - 160 else -1
            else -> if (eqValue in 160..239) eqValue else -1
        }
    }

    /** Exact `ScPeqBandHelper::presetSlotIndexForSingle`. */
    fun presetSlotIndexForSingle(schema: FiioDeviceSchema, eqValue: Int): Int = when (schema.parseRulerType) {
        2 -> getSendIndexFromUseIndex(schema.deviceType, eqValue)
        3 -> eqValue
        else -> 0
    }

    /** `eqSwitch.parseRulerType` decode: ruler 1 is KA17 inverted (`b != 0x10 && b != 0x0b`), ruler 2 (`b == 0`). */
    fun decodeEqSwitch(raw: ByteArray, eqSwitchRuler: Int): Boolean {
        if (raw.isEmpty()) return true
        val b = raw[0].toInt() and 0xFF
        return when (eqSwitchRuler) {
            1 -> b != 0x10 && b != 0x0B
            2 -> b == 0
            else -> b != 0
        }
    }

    fun encodeEqSwitch(enabled: Boolean, eqSwitchRuler: Int): ByteArray = when (eqSwitchRuler) {
        1 -> byteArrayOf(if (enabled) 0x00 else 0x10)
        else -> byteArrayOf(if (enabled) 1 else 0)
    }

    fun buildQueryMasterGainPayload(schema: FiioDeviceSchema, presetId: Int): ByteArray {
        if (schema.parseRulerType == 2) {
            val slot = getSendIndexFromUseIndex(schema.deviceType, presetId)
            if (slot >= 0) return byteArrayOf((slot and 0xFF).toByte())
        }
        return byteArrayOf()
    }

    fun decodeMasterGain(raw: ByteArray, parseRulerType: Int): Double? {
        if (raw.isEmpty()) return null
        return when (parseRulerType) {
            2 -> {
                if (raw.size < 3) return null
                BytePack.getI16Be(raw, 1) / 10.0
            }
            11 -> decodeBr13MasterGain(raw)
            else -> {
                if (raw.size < 2) return null
                BytePack.getI16Be(raw, 0) / 10.0
            }
        }
    }

    fun encodeMasterGain(schema: FiioDeviceSchema, gainDb: Double, presetId: Int): ByteArray {
        return when (schema.parseRulerType) {
            11 -> encodeBr13MasterGain(gainDb)
            2 -> {
                val slot = getSendIndexFromUseIndex(schema.deviceType, presetId).coerceAtLeast(0)
                val raw = (gainDb * 10.0).roundToInt().coerceIn(-32768, 32767)
                byteArrayOf(
                    (slot and 0xFF).toByte(),
                    ((raw ushr 8) and 0xFF).toByte(),
                    (raw and 0xFF).toByte()
                )
            }
            else -> {
                val raw = (gainDb * 10.0).roundToInt().coerceIn(-32768, 32767)
                byteArrayOf(((raw ushr 8) and 0xFF).toByte(), (raw and 0xFF).toByte())
            }
        }
    }

    fun buildQuerySingleBandPayload(schema: FiioDeviceSchema, bandIndex: Int, presetId: Int): ByteArray {
        val ruler = schema.parseRulerType
        return if (ruler == 2) {
            val slot = getSendIndexFromUseIndex(schema.deviceType, presetId).coerceAtLeast(0)
            byteArrayOf((slot and 0xFF).toByte(), (bandIndex and 0xFF).toByte())
        } else if (isGraphicRuler(ruler)) {
            byteArrayOf(((bandIndex + 1) and 0xFF).toByte())
        } else {
            byteArrayOf((bandIndex and 0xFF).toByte())
        }
    }

    fun buildQueryBatchBandsPayload(schema: FiioDeviceSchema, presetId: Int): ByteArray {
        return if (schema.parseRulerType == 2) {
            val slot = getSendIndexFromUseIndex(schema.deviceType, presetId).coerceAtLeast(0)
            byteArrayOf((slot and 0xFF).toByte())
        } else if (schema.eqBandSupportBatch && schema.bandCount > 0) {
            // Standard FiiO batch query for N bands: [0, bandCount - 1]
            byteArrayOf(0x00, ((schema.bandCount - 1).coerceAtLeast(0) and 0xFF).toByte())
        } else {
            byteArrayOf()
        }
    }

    fun decodeSingleBand(raw: ByteArray, schema: FiioDeviceSchema, expectedIndex: Int): EqBand? {
        if (raw.isEmpty()) return null
        val ruler = schema.parseRulerType
        return when {
            isGraphicRuler(ruler) -> {
                if (raw.size < 3) return null
                val freqs = graphicFreqs(ruler)
                val bandIdx = (raw[0].toInt() and 0xFF) - 1
                if (bandIdx !in freqs.indices) return null
                val gain = (BytePack.getI16Be(raw, 1) / 60.0 * 10.0).roundToInt() / 10.0
                EqBand(bandIdx, freqs[bandIdx], gain.coerceIn(schema.gainMin, schema.gainMax), 0.71, FilterType.PEAK)
            }
            ruler == 2 -> {
                if (raw.size < 8) return null
                val off = if (raw.size >= 9) raw.size - 7 else 2
                if (off + 6 > raw.size) return null
                val gain = (BytePack.getI16Be(raw, off) / 10.0).coerceIn(schema.gainMin, schema.gainMax)
                val freq = BytePack.getU16Be(raw, off + 2).coerceIn(20, 20_000)
                val q = (BytePack.getU16Be(raw, off + 4) / 100.0).coerceIn(0.25, 8.0)
                EqBand(expectedIndex, freq, gain, q, FilterType.PEAK)
            }
            ruler == 3 -> decodeK19BandBytes(raw, expectedIndex, hasPrefixBand = true, schema = schema)
            ruler == 11 -> decodeBr13Band(raw, expectedIndex, schema)
            else -> {
                if (raw.size < 8) return null
                val off = if (raw.size >= 9 && (raw[0].toInt() and 0xFF) == (raw[1].toInt() and 0xFF)) 1 else 0
                if (off + 8 > raw.size) return null
                val idx = raw[off].toInt() and 0xFF
                val gain = (BytePack.getI16Be(raw, off + 1) / 10.0).coerceIn(schema.gainMin, schema.gainMax)
                val freq = BytePack.getU16Be(raw, off + 3).coerceIn(20, 20_000)
                val q = (BytePack.getU16Be(raw, off + 5) / 100.0).coerceIn(0.25, 8.0)
                val fType = FilterType.fromFiioCode(raw[off + 7].toInt() and 0xFF)
                EqBand(idx, freq, gain, q, fType)
            }
        }
    }

    fun decodeBatchBands(raw: ByteArray, schema: FiioDeviceSchema): List<EqBand>? {
        if (raw.isEmpty()) return null
        val ruler = schema.parseRulerType
        val count = schema.bandCount.coerceIn(1, 31)
        return when {
            isGraphicRuler(ruler) -> {
                val freqs = graphicFreqs(ruler)
                val out = ArrayList<EqBand>(freqs.size)
                for (i in freqs.indices) {
                    val off = i * 2
                    if (off + 2 > raw.size) break
                    val gain = (BytePack.getI16Be(raw, off) / 60.0 * 10.0).roundToInt() / 10.0
                    out += EqBand(i, freqs[i], gain.coerceIn(schema.gainMin, schema.gainMax), 0.71, FilterType.PEAK)
                }
                if (out.isNotEmpty()) out else null
            }
            ruler == 3 -> {
                val stride = if (raw.size >= count * 7) 7 else 6
                val out = ArrayList<EqBand>(count)
                for (i in 0 until count) {
                    val off = i * stride
                    if (off + 6 > raw.size) break
                    decodeK19BandBytes(raw.copyOfRange(off, off + stride), i, hasPrefixBand = stride >= 7, schema = schema)?.let {
                        out += it
                    }
                }
                if (out.isNotEmpty()) out else null
            }
            ruler == 11 -> {
                // Single or sequential chunks of 16B or 17B
                val out = ArrayList<EqBand>(count)
                val stride = if (raw.size >= count * 17) 17 else 16
                for (i in 0 until count) {
                    val off = i * stride
                    if (off + stride > raw.size) break
                    decodeBr13Band(raw.copyOfRange(off, off + stride), i, schema)?.let { out += it }
                }
                if (out.isNotEmpty()) out else null
            }
            else -> {
                // Standard parametric batch: check 1+N*8 ([count, 0, ...]), N*9 ([idx, idx, ...]), or N*8 ([idx, g, f, q, ft])
                val out = ArrayList<EqBand>(count)
                val hasCountHeader = raw.size >= 9 &&
                    (raw.size - 1) % 8 == 0 &&
                    (raw[0].toInt() and 0xFF) in 1..count &&
                    (raw[1].toInt() and 0xFF) == 0 &&
                    (raw[0].toInt() and 0xFF) != (raw[1].toInt() and 0xFF)
                val entrySize = when {
                    hasCountHeader -> 8
                    raw.size >= 9 && (raw[0].toInt() and 0xFF) == (raw[1].toInt() and 0xFF) && raw.size % 9 == 0 -> 9
                    raw.size % 8 == 0 -> 8
                    raw.size >= 9 && (raw[0].toInt() and 0xFF) == 0 && (raw[1].toInt() and 0xFF) == 0 && raw.size / 9 >= 1 -> 9
                    raw.size >= 8 -> 8
                    else -> return null
                }
                var offset = if (hasCountHeader) 1 else 0
                var seq = 0
                while (offset + 8 <= raw.size && seq < count) {
                    val start = if (entrySize == 9 && offset + 9 <= raw.size) offset + 1 else offset
                    val idx = raw[start].toInt() and 0xFF
                    val gain = (BytePack.getI16Be(raw, start + 1) / 10.0).coerceIn(schema.gainMin, schema.gainMax)
                    val freq = BytePack.getU16Be(raw, start + 3).coerceIn(20, 20_000)
                    val q = (BytePack.getU16Be(raw, start + 5) / 100.0).coerceIn(0.25, 8.0)
                    val fType = FilterType.fromFiioCode(raw[start + 7].toInt() and 0xFF)
                    out += EqBand(if (idx in 0 until count) idx else seq, freq, gain, q, fType)
                    offset += entrySize
                    seq++
                }
                if (out.isNotEmpty()) out else null
            }
        }
    }

    fun encodeSingleBand(
        schema: FiioDeviceSchema,
        band: EqBand,
        presetId: Int,
        batchSet: Boolean = false
    ): ByteArray {
        val ruler = schema.parseRulerType
        return when {
            isGraphicRuler(ruler) -> {
                val gainRaw = ((band.gainDb * 10.0).roundToInt() / 10.0 * 60.0).roundToInt().coerceIn(-32768, 32767)
                if (batchSet) {
                    byteArrayOf(
                        (band.index and 0xFF).toByte(),
                        (band.index and 0xFF).toByte(),
                        ((gainRaw ushr 8) and 0xFF).toByte(),
                        (gainRaw and 0xFF).toByte()
                    )
                } else {
                    byteArrayOf(
                        ((band.index + 1) and 0xFF).toByte(),
                        ((gainRaw ushr 8) and 0xFF).toByte(),
                        (gainRaw and 0xFF).toByte()
                    )
                }
            }
            ruler == 2 -> {
                val slot = getSendIndexFromUseIndex(schema.deviceType, presetId).coerceAtLeast(0)
                val g = (band.gainDb * 10.0).roundToInt().coerceIn(-32768, 32767)
                val f = band.frequencyHz.coerceIn(20, 20_000)
                val q = (band.q * 100.0).roundToInt().coerceIn(25, 800)
                byteArrayOf(
                    (slot and 0xFF).toByte(),
                    (band.index and 0xFF).toByte(),
                    ((g ushr 8) and 0xFF).toByte(),
                    (g and 0xFF).toByte(),
                    ((f ushr 8) and 0xFF).toByte(),
                    (f and 0xFF).toByte(),
                    ((q ushr 8) and 0xFF).toByte(),
                    (q and 0xFF).toByte()
                )
            }
            ruler == 3 -> {
                val raw6 = encodeK19BandBytes(band)
                if (batchSet) {
                    val slot = presetSlotIndexForSingle(schema, presetId).coerceAtLeast(0)
                    byteArrayOf((slot and 0xFF).toByte(), (band.index and 0xFF).toByte()) + raw6
                } else {
                    byteArrayOf((band.index and 0xFF).toByte()) + raw6
                }
            }
            ruler == 11 -> encodeBr13Band(band)
            else -> {
                val g = (band.gainDb * 10.0).roundToInt().coerceIn(-32768, 32767)
                val f = band.frequencyHz.coerceIn(20, 20_000)
                val q = (band.q * 100.0).roundToInt().coerceIn(25, 800)
                val ft = band.filterType.fiioCode and 0xFF
                if (batchSet) {
                    byteArrayOf(
                        (band.index and 0xFF).toByte(),
                        (band.index and 0xFF).toByte(),
                        ((g ushr 8) and 0xFF).toByte(),
                        (g and 0xFF).toByte(),
                        ((f ushr 8) and 0xFF).toByte(),
                        (f and 0xFF).toByte(),
                        ((q ushr 8) and 0xFF).toByte(),
                        (q and 0xFF).toByte(),
                        ft.toByte()
                    )
                } else {
                    byteArrayOf(
                        (band.index and 0xFF).toByte(),
                        ((g ushr 8) and 0xFF).toByte(),
                        (g and 0xFF).toByte(),
                        ((f ushr 8) and 0xFF).toByte(),
                        (f and 0xFF).toByte(),
                        ((q ushr 8) and 0xFF).toByte(),
                        (q and 0xFF).toByte(),
                        ft.toByte()
                    )
                }
            }
        }
    }

    fun encodeBandForWrite(schema: FiioDeviceSchema, band: EqBand, presetId: Int): ByteArray =
        encodeSingleBand(schema, band, presetId, batchSet = schema.eqBandSupportBatch)

    fun buildSavePayload(schema: FiioDeviceSchema, presetId: Int): ByteArray? {
        val wireSlot = if (schema.parseRulerType == 3) presetId - 7 else presetId
        if (wireSlot !in 0..255) return null
        return byteArrayOf((wireSlot and 0xFF).toByte())
    }

    fun buildResetPayload(schema: FiioDeviceSchema, presetId: Int): ByteArray? {
        return when (schema.eqResetRuler) {
            0 -> byteArrayOf()
            1 -> byteArrayOf((presetId and 0xFF).toByte())
            2 -> {
                val slot = getSendIndexFromUseIndex(schema.deviceType, presetId)
                if (slot < 0) null else byteArrayOf(((slot + 1) and 0xFF).toByte())
            }
            else -> byteArrayOf()
        }
    }

    /**
     * Exact parity with `ScPeqEqName::buildQueries` + `SCDevicePeqInfo::getCustomRanges`:
     * contiguous 2-byte `[startSlot, endSlot]` ranges for `editEqNameSupportBatch == true`.
     */
    fun encodeQueryBatchEqNamesPayloads(schema: FiioDeviceSchema, customPresetIds: Collection<Int>): List<ByteArray> {
        val wireIds = customPresetIds.mapNotNull { id ->
            val slot = if (schema.parseRulerType == 9) id - 7 else id
            slot.takeIf { it in 0..255 }
        }.distinct().sorted()
        if (wireIds.isEmpty()) return emptyList()

        val ranges = ArrayList<ByteArray>()
        var start = wireIds[0]
        var end = start
        for (i in 1 until wireIds.size) {
            val cur = wireIds[i]
            if (cur == end + 1) {
                end = cur
            } else {
                ranges += byteArrayOf(start.toByte(), end.toByte())
                start = cur
                end = cur
            }
        }
        ranges += byteArrayOf(start.toByte(), end.toByte())
        return ranges
    }

    /**
     * Parses one batch preset-name response frame (either packed `[start, end, ...N*8B]` or streamed `[slot, slot, ...8B]`),
     * merging non-empty names into [namesOut] and recording covered preset IDs in [coveredSlotsOut].
     */
    fun accumulateBatchEqNamesFrame(
        raw: ByteArray,
        customPresetIds: List<Int>,
        parseRulerType: Int = 0,
        namesOut: MutableMap<Int, String>,
        coveredSlotsOut: MutableSet<Int>
    ): Boolean {
        if (raw.size < 2) return false
        val startId = raw[0].toInt() and 0xFF
        val endId = raw[1].toInt() and 0xFF
        if (endId >= startId && (endId - startId + 1) <= 64 && raw.size >= 2 + (endId - startId + 1) * 8) {
            for (s in startId..endId) {
                coveredSlotsOut += if (parseRulerType == 9) s + 7 else s
            }
        }
        val decoded = decodeBatchEqNames(raw, customPresetIds, parseRulerType)
        namesOut.putAll(decoded)
        return decoded.isNotEmpty() || endId >= startId
    }

    fun encodeQueryEditEqNamePayload(schema: FiioDeviceSchema, presetId: Int): ByteArray {
        val slot = resolveEqNameSlot(schema, presetId).coerceAtLeast(0)
        return if (slot <= 0xFF) {
            byteArrayOf((slot and 0xFF).toByte())
        } else {
            byteArrayOf(((slot ushr 8) and 0xFF).toByte(), (slot and 0xFF).toByte())
        }
    }

    fun decodeEditEqNamePayload(schema: FiioDeviceSchema, queriedPresetId: Int, raw: ByteArray): Pair<Int, String>? {
        if (raw.isEmpty()) return null
        val queriedSlot = resolveEqNameSlot(schema, queriedPresetId).coerceAtLeast(0)
        val slotBytesLen = if (queriedSlot > 0xFF || raw.size >= 10) 2 else 1
        if (raw.size <= slotBytesLen) return null
        val text = decodeFixedNameSlice(raw, slotBytesLen, raw.size - slotBytesLen)
        return queriedPresetId to text
    }

    /**
     * Exact parity with `ScPeqEqName::_decodeBatchPlain`:
     * response is `[startSlot, endSlot, ...8B UTF-8 entries per slot]` (or legacy 9/10-byte id-prefixed records).
     */
    fun decodeBatchEqNames(
        raw: ByteArray,
        customPresetIds: List<Int>,
        parseRulerType: Int = 0
    ): Map<Int, String> {
        if (raw.isEmpty()) return emptyMap()
        val out = LinkedHashMap<Int, String>()

        if (raw.size >= 2) {
            val startId = raw[0].toInt() and 0xFF
            val endId = raw[1].toInt() and 0xFF
            val count = endId - startId + 1
            if (endId >= startId && count in 1..64 && raw.size >= 2 + count * 8) {
                for (i in 0 until count) {
                    val slot = startId + i
                    val key = if (parseRulerType == 9) slot + 7 else slot
                    val text = decodeFixedNameSlice(raw, 2 + i * 8, 8)
                    if (text.isNotEmpty()) out[key] = text
                }
                return out
            }
        }

        // Fallback for legacy 9-byte ([slot1B, ...8B utf8]) or 10-byte ([slot2B, ...8B utf8]) entries
        val has2ByteIds = customPresetIds.any { it > 0xFF }
        val entrySize = if (has2ByteIds && raw.size % 10 == 0) 10 else if (raw.size % 9 == 0) 9 else 0
        if (entrySize == 0) return emptyMap()
        var off = 0
        while (off + entrySize <= raw.size) {
            val idLen = if (entrySize == 10) 2 else 1
            val id = if (idLen == 2) BytePack.getU16Be(raw, off) else (raw[off].toInt() and 0xFF)
            val text = decodeFixedNameSlice(raw, off + idLen, entrySize - idLen)
            if (text.isNotEmpty()) out[id] = text
            off += entrySize
        }
        return out
    }

    /**
     * Exact parity with `ScPeqBandHelper::buildSetEqNamePayloadBatch` (20B `[slot, slot, ...utf8]`)
     * and `ScPeqBandHelper::buildSetEqNamePayloadSingle` (18B `[slot, ...utf8]` or 20B `[hi, lo, ...utf8]`).
     */
    fun buildEditEqNamePayload(schema: FiioDeviceSchema, presetId: Int, name: String): ByteArray? {
        val utf8 = trimUtf8ToMaxBytes(name.trim(), 8)
        if (utf8.isEmpty()) return null
        if (schema.editEqNameSupportBatch) {
            if (schema.parseRulerType in 1..4) return null
            if (presetId !in 0..255) return null
            val slot = (presetId and 0xFF).toByte()
            val out = ByteArray(20)
            out[0] = slot
            out[1] = slot
            utf8.copyInto(out, destinationOffset = 2)
            return out
        }
        val slot = resolveEqNameSlot(schema, presetId)
        if (slot < 0) return null
        return if (slot <= 0xFF) {
            val out = ByteArray(18)
            out[0] = (slot and 0xFF).toByte()
            utf8.copyInto(out, destinationOffset = 1)
            out
        } else {
            val out = ByteArray(20)
            out[0] = ((slot ushr 8) and 0xFF).toByte()
            out[1] = (slot and 0xFF).toByte()
            utf8.copyInto(out, destinationOffset = 2)
            out
        }
    }

    private fun resolveEqNameSlot(schema: FiioDeviceSchema, presetId: Int): Int = when (schema.parseRulerType) {
        9 -> presetId - 7
        2, 3 -> getSendIndexFromUseIndex(schema.deviceType, presetId)
        else -> presetId
    }

    private fun decodeFixedNameSlice(raw: ByteArray, offset: Int, maxLen: Int): String {
        if (offset >= raw.size || maxLen <= 0) return ""
        val end = minOf(offset + maxLen, raw.size)
        return raw.copyOfRange(offset, end)
            .takeWhile { it != 0.toByte() }
            .toByteArray()
            .decodeToString()
            .trim()
    }

    private fun trimUtf8ToMaxBytes(text: String, maxBytes: Int): ByteArray {
        val full = text.encodeToByteArray()
        if (full.size <= maxBytes) return full
        var cut = maxBytes
        // Back off from UTF-8 continuation bytes (10xxxxxx) so multi-byte characters stay intact
        while (cut > 0 && (full[cut].toInt() and 0xC0) == 0x80) cut--
        return if (cut > 0) full.copyOf(cut) else ByteArray(0)
    }

    private fun decodeK19BandBytes(raw: ByteArray, defaultIdx: Int, hasPrefixBand: Boolean, schema: FiioDeviceSchema): EqBand? {
        val off = if (hasPrefixBand && raw.size >= 7) 1 else 0
        if (off + 6 > raw.size) return null
        val freqIdx = BytePack.getU16Be(raw, off).coerceIn(0, K19_FREQ_TABLE.lastIndex)
        val gainRaw = BytePack.getU16Be(raw, off + 2)
        val qIdx = (raw[off + 4].toInt() and 0xFF).coerceIn(0, K19_Q_TABLE.lastIndex)
        val filterCode = raw[off + 5].toInt() and 0xFF
        val bandIdx = if (hasPrefixBand && raw.size >= 7) (raw[0].toInt() and 0xFF) else defaultIdx
        val freq = K19_FREQ_TABLE[freqIdx].roundToInt().coerceIn(20, 20_000)
        val gain = ((gainRaw * 0.2 - 24.0) * 10.0).roundToInt() / 10.0
        val q = K19_Q_TABLE[qIdx]
        return EqBand(bandIdx, freq, gain.coerceIn(schema.gainMin, schema.gainMax), q, FilterType.fromFiioCode(filterCode))
    }

    private fun encodeK19BandBytes(band: EqBand): ByteArray {
        val fIdx = nearestIndex(K19_FREQ_TABLE, band.frequencyHz.toDouble())
        val gIdx = ((band.gainDb + 24.0) / 0.2).roundToInt().coerceIn(0, 240)
        val qIdx = nearestIndex(K19_Q_TABLE, band.q)
        return byteArrayOf(
            ((fIdx ushr 8) and 0xFF).toByte(),
            (fIdx and 0xFF).toByte(),
            ((gIdx ushr 8) and 0xFF).toByte(),
            (gIdx and 0xFF).toByte(),
            (qIdx and 0xFF).toByte(),
            (band.filterType.fiioCode and 0xFF).toByte()
        )
    }

    internal fun nearestIndex(table: DoubleArray, target: Double): Int {
        var bestIdx = 0
        var bestDiff = Double.MAX_VALUE
        for (i in table.indices) {
            val d = abs(table[i] - target)
            if (d < bestDiff) {
                bestDiff = d
                bestIdx = i
            }
        }
        return bestIdx
    }

    private fun swapInt32Words(bytes: ByteArray): ByteArray {
        val out = bytes.copyOf()
        var i = 0
        while (i + 3 < out.size) {
            val b0 = out[i]
            val b1 = out[i + 1]
            out[i] = out[i + 3]
            out[i + 1] = out[i + 2]
            out[i + 2] = b1
            out[i + 3] = b0
            i += 4
        }
        return out
    }

    private fun readBeInt32(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 >= bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun decodeBr13Band(raw: ByteArray, defaultIdx: Int, schema: FiioDeviceSchema): EqBand? {
        if (raw.size < 16) return null
        val hasBandPrefix = raw.size >= 17
        val bandIdx = if (hasBandPrefix) (raw[0].toInt() and 0xFF) else defaultIdx
        val sliceStart = if (hasBandPrefix) 1 else 0
        val swapped = swapInt32Words(raw.copyOfRange(sliceStart, sliceStart + 16))
        val freq = (readBeInt32(swapped, 4) / 4096.0).roundToInt().coerceIn(20, 20_000)
        val gain = ((readBeInt32(swapped, 8) / 1048576.0) * 10.0).roundToInt() / 10.0
        val q = ((readBeInt32(swapped, 12) / 16777216.0) * 100.0).roundToInt() / 100.0
        return EqBand(
            index = bandIdx,
            frequencyHz = freq,
            gainDb = gain.coerceIn(schema.gainMin, schema.gainMax),
            q = q.coerceIn(0.25, 16.0),
            filterType = FilterType.PEAK
        )
    }

    private fun encodeBr13Band(band: EqBand): ByteArray {
        val out = ByteArray(13)
        out[0] = (band.index and 0xFF).toByte()
        val fFixed = band.frequencyHz.coerceIn(20, 20_000) * 4096
        val gRounded = (band.gainDb * 10.0).roundToInt() / 10.0
        val gFixed = (gRounded * 1048576.0).roundToInt()
        val qRounded = (band.q * 100.0).roundToInt() / 100.0
        val qFixed = (qRounded * 16777216.0).roundToInt()
        writeBeInt32(out, 1, fFixed)
        writeBeInt32(out, 5, gFixed)
        writeBeInt32(out, 9, qFixed)
        return out
    }

    private fun decodeBr13MasterGain(raw: ByteArray): Double? {
        val rawInt = when {
            raw.size >= 7 -> readBeInt32(raw, 3)
            raw.size >= 4 -> readBeInt32(raw, 0)
            else -> return null
        }
        return ((rawInt / 1048576.0) * 10.0).roundToInt() / 10.0
    }

    private fun encodeBr13MasterGain(gainDb: Double): ByteArray {
        val out = ByteArray(7)
        out[2] = 0x0A
        val fixed = (gainDb * 1048576.0).roundToInt()
        writeBeInt32(out, 3, fixed)
        return out
    }

    private fun writeBeInt32(buf: ByteArray, offset: Int, v: Int) {
        buf[offset] = ((v ushr 24) and 0xFF).toByte()
        buf[offset + 1] = ((v ushr 16) and 0xFF).toByte()
        buf[offset + 2] = ((v ushr 8) and 0xFF).toByte()
        buf[offset + 3] = (v and 0xFF).toByte()
    }

    fun flatBandsForSchema(schema: FiioDeviceSchema): List<EqBand> {
        val count = schema.bandCount.coerceIn(1, 31)
        if (isGraphicRuler(schema.parseRulerType)) {
            val freqs = graphicFreqs(schema.parseRulerType)
            return List(count) { idx ->
                EqBand(idx, freqs.getOrElse(idx) { 1000 }, 0.0, 0.71, FilterType.PEAK)
            }
        }
        return DefaultBands.flatBands(count)
    }
}
