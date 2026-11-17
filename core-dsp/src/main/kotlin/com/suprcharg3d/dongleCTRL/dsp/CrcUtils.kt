package com.suprcharg3d.dongleCTRL.dsp

object CrcUtils {

    /**
     * CRC-8/MAXIM (polynomial 0x31, reflected 0x8C, init 0x00) used by FiiO 0xAA/0xBB frames.
     */
    fun crc8Maxim(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = 0
        val end = (offset + length).coerceAtMost(data.size)
        for (i in offset until end) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 1) != 0) (crc ushr 1) xor 0x8C else crc ushr 1
            }
        }
        return crc and 0xFF
    }

    /**
     * CRC-16/CCITT-FALSE (polynomial 0x1021, init 0xFFFF) used by CB1100AU "EQ1#" payloads.
     */
    fun crc16Ccitt(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = 0xFFFF
        val end = (offset + length).coerceAtMost(data.size)
        for (i in offset until end) {
            val byte = data[i].toInt() and 0xFF
            crc = crc xor (byte shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) ((crc shl 1) xor 0x1021) and 0xFFFF else (crc shl 1) and 0xFFFF
            }
        }
        return crc and 0xFFFF
    }

    /**
     * CRC-16/MODBUS (polynomial 0xA001 reflected, init 0xFFFF) used by KT1213/MT2000 0x57 packets.
     */
    fun crc16Ibm(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = 0xFFFF
        val end = (offset + length).coerceAtMost(data.size)
        for (i in offset until end) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 1) != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
            }
        }
        return crc and 0xFFFF
    }

    fun checksumSum8(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var sum = 0
        val end = (offset + length).coerceAtMost(data.size)
        for (i in offset until end) {
            sum += data[i].toInt() and 0xFF
        }
        return sum and 0xFF
    }
}
