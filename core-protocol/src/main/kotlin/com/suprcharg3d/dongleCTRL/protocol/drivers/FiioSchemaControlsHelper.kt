package com.suprcharg3d.dongleCTRL.protocol.drivers

import com.suprcharg3d.dongleCTRL.protocol.ControlCategory
import com.suprcharg3d.dongleCTRL.protocol.ControlKind
import com.suprcharg3d.dongleCTRL.protocol.DacControlDescriptor
import com.suprcharg3d.dongleCTRL.protocol.DacControlValue
import com.suprcharg3d.dongleCTRL.protocol.DacSliderField
import com.suprcharg3d.dongleCTRL.protocol.FiioControlDef
import com.suprcharg3d.dongleCTRL.protocol.FiioDeviceSchema
import com.suprcharg3d.dongleCTRL.protocol.NamedOption
import kotlin.math.roundToInt

/**
 * Pure-Kotlin serializer/deserializer for every FiiO schema control (`uiType` + `parseRulerType`)
 * matching official FiiO Control behavior (`SliderPayload`, `AudioSliderPayload`, `BalancePayload`,
 * `SwitchPayload`, `FilterChoosePayload`, `RadioPayload`, `BtEncodePayload`, `ScDeviceNamePayloadBuilder`).
 */
internal object FiioSchemaControlsHelper {

    fun toDescriptor(def: FiioControlDef): DacControlDescriptor {
        val kind = when (def.uiType) {
            0, 2 -> ControlKind.SWITCH
            3, 10 -> ControlKind.SLIDER
            1, 13 -> ControlKind.SINGLE_CHOICE
            8, 15 -> if (def.bitMaskOptions.isNotEmpty()) ControlKind.BITMASK else ControlKind.SINGLE_CHOICE
            11 -> ControlKind.DUAL_SLIDER_SWITCH
            else -> ControlKind.ACTION
        }
        val category = when {
            def.section.equals("audio", ignoreCase = true) ||
                def.key.contains("vol", ignoreCase = true) ||
                def.key.contains("gain", ignoreCase = true) ||
                def.key.contains("harmonic", ignoreCase = true) ||
                def.key.contains("dre", ignoreCase = true) ||
                def.key.contains("boost", ignoreCase = true) ||
                def.key.contains("distortion", ignoreCase = true) -> ControlCategory.AUDIO

            def.key.contains("bt", ignoreCase = true) ||
                def.key.contains("bluetooth", ignoreCase = true) ||
                def.key.contains("codec", ignoreCase = true) ||
                def.key.contains("encode", ignoreCase = true) ||
                def.key.contains("uac", ignoreCase = true) ||
                def.key.contains("input", ignoreCase = true) ||
                def.key.contains("spdif", ignoreCase = true) ||
                def.key.contains("hid", ignoreCase = true) ||
                def.key.contains("multiply", ignoreCase = true) ||
                def.key.contains("low_latency", ignoreCase = true) -> ControlCategory.BLUETOOTH

            def.key.contains("bright", ignoreCase = true) ||
                def.key.contains("screen", ignoreCase = true) ||
                def.key.contains("rotat", ignoreCase = true) ||
                def.key.contains("light", ignoreCase = true) ||
                def.key.contains("led", ignoreCase = true) ||
                def.key.contains("rgb", ignoreCase = true) ||
                def.key.contains("indicaot", ignoreCase = true) ||
                def.key.contains("indicator", ignoreCase = true) ||
                def.key.contains("theme", ignoreCase = true) -> ControlCategory.DISPLAY

            def.key.contains("btn", ignoreCase = true) ||
                def.key.contains("button", ignoreCase = true) ||
                def.key.contains("click", ignoreCase = true) ||
                def.key.contains("knob", ignoreCase = true) ||
                def.key.contains("wake", ignoreCase = true) ||
                def.key.contains("lock", ignoreCase = true) ||
                def.key.contains("wire_control", ignoreCase = true) ||
                def.key.contains("menu_operation", ignoreCase = true) -> ControlCategory.BUTTONS

            def.key.contains("power", ignoreCase = true) ||
                def.key.contains("charge", ignoreCase = true) ||
                def.key.contains("battery", ignoreCase = true) ||
                def.key.contains("car_mode", ignoreCase = true) ||
                def.key.contains("shut", ignoreCase = true) ||
                def.key.contains("standby", ignoreCase = true) -> ControlCategory.POWER

            else -> ControlCategory.GENERAL
        }

        val options = when {
            def.options.isNotEmpty() -> def.options
            def.bitMaskOptions.isNotEmpty() -> def.bitMaskOptions
            else -> emptyList()
        }

        val minVal = def.min ?: 0
        val maxVal = def.max ?: if (options.isNotEmpty()) options.maxOf { it.id } else 100
        val stepVal = (def.step ?: 1).coerceAtLeast(1)

        return DacControlDescriptor(
            key = def.key,
            label = def.label.ifBlank { def.key.replace('_', ' ') },
            category = category,
            kind = kind,
            min = minVal,
            max = maxVal,
            step = stepVal,
            unit = def.unit,
            startText = def.startText,
            options = options,
            sliders = def.sliders.map { DacSliderField(it.min, it.max, it.step, it.label) },
            isReadable = def.getCmd > 0,
            isWritable = def.setCmd > 0
        )
    }

    fun isPrimaryControl(key: String): Boolean {
        val k = key.lowercase()
        return k in PRIMARY_KEYS
    }

    fun buildExtraControls(schema: FiioDeviceSchema): List<DacControlDescriptor> =
        schema.controls
            .filter { it.key.isNotBlank() && !isPrimaryControl(it.key) && (it.getCmd > 0 || it.setCmd > 0) }
            .map { toDescriptor(it) }

    private val PRIMARY_KEYS = setOf(
        "vol_output",
        "device_volume",
        "device_volume_nui",
        "filter",
        "utws17_filter",
        "gain",
        "gain_level",
        "boost_bal_out_mode",
        "dac_work_mode",
        "output_estimation",
        "channel_balance",
        "uac_version",
        "uac_version_nui",
        "uac",
        "uac_1_0",
        "mic_detect",
        "microphone_switch",
        "mic_vol",
        "microphone_volume",
        "mic_monitor",
        "mic_monitor_volume",
        "microphone_listen_title",
        "led_light",
        "indicator_light_switch",
        "indicator_light_brightness",
        "led_title",
        "indicaot"
    )

    fun decodeResponse(def: FiioControlDef, raw: ByteArray, isUsb: Boolean = false): DacControlValue {
        if (raw.isEmpty()) return DacControlValue()
        val rawHex = raw.toHex()
        val ui = def.uiType ?: return DacControlValue(rawHex = rawHex)
        val ruler = def.parseRulerType
        return when (ui) {
            0, 2 -> {
                val b = raw[0].toInt() and 0xFF
                val on = when (ruler) {
                    1 -> b != 0x10 && b != 0x0B
                    2, 4 -> b == 0
                    3 -> b == 5
                    else -> b == 1
                }
                DacControlValue(intValue = if (on) 1 else 0, boolValue = on, rawHex = rawHex)
            }
            3, 10 -> {
                // FiiO SliderPayload & AudioSliderPayload always read bytes[0] as unsigned 8-bit value
                val rawByte = raw[0].toInt() and 0xFF
                val minVal = def.min ?: 0
                val step = (def.step ?: 1).coerceAtLeast(1)
                val decoded = if (ruler == 1) minVal + rawByte * step else rawByte
                val sec = if (raw.size >= 2) (raw[1].toInt() and 0xFF) else 0
                DacControlValue(
                    intValue = decoded,
                    secondaryValue = sec,
                    boolValue = decoded != 0,
                    rawHex = rawHex
                )
            }
            6 -> {
                val bal = decodeChannelBalance(raw, ruler, isUsb)
                DacControlValue(intValue = bal, boolValue = bal != 0, rawHex = rawHex)
            }
            1, 13 -> {
                val v = when (ruler) {
                    1, 3, 4 -> (if (raw.size >= 2) raw[1] else raw[0]).toInt() and 0xFF
                    5 -> raw[0].toInt() and 0xFF
                    else -> raw[0].toInt() and 0xFF
                }
                val sec = raw[0].toInt() and 0xFF
                DacControlValue(intValue = v, secondaryValue = sec, boolValue = v != 0, rawHex = rawHex)
            }
            8, 15 -> {
                if (def.bitMaskOptions.isNotEmpty() && ruler == 5 && raw.size > 1) {
                    // BR13 active codec priority list: raw[0] is top-priority codec
                    val active = raw[0].toInt() and 0xFF
                    DacControlValue(intValue = active, boolValue = active != 0, rawHex = rawHex)
                } else {
                    val rawVal = when (ruler) {
                        1, 3, 4 -> (if (raw.size >= 2) raw[1] else raw[0]).toInt() and 0xFF
                        else -> parseUint(raw)
                    }
                    DacControlValue(
                        intValue = rawVal,
                        secondaryValue = raw[0].toInt() and 0xFF,
                        boolValue = rawVal != 0,
                        rawHex = rawHex
                    )
                }
            }
            11 -> {
                val en = (raw[0].toInt() and 0xFF) != 0
                val s1 = if (raw.size >= 2) raw[1].toInt() and 0xFF else 0
                val s2 = if (raw.size >= 3) raw[2].toInt() and 0xFF else 0
                DacControlValue(intValue = s1, secondaryValue = s2, boolValue = en, rawHex = rawHex)
            }
            else -> DacControlValue(intValue = parseUint(raw), boolValue = parseUint(raw) != 0, rawHex = rawHex)
        }
    }

    fun encodePayload(
        def: FiioControlDef,
        value: DacControlValue,
        presetSlotId: Int = 0,
        isUsb: Boolean = false
    ): ByteArray {
        val ruler = def.parseRulerType
        val prevBytes = parseHex(value.rawHex)
        return when (def.uiType) {
            0, 2 -> {
                when (ruler) {
                    1 -> byteArrayOf(if (value.boolValue) 0x00 else 0x10)
                    3 -> reorderBr13CodecList(prevBytes, value.boolValue)
                    4 -> byteArrayOf(if (value.boolValue) 0 else 1)
                    else -> byteArrayOf(if (value.boolValue) 1 else 0)
                }
            }
            3, 10 -> {
                val minVal = def.min ?: 0
                val maxVal = def.max ?: 100
                val step = (def.step ?: 1).coerceAtLeast(1)
                val clamped = value.intValue.coerceIn(minVal, maxVal)
                val rawByte = when (ruler) {
                    1 -> ((clamped - minVal).toDouble() / step.toDouble()).roundToInt().coerceIn(0, 255)
                    else -> clamped.coerceIn(0, 255)
                }
                when (ruler) {
                    2 -> {
                        val b1 = prevBytes.getOrNull(1) ?: (value.secondaryValue and 0xFF).toByte()
                        byteArrayOf((rawByte and 0xFF).toByte(), b1)
                    }
                    3 -> byteArrayOf((presetSlotId and 0xFF).toByte(), (rawByte and 0xFF).toByte())
                    else -> byteArrayOf((rawByte and 0xFF).toByte())
                }
            }
            6 -> encodeChannelBalance(value.intValue, def.max ?: 12, ruler, isUsb)
            1, 13 -> encodeChoice(value.intValue, ruler, prevBytes)
            8, 15 -> {
                if (def.bitMaskOptions.isNotEmpty()) {
                    when (ruler) {
                        5 -> {
                            // BR13 codec priority reorder: selected codec moves to front
                            val chosen = value.intValue and 0xFF
                            val list = prevBytes.map { it.toInt() and 0xFF }
                                .filter { it != 0 && it != chosen }
                            (listOf(chosen) + list + listOf(0)).map { it.toByte() }.toByteArray()
                        }
                        1 -> byteArrayOf(prevBytes.getOrNull(0) ?: 0, (value.intValue and 0xFF).toByte())
                        3 -> byteArrayOf(0x02, (value.intValue and 0xFF).toByte())
                        4 -> byteArrayOf(0x04, (value.intValue and 0xFF).toByte())
                        else -> {
                            val bytesNeeded = ((def.bitMaskOptions.maxOfOrNull { it.id } ?: 1)
                                .let { highest -> if (highest > 255) 2 else 1 })
                            if (bytesNeeded == 2) {
                                byteArrayOf(((value.intValue ushr 8) and 0xFF).toByte(), (value.intValue and 0xFF).toByte())
                            } else {
                                byteArrayOf((value.intValue and 0xFF).toByte())
                            }
                        }
                    }
                } else {
                    encodeChoice(value.intValue, ruler, prevBytes)
                }
            }
            11 -> byteArrayOf(
                if (value.boolValue) 1 else 0,
                (value.intValue and 0xFF).toByte(),
                (value.secondaryValue and 0xFF).toByte()
            )
            else -> byteArrayOf((value.intValue and 0xFF).toByte())
        }
    }

    private fun encodeChoice(valInt: Int, ruler: Int, prevBytes: ByteArray): ByteArray {
        val v = valInt and 0xFF
        return when (ruler) {
            1 -> byteArrayOf(prevBytes.getOrNull(0) ?: 0, v.toByte())
            3 -> byteArrayOf(0x02, v.toByte())
            4 -> byteArrayOf(0x04, v.toByte())
            5 -> byteArrayOf(((v + 1) and 0xFF).toByte())
            else -> byteArrayOf(v.toByte())
        }
    }

    private fun reorderBr13CodecList(currentRaw: ByteArray, lowLatencyOn: Boolean): ByteArray {
        val list = currentRaw.map { it.toInt() and 0xFF }.filter { it != 0 }
        if (list.isEmpty()) return byteArrayOf(if (lowLatencyOn) 5 else 2, 0)
        val reordered = if (lowLatencyOn) {
            listOf(5) + list.filter { it != 5 }
        } else {
            val rest = list.filter { it != 5 }
            if (rest.isNotEmpty()) rest + listOf(5) else listOf(2)
        }
        return (reordered + listOf(0)).map { it.toByte() }.toByteArray()
    }

    /**
     * Exact parity with `BalancePayload` rulers 0..6 across USB (`sideFlag=true`) and BT (`sideFlag=false`).
     */
    fun decodeChannelBalance(raw: ByteArray, parseRulerType: Int = 0, isUsb: Boolean = false): Int {
        if (raw.isEmpty()) return 0
        return when (parseRulerType) {
            1 -> {
                // UTWS3 1-byte: upper nibble = side (1=Left, 2=Right), lower nibble = mag
                val b = raw[0].toInt() and 0xFF
                val side = (b ushr 4) and 0x0F
                val mag = b and 0x0F
                when (side) {
                    1 -> -mag
                    2 -> mag
                    else -> 0
                }
            }
            2 -> {
                // BTR3K, LC-BT2 v1.0: [1=L/else=R, mag]
                if (raw.size < 2) return 0
                val side = raw[0].toInt() and 0xFF
                val mag = raw[1].toInt() and 0xFF
                if (side == 1) -mag else mag
            }
            3 -> {
                // BR13 signed attenuate: [left_s8, right_s8]
                if (raw.size < 2) return 0
                val b0 = raw[0].toInt().toByte().toInt()
                val b1 = raw[1].toInt().toByte().toInt()
                if (b0 != 0) b0 else -b1
            }
            4 -> {
                // BTR5, K9*, Q7, FW3, FW5, SA1: [1=L/2=R, mag]
                if (raw.size < 2) return 0
                val b0 = raw[0].toInt() and 0xFF
                val b1 = raw[1].toInt() and 0xFF
                when (b0) {
                    1 -> -b1
                    2 -> b1
                    else -> 0
                }
            }
            5 -> {
                // KA17 (USB), BTR3 (BT): negated rule 0
                -decodeRule0(raw, isUsb)
            }
            6 -> {
                // Q5s, Q5s-TC: center [1, 0xFF] or [1, 0]
                if (raw.size < 2) return 0
                val b0 = raw[0].toInt() and 0xFF
                val b1 = raw[1].toInt() and 0xFF
                if (b1 == 0xFF || b1 == 0) 0 else when (b0) {
                    1 -> -b1
                    2 -> b1
                    else -> 0
                }
            }
            else -> decodeRule0(raw, isUsb)
        }
    }

    /**
     * Exact parity with `BalancePayload` rulers 0..6 across USB (`isUSBProtocol=true`) and BT (`isUSBProtocol=false`).
     * Note: Dart AOT stores `true=0x20` (bit 4=0) and `false=0x30` (bit 4=1), so `tbnz w2, #4` selects BT:
     * - USB (`isUsb = true`): `[rightLevel, leftLevel]` (negated by ruler 5 on KA17 to `[leftLevel, rightLevel]`)
     * - BT (`isUsb = false`): `[side (0=Left, 1=Right), mag]`
     */
    private fun decodeRule0(raw: ByteArray, isUsb: Boolean): Int {
        if (raw.size < 2) return 0
        val b0 = raw[0].toInt() and 0xFF
        val b1 = raw[1].toInt() and 0xFF
        return if (isUsb) {
            if (b0 > b1) b0 else -b1
        } else {
            if (b1 == 0) 0 else if (b0 == 0) -b1 else b1
        }
    }

    fun encodeChannelBalance(
        balance: Int,
        maxAbs: Int,
        parseRulerType: Int = 0,
        isUsb: Boolean = false
    ): ByteArray {
        val v = balance.coerceIn(-maxAbs, maxAbs)
        return when (parseRulerType) {
            1 -> {
                val side = when {
                    v < 0 -> 1
                    v > 0 -> 2
                    else -> 0
                }
                val mag = kotlin.math.abs(v) and 0x0F
                byteArrayOf(((side shl 4) or mag).toByte())
            }
            2 -> {
                if (v < 0) byteArrayOf(1, (-v).toByte())
                else byteArrayOf(0, v.toByte())
            }
            3 -> {
                if (v < 0) byteArrayOf((v and 0xFF).toByte(), 0)
                else byteArrayOf(0, ((-v) and 0xFF).toByte())
            }
            4 -> {
                when {
                    v < 0 -> byteArrayOf(1, (-v).toByte())
                    v > 0 -> byteArrayOf(2, v.toByte())
                    else -> byteArrayOf(1, 0)
                }
            }
            5 -> encodeRule0(-v, isUsb)
            6 -> {
                when {
                    v < 0 -> byteArrayOf(1, (-v).toByte())
                    v > 0 -> byteArrayOf(2, v.toByte())
                    else -> byteArrayOf(1, 0xFF.toByte())
                }
            }
            else -> encodeRule0(v, isUsb)
        }
    }

    private fun encodeRule0(v: Int, isUsb: Boolean): ByteArray {
        return if (isUsb) {
            if (v < 0) byteArrayOf(0, (-v).toByte())
            else byteArrayOf(v.toByte(), 0)
        } else {
            when {
                v < 0 -> byteArrayOf(0, (-v).toByte())
                v > 0 -> byteArrayOf(1, v.toByte())
                else -> byteArrayOf(1, 0)
            }
        }
    }

    /** Backwards-compatible overload for callers not yet updated to pass `parseRulerType`. */
    fun decodeChannelBalance(raw: ByteArray, isBtSideMagFormat: Boolean): Int =
        decodeChannelBalance(raw, parseRulerType = 0, isUsb = !isBtSideMagFormat)

    /** Backwards-compatible overload for callers not yet updated to pass `parseRulerType`. */
    fun encodeChannelBalance(balance: Int, maxAbs: Int, isBtSideMagFormat: Boolean): ByteArray =
        encodeChannelBalance(balance, maxAbs, parseRulerType = 0, isUsb = !isBtSideMagFormat)

    fun parseUint(raw: ByteArray): Int {
        if (raw.isEmpty()) return 0
        return when (raw.size) {
            1 -> raw[0].toInt() and 0xFF
            2 -> ((raw[0].toInt() and 0xFF) shl 8) or (raw[1].toInt() and 0xFF)
            else -> {
                var acc = 0
                val n = minOf(raw.size, 4)
                for (i in 0 until n) {
                    acc = (acc shl 8) or (raw[i].toInt() and 0xFF)
                }
                acc
            }
        }
    }

    fun decodeDeviceName(chunks: List<ByteArray>, ruler: Int): String {
        if (chunks.isEmpty()) return ""
        val combined = if (chunks.size == 1) chunks[0] else {
            val out = ArrayList<Byte>()
            chunks.forEach { chunk -> chunk.forEach { out.add(it) } }
            out.toByteArray()
        }
        return decodeDeviceName(combined, ruler)
    }

    fun decodeDeviceName(raw: ByteArray, ruler: Int): String {
        if (raw.isEmpty()) return ""
        if (ruler == 3) {
            return raw.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8).trim()
        }
        val unwrapped = if (raw.size >= 3 && (raw[0].toInt() and 0xFF) == 0xFF && (raw.last().toInt() and 0xFF) == 0xFF) {
            val declaredLen = raw[1].toInt() and 0xFF
            val avail = raw.size - 3
            val actualLen = if (declaredLen in 1..avail) declaredLen else avail
            raw.copyOfRange(2, 2 + actualLen)
        } else {
            raw.takeWhile { it != 0.toByte() }.toByteArray()
        }
        return unwrapped.toString(Charsets.UTF_8).trim()
    }

    /**
     * Exact parity with `ScDeviceNamePayloadBuilder::buildSetPayloads`:
     * - ruler 3 (`BR13`): raw UTF-8
     * - ruler 0 & ruler 1: framed `[0xFF, len, ...utf8, 0xFF]`, chunked at 10 bytes when `ruler != 0 && framed.size > 10`
     */
    fun encodeDeviceNameChunks(name: String, maxBytes: Int, ruler: Int): List<ByteArray> {
        val raw = name.trim().toByteArray(Charsets.UTF_8)
        val trimmed = if (raw.size > maxBytes) raw.copyOf(maxBytes) else raw
        if (trimmed.isEmpty()) return emptyList()
        if (ruler == 3) return listOf(trimmed)

        val framed = ByteArray(trimmed.size + 3)
        framed[0] = 0xFF.toByte()
        framed[1] = (trimmed.size and 0xFF).toByte()
        trimmed.copyInto(framed, destinationOffset = 2)
        framed[framed.lastIndex] = 0xFF.toByte()

        if (ruler == 0 || framed.size <= 10) return listOf(framed)
        val chunks = ArrayList<ByteArray>()
        var offset = 0
        while (offset < framed.size) {
            val end = minOf(offset + 10, framed.size)
            chunks += framed.copyOfRange(offset, end)
            offset = end
        }
        return chunks
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

    private fun parseHex(hex: String): ByteArray {
        val clean = hex.trim()
        if (clean.length < 2 || clean.length % 2 != 0) return byteArrayOf()
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
