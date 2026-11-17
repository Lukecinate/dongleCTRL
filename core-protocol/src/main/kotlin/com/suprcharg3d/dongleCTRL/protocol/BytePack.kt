package com.suprcharg3d.dongleCTRL.protocol

internal object BytePack {

    fun u16Le(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    fun u16Be(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 1] = (value and 0xFF).toByte()
    }

    fun i32Le(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    fun i24Le(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 16) and 0xFF).toByte()
    }

    fun getU16Le(buf: ByteArray, offset: Int): Int {
        if (offset + 1 >= buf.size) return 0
        return (buf[offset].toInt() and 0xFF) or ((buf[offset + 1].toInt() and 0xFF) shl 8)
    }

    fun getI16Le(buf: ByteArray, offset: Int): Int {
        val raw = getU16Le(buf, offset)
        return if (raw and 0x8000 != 0) raw or -0x10000 else raw
    }

    fun getU16Be(buf: ByteArray, offset: Int): Int {
        if (offset + 1 >= buf.size) return 0
        return ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)
    }

    fun getI16Be(buf: ByteArray, offset: Int): Int {
        val raw = getU16Be(buf, offset)
        return if (raw and 0x8000 != 0) raw or -0x10000 else raw
    }

    fun getU32Le(buf: ByteArray, offset: Int): Long {
        if (offset + 3 >= buf.size) return 0L
        return (buf[offset].toLong() and 0xFF) or
            ((buf[offset + 1].toLong() and 0xFF) shl 8) or
            ((buf[offset + 2].toLong() and 0xFF) shl 16) or
            ((buf[offset + 3].toLong() and 0xFF) shl 24)
    }

    fun fixed64(vararg prefix: Int): ByteArray {
        val out = ByteArray(64)
        for (i in prefix.indices) {
            if (i >= out.size) break
            out[i] = (prefix[i] and 0xFF).toByte()
        }
        return out
    }
}
