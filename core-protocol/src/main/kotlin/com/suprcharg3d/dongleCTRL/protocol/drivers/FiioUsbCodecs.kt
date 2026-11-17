package com.suprcharg3d.dongleCTRL.protocol.drivers

import com.suprcharg3d.dongleCTRL.protocol.DacTransport
import kotlinx.coroutines.delay

/**
 * Pure-Kotlin codecs and USB transport helpers for all FiiO proprietary USB / bridge sub-protocols
 * (`KA5/KA3/KA2 C7A5`, `KA1 REG64`, `Q11/KA13/KA11 XMOS`, `BT11/AirLink Jiedian`, and `ScBatchCommandCodec`).
 */
internal object FiioUsbCodecs {

    // ---------------------------------------------------------------------------------------------
    // C7A5 / KA5 (61449) / KA3 (61452) / KA2 (61454)
    // ---------------------------------------------------------------------------------------------

    data class ReadSpec(val cmdId: Int, val aggReg: Int, val offset: Int, val length: Int = 1)

    // Exact ReadSpec maps extracted from Blutter pp+0x371a8 (KA5), pp+0x37200 (KA3), pp+0x37208 (KA2)
    private val KA5_SPECS = mapOf(
        0x01 to ReadSpec(0x01, 0xA3, 3, 1),
        0x02 to ReadSpec(0x02, 0xA3, 4, 1),
        0x04 to ReadSpec(0x04, 0xA2, 4, 1),
        0x05 to ReadSpec(0x05, 0xA2, 5, 2),
        0x06 to ReadSpec(0x06, 0xA3, 5, 1),
        0x07 to ReadSpec(0x07, 0xA3, 6, 1),
        0x08 to ReadSpec(0x08, 0xA4, 3, 1),
        0x09 to ReadSpec(0x09, 0xA4, 4, 1),
        0x0A to ReadSpec(0x0A, 0xA0, 6, 1),
        0x0B to ReadSpec(0x0B, 0xA4, 5, 1),
        0x0C to ReadSpec(0x0C, 0xA4, 6, 1),
        0x0D to ReadSpec(0x0D, 0xA0, 5, 1),
        0xA0 to ReadSpec(0xA0, 0xA0, 3, 1),
        0xA1 to ReadSpec(0xA1, 0xA1, 3, 1)
    )

    private val KA3_SPECS = mapOf(
        0x01 to ReadSpec(0x01, 0xA3, 3, 1),
        0x02 to ReadSpec(0x02, 0xA3, 4, 1),
        0x03 to ReadSpec(0x03, 0xA2, 3, 1),
        0x04 to ReadSpec(0x04, 0xA2, 4, 1),
        0x05 to ReadSpec(0x05, 0xA2, 5, 2),
        0x06 to ReadSpec(0x06, 0xA3, 5, 1),
        0x07 to ReadSpec(0x07, 0xA3, 6, 1),
        0x08 to ReadSpec(0x08, 0xA3, 2, 1),
        0x09 to ReadSpec(0x09, 0xA3, 1, 1),
        0xA0 to ReadSpec(0xA0, 0xA0, 3, 3),
        0xA1 to ReadSpec(0xA1, 0xA1, 3, 1)
    )

    private val KA2_SPECS = mapOf(
        0x01 to ReadSpec(0x01, 0xA3, 3, 1),
        0x02 to ReadSpec(0x02, 0xA3, 4, 1),
        0x03 to ReadSpec(0x03, 0xA2, 3, 1),
        0x04 to ReadSpec(0x04, 0xA2, 4, 1),
        0x05 to ReadSpec(0x05, 0xA2, 5, 2),
        0x06 to ReadSpec(0x06, 0xA3, 5, 1),
        0x07 to ReadSpec(0x07, 0xA3, 6, 1),
        0xA0 to ReadSpec(0xA0, 0xA0, 3, 3),
        0xA1 to ReadSpec(0xA1, 0xA1, 3, 1)
    )

    // KA5 volume step delta tables summing to 255
    private val KA5_VOL_DELTA_B_60 = buildList {
        add(65); repeat(8) { add(8) }; repeat(6) { add(6) }; repeat(10) { add(4) }
        repeat(5) { add(2) }; repeat(10) { add(2) }; repeat(20) { add(1) }
    }.toIntArray() // 60 steps (or 0..60)

    private val KA5_VOL_DELTA_A_120 = buildList {
        add(65); add(8); repeat(8) { add(4) }; repeat(40) { add(2) }; repeat(70) { add(1) }
    }.toIntArray() // 120 steps

    private val KA2_VOL_STEP_TABLE = buildList {
        repeat(99) { add(1) }; repeat(10) { add(2) }; repeat(11) { add(4) }
    }.toIntArray()

    private val KA5_BRIGHTNESS_TO_REG = mapOf(
        1 to 0x00, 2 to 0x0A, 3 to 0x19, 4 to 0x28, 5 to 0x3C,
        6 to 0x50, 7 to 0x69, 8 to 0x82, 9 to 0xA0, 10 to 0xC8
    )

    fun specsForCbt(cbt: Int): Map<Int, ReadSpec> = when (cbt) {
        61449 -> KA5_SPECS
        61452 -> KA3_SPECS
        else -> KA2_SPECS
    }

    fun buildC7A5Frame(cmdId: Int, data: ByteArray = byteArrayOf()): ByteArray {
        val out = ByteArray(7)
        out[0] = 0xC7.toByte()
        out[1] = 0xA5.toByte()
        out[2] = (cmdId and 0xFF).toByte()
        for (i in 0 until minOf(4, data.size)) {
            out[3 + i] = data[i]
        }
        return out
    }

    fun ka5VolLevelToReg(level: Int, volCtrl: Int): Int {
        val table = if (volCtrl == 0) KA5_VOL_DELTA_A_120 else KA5_VOL_DELTA_B_60
        val clamped = level.coerceIn(0, table.size)
        var reg = 255
        for (i in 0 until clamped) reg -= table[i]
        return reg.coerceIn(0, 255)
    }

    fun ka5VolRegToLevel(reg: Int, volCtrl: Int): Int {
        val table = if (volCtrl == 0) KA5_VOL_DELTA_A_120 else KA5_VOL_DELTA_B_60
        var acc = 255
        if (reg >= 255) return 0
        for (i in table.indices) {
            acc -= table[i]
            if (acc <= reg) return i + 1
        }
        return table.size
    }

    fun ka3VolLevelToReg(level: Int): Int {
        val c = level.coerceIn(0, 120)
        return if (c >= 100) 120 - c else (100 - c) + 20
    }

    fun ka3VolRegToLevel(reg: Int): Int {
        val r = reg and 0xFF
        return when {
            r <= 0 -> 120
            r <= 20 -> 120 - r
            r < 140 -> 120 - r
            else -> 0
        }.coerceIn(0, 120)
    }

    fun ka2VolLevelToReg(level: Int): Int {
        val stepsFromMax = (120 - level.coerceIn(0, 120)).coerceIn(0, KA2_VOL_STEP_TABLE.size)
        var reg = 0
        for (i in 0 until stepsFromMax) reg += KA2_VOL_STEP_TABLE[i]
        return reg.coerceIn(0, 255)
    }

    fun ka2VolRegToLevel(reg: Int): Int {
        var best = 120
        var bestDiff = Int.MAX_VALUE
        for (lvl in 0..120) {
            val d = kotlin.math.abs(ka2VolLevelToReg(lvl) - (reg and 0xFF))
            if (d < bestDiff) {
                bestDiff = d
                best = lvl
            }
        }
        return best
    }

    fun encodeC7A5Write(cbt: Int, cmdId: Int, payload: ByteArray, volCtrl: Int = 0): ByteArray {
        val b0 = payload.getOrNull(0)?.toInt()?.and(0xFF) ?: 0
        return when (cmdId) {
            0x04 -> {
                val reg = when (cbt) {
                    61449 -> ka5VolLevelToReg(b0, volCtrl)
                    61452 -> ka3VolLevelToReg(b0)
                    else -> ka2VolLevelToReg(b0)
                }
                buildC7A5Frame(cmdId, byteArrayOf((reg and 0xFF).toByte()))
            }
            0x0B -> {
                val reg = if (cbt == 61449) (KA5_BRIGHTNESS_TO_REG[b0] ?: 60) else b0
                buildC7A5Frame(cmdId, byteArrayOf((reg and 0xFF).toByte()))
            }
            0x02 -> {
                // KA2 increments option by +1 (`Ka2Codec::encodeWrite` L382)
                val v = if (cbt == 61454) (b0 + 1) else b0
                buildC7A5Frame(cmdId, byteArrayOf((v and 0xFF).toByte()))
            }
            else -> buildC7A5Frame(cmdId, payload)
        }
    }

    fun extractC7A5Read(cbt: Int, cmdId: Int, a7Frame: ByteArray, volCtrl: Int = 0): ByteArray? {
        if (a7Frame.size < 7 || (a7Frame[0].toInt() and 0xFF) != 0xA7) return null
        val spec = specsForCbt(cbt)[cmdId] ?: return null
        if (spec.offset + spec.length > a7Frame.size) return null
        return when {
            cmdId == 0x04 -> {
                val reg = a7Frame[spec.offset].toInt() and 0xFF
                val lvl = when (cbt) {
                    61449 -> ka5VolRegToLevel(reg, volCtrl)
                    61452 -> ka3VolRegToLevel(reg)
                    else -> ka2VolRegToLevel(reg)
                }
                byteArrayOf((lvl and 0xFF).toByte())
            }
            cmdId == 0x05 -> {
                // Balance 2 bytes at offset 5..6
                byteArrayOf(a7Frame[spec.offset], a7Frame[spec.offset + 1])
            }
            cmdId == 0x0B && cbt == 61449 -> {
                val reg = a7Frame[spec.offset].toInt() and 0xFF
                val lvl = KA5_BRIGHTNESS_TO_REG.entries.minByOrNull { kotlin.math.abs(it.value - reg) }?.key ?: 1
                byteArrayOf(lvl.toByte())
            }
            cmdId == 0x02 && cbt == 61452 -> {
                val v = ((a7Frame[spec.offset].toInt() and 0xFF) - 1).coerceAtLeast(0)
                byteArrayOf(v.toByte())
            }
            cmdId == 0xA0 -> {
                val rawVer = a7Frame[spec.offset].toInt() and 0xFF
                val text = "v${rawVer / 100}.${(rawVer % 100) / 10}.${rawVer % 10}"
                text.toByteArray(Charsets.UTF_8)
            }
            else -> a7Frame.copyOfRange(spec.offset, spec.offset + spec.length)
        }
    }

    suspend fun transceiveC7A5Aggregate(
        transport: DacTransport,
        aggReg: Int,
        acInterfaceIndex: Int = 0
    ): ByteArray? {
        val req = buildC7A5Frame(aggReg)
        val outOk = transport.controlTransfer(
            requestType = 0x43,
            request = 0xA0,
            value = 0x09A0,
            index = acInterfaceIndex,
            buffer = req,
            timeoutMs = 1000
        )
        if (outOk < 0) return null
        delay(20)
        val resp = ByteArray(7)
        val read = transport.controlTransfer(
            requestType = 0xC3,
            request = 0xA1,
            value = 0x09A0,
            index = acInterfaceIndex,
            buffer = resp,
            timeoutMs = 1000
        )
        return if (read >= 7 && (resp[0].toInt() and 0xFF) == 0xA7) resp else null
    }

    suspend fun writeC7A5Control(
        transport: DacTransport,
        cbt: Int,
        cmdId: Int,
        payload: ByteArray,
        volCtrl: Int = 0,
        acInterfaceIndex: Int = 0
    ): Boolean {
        val frame = encodeC7A5Write(cbt, cmdId, payload, volCtrl)
        return transport.controlTransfer(
            requestType = 0x43,
            request = 0xA0,
            value = 0x09A0,
            index = acInterfaceIndex,
            buffer = frame,
            timeoutMs = 1000
        ) >= frame.size
    }

    // ---------------------------------------------------------------------------------------------
    // KA1 REG64 (61450)
    // ---------------------------------------------------------------------------------------------

    private val KA1_READ_REG = mapOf(
        1 to 0xE3, 2 to 0xE8, 3 to 0xFE, 4 to 0xF9,
        5 to 0xEA, 6 to 0xEA, 7 to 0xEA, 8 to 0xEA, 9 to 0xEB
    )

    fun ka1IsReg234Field(cmdId: Int): Boolean = KA1_READ_REG[cmdId] == 0xEA

    fun ka1Extract(cmdId: Int, regVal: Int): ByteArray = when (cmdId) {
        5 -> byteArrayOf(((regVal ushr 4) and 0x03).toByte()) // indicator
        6 -> byteArrayOf(((regVal ushr 2) and 0x01).toByte()) // mic_detect
        7 -> byteArrayOf(((regVal ushr 3) and 0x01).toByte()) // uac_version
        8 -> byteArrayOf((regVal and 0x0F).toByte())          // filter
        else -> byteArrayOf((regVal and 0xFF).toByte())
    }

    fun ka1ApplyReg234Field(cmdId: Int, currentEa: Int, value: Int): Int = when (cmdId) {
        5 -> (currentEa and 0xCF) or ((value and 0x03) shl 4)
        6 -> if (value != 0) (currentEa or 0x04) else (currentEa and 0xFB)
        7 -> if (value != 0) (currentEa or 0x08) else (currentEa and 0xF7)
        else -> (currentEa and 0xF0) or (value and 0x0F)
    }

    fun ka1ReadRegOf(cmdId: Int): Int? = KA1_READ_REG[cmdId]

    // ---------------------------------------------------------------------------------------------
    // KA13 / KA11 / Q11 XMOS I2C (61448)
    // ---------------------------------------------------------------------------------------------

    fun ka13BuildReadReport(cmdId: Int, seq: Int): ByteArray? {
        val body: ByteArray = when (cmdId) {
            1 -> byteArrayOf(0x0A, 0x12, 0xE4.toByte(), 0xA2.toByte(), 0x46, 0x01, (seq and 0xFF).toByte())
            2 -> byteArrayOf(0x08, 0x12, 0xE4.toByte(), 0xA2.toByte(), 0x02, 0x02, (seq and 0xFF).toByte())
            3 -> byteArrayOf(0x04, 0x12, 0xE4.toByte(), 0xA2.toByte(), 0x02, 0x02, 0x01)
            4 -> byteArrayOf(0x0C, 0x12, 0xE4.toByte(), 0xA2.toByte(), 0x10, 0x01, (seq and 0xFF).toByte())
            6 -> byteArrayOf(0x09, 0x12, 0xE4.toByte(), 0xA2.toByte(), 0x45, 0x01, (seq and 0xFF).toByte())
            0, 9 -> byteArrayOf(0x0B, 0x12, 0xE4.toByte(), 0xA2.toByte(), 0x47, 0x01, (seq and 0xFF).toByte())
            else -> return null
        }
        return ByteArray(64).also { body.copyInto(it) }
    }

    fun ka13DecodeRead(cmdId: Int, rawReport: ByteArray): ByteArray? {
        if (rawReport.size < 4) return null
        val b = rawReport[0].toInt() and 0xFF
        val v1 = rawReport[1].toInt() and 0xFF
        return when (cmdId) {
            1 -> byteArrayOf(if ((b and 0x02) != 0) 1 else 0) // func_spdif
            2 -> byteArrayOf((v1 and 0x03).toByte())            // indicator
            3 -> byteArrayOf((b and 0x07).toByte())             // filter
            4 -> byteArrayOf((b.coerceIn(0, 120)).toByte())     // volume
            6 -> byteArrayOf((b and 0x01).toByte())             // uac_version
            0, 9 -> byteArrayOf((b and 0x0F).toByte())          // sample rate index
            else -> null
        }
    }

    /**
     * Optional KA13/KA11/Q11 XMOS UAC left-channel quirk read (`ka13_hid_io.dart readUacLeft`).
     * Best-effort: swallows vendor/interface errors when Android or non-XMOS firmware rejects `0xA0`.
     */
    suspend fun ka13ReadUacLeft(transport: DacTransport): ByteArray? = try {
        val buf = ByteArray(4)
        val n = transport.controlTransfer(0xA0, 0x01, 0x0A01, 0x0201, buf, 500)
        if (n > 0) buf.copyOf(n) else null
    } catch (_: Throwable) {
        null
    }

    // ---------------------------------------------------------------------------------------------
    // GigaWit / Jiedian USB HID 0x07 Bridge (`BT11` / `Air Link` over USB, `protocol_pretty.js:2648`)
    // ---------------------------------------------------------------------------------------------

    fun buildJiedianUsbReport(innerPayload: ByteArray): ByteArray {
        val out = ByteArray(64)
        out[0] = 0x07
        val len = minOf(innerPayload.size, 63)
        innerPayload.copyInto(out, destinationOffset = 1, startIndex = 0, endIndex = len)
        return out
    }

    /**
     * Encodes `An(featureId, cmd, payload)` = `[0xFF, 0x03, 0x00, len, 0x00, 0x1D, featureId << 1, cmd, ...payload]`.
     */
    fun gigaWitBuildPacket(featureId: Int, cmd: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        val out = ByteArray(8 + payload.size)
        out[0] = 0xFF.toByte()
        out[1] = 0x03
        out[2] = 0x00
        out[3] = (payload.size and 0xFF).toByte()
        out[4] = 0x00
        out[5] = 0x1D
        out[6] = ((featureId shl 1) and 0xFF).toByte()
        out[7] = (cmd and 0xFF).toByte()
        payload.copyInto(out, destinationOffset = 8)
        return out
    }

    /**
     * Decodes a GigaWit HID response (`ol(e)` in `protocol_pretty.js`), handling optional leading `0x07` report ID.
     */
    fun gigaWitExtractPayload(rawReport: ByteArray, expectedFeatureId: Int? = null, expectedCmd: Int? = null): ByteArray? {
        val inner = if (rawReport.size > 1 && (rawReport[0].toInt() and 0xFF) == 0x07 &&
            (rawReport[1].toInt() and 0xFF) == 0xFF
        ) {
            rawReport.copyOfRange(1, rawReport.size)
        } else {
            rawReport
        }
        if (inner.size < 8) return null
        if ((inner[0].toInt() and 0xFF) != 0xFF || (inner[1].toInt() and 0xFF) != 0x03) return null
        if (expectedFeatureId != null && ((inner[6].toInt() and 0xFF) ushr 1) != expectedFeatureId) return null
        if (expectedCmd != null && (inner[7].toInt() and 0xFF) != (expectedCmd and 0xFF)) return null
        val len = (inner[3].toInt() and 0xFF).coerceAtMost((inner.size - 8).coerceAtLeast(0))
        return inner.copyOfRange(8, 8 + len)
    }

    // ---------------------------------------------------------------------------------------------
    // ScBatchCommandCodec (0x0F0101 / ScBatchMultiQuery)
    // ---------------------------------------------------------------------------------------------

    data class BatchResponseItem(val cmd24: Int, val status: Int, val payload: ByteArray)

    fun buildBatchQueryRequest(cmds24: List<Int>, maxPayloadBytes: Int = 200): List<ByteArray> {
        val chunks = ArrayList<ByteArray>()
        var current = mutableListOf<Int>()
        for (cmd in cmds24.distinct()) {
            // Each query item takes 4 bytes ([cmdHi, cmdMid, cmdLo, len=0]) plus 1-byte count header
            val projected = 1 + (current.size + 1) * 4
            if (current.size >= 30 || projected > maxPayloadBytes) {
                if (current.isNotEmpty()) chunks += encodeBatchChunk(current)
                current = mutableListOf()
            }
            current.add(cmd)
        }
        if (current.isNotEmpty()) chunks += encodeBatchChunk(current)
        return chunks
    }

    private fun encodeBatchChunk(cmds: List<Int>): ByteArray {
        val out = ByteArray(1 + cmds.size * 4)
        out[0] = (cmds.size and 0xFF).toByte()
        var pos = 1
        for (c in cmds) {
            out[pos++] = ((c ushr 16) and 0xFF).toByte()
            out[pos++] = ((c ushr 8) and 0xFF).toByte()
            out[pos++] = (c and 0xFF).toByte()
            out[pos++] = 0x00 // param len = 0
        }
        return out
    }

    fun parseBatchQueryResponse(payload: ByteArray): List<BatchResponseItem> {
        if (payload.isEmpty()) return emptyList()
        val count = payload[0].toInt() and 0xFF
        val items = ArrayList<BatchResponseItem>(count)
        var pos = 1
        for (i in 0 until count) {
            if (pos + 5 > payload.size) break
            val cmd24 = ((payload[pos].toInt() and 0xFF) shl 16) or
                ((payload[pos + 1].toInt() and 0xFF) shl 8) or
                (payload[pos + 2].toInt() and 0xFF)
            val status = payload[pos + 3].toInt() and 0xFF
            val itemLen = payload[pos + 4].toInt() and 0xFF
            pos += 5
            if (pos + itemLen > payload.size) break
            val data = payload.copyOfRange(pos, pos + itemLen)
            pos += itemLen
            items += BatchResponseItem(cmd24, status, data)
        }
        return items
    }
}
