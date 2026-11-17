package com.suprcharg3d.dongleCTRL.protocol

data class ParsedHidDescriptor(
    val usagePages: Set<Int> = emptySet(),
    val inputPayloadBytes: Map<Int, Int> = emptyMap(),
    val outputPayloadBytes: Map<Int, Int> = emptyMap(),
    val featurePayloadBytes: Map<Int, Int> = emptyMap()
) {
    val hasVendorUsagePage: Boolean
        get() = usagePages.any { it in 0xFF00..0xFFFF }

    val maxReportBytes: Int
        get() = maxOf(
            inputPayloadBytes.values.maxOrNull() ?: 0,
            outputPayloadBytes.values.maxOrNull() ?: 0,
            featurePayloadBytes.values.maxOrNull() ?: 0
        )

    /**
     * Consumer/media button-only interfaces (0x0C, 0x0B, 0x09, 0x08) with tiny < 8B reports and no vendor page
     * must be skipped when a DAC exposes multiple HID interfaces (e.g. JA11, BTR17, KA15).
     */
    val isWiredRemoteOnly: Boolean
        get() = !hasVendorUsagePage && maxReportBytes in 1..7

    fun wireOutputSizes(): Map<Int, Int> =
        outputPayloadBytes.mapValues { (rid, payloadLen) -> if (rid > 0) payloadLen + 1 else payloadLen }

    fun wireInputSizes(): Map<Int, Int> =
        inputPayloadBytes.mapValues { (rid, payloadLen) -> if (rid > 0) payloadLen + 1 else payloadLen }
}

/**
 * Pure-Kotlin USB HID Report Descriptor parser matching FiiO FlutterUsbHidLib (`me.a` / `ne.a`)
 * and WebHID output report sizing.
 */
object HidReportDescriptorParser {

    fun parse(raw: ByteArray): ParsedHidDescriptor {
        if (raw.isEmpty()) return ParsedHidDescriptor()
        val usagePages = LinkedHashSet<Int>()
        val inBits = LinkedHashMap<Int, Int>()
        val outBits = LinkedHashMap<Int, Int>()
        val featBits = LinkedHashMap<Int, Int>()

        var reportSize = 0
        var reportCount = 0
        var reportId = 0
        val stack = ArrayDeque<Triple<Int, Int, Int>>()

        var i = 0
        while (i < raw.size) {
            val prefix = raw[i].toInt() and 0xFF
            if (prefix == 0xFE) {
                // Long item
                if (i + 1 >= raw.size) break
                val dLen = raw[i + 1].toInt() and 0xFF
                i += 3 + dLen
                continue
            }
            val sizeCode = prefix and 0x03
            val dSize = if (sizeCode == 3) 4 else sizeCode
            val itemType = (prefix ushr 2) and 0x03 // 0=Main, 1=Global, 2=Local
            val tag = (prefix ushr 4) and 0x0F

            if (i + 1 + dSize > raw.size) break
            var dataVal = 0
            for (b in 0 until dSize) {
                dataVal = dataVal or ((raw[i + 1 + b].toInt() and 0xFF) shl (8 * b))
            }

            when (itemType) {
                0 -> when (tag) { // Main
                    0x08 -> { // Input
                        val total = reportSize * reportCount
                        if (total > 0) inBits[reportId] = (inBits[reportId] ?: 0) + total
                    }
                    0x09 -> { // Output
                        val total = reportSize * reportCount
                        if (total > 0) outBits[reportId] = (outBits[reportId] ?: 0) + total
                    }
                    0x0B -> { // Feature
                        val total = reportSize * reportCount
                        if (total > 0) featBits[reportId] = (featBits[reportId] ?: 0) + total
                    }
                }
                1 -> when (tag) { // Global
                    0x00 -> usagePages += dataVal
                    0x07 -> reportSize = dataVal
                    0x08 -> reportId = dataVal
                    0x09 -> reportCount = dataVal
                    0x0A -> stack.addLast(Triple(reportSize, reportCount, reportId))
                    0x0B -> if (stack.isNotEmpty()) {
                        val (rs, rc, rid) = stack.removeLast()
                        reportSize = rs
                        reportCount = rc
                        reportId = rid
                    }
                }
            }
            i += 1 + dSize
        }

        return ParsedHidDescriptor(
            usagePages = usagePages,
            inputPayloadBytes = inBits.mapValues { (_, bits) -> (bits + 7) / 8 },
            outputPayloadBytes = outBits.mapValues { (_, bits) -> (bits + 7) / 8 },
            featurePayloadBytes = featBits.mapValues { (_, bits) -> (bits + 7) / 8 }
        )
    }
}
