package com.suprcharg3d.dongleCTRL.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.suprcharg3d.dongleCTRL.protocol.DacTransport
import com.suprcharg3d.dongleCTRL.protocol.TransportType
import com.suprcharg3d.dongleCTRL.protocol.UsbDeviceDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Minimal CDC-ACM USB Serial transport (57600 baud 8N1) for desktop FiiO K15 / K17 / K19 DACs over USB OTG.
 */
class AndroidUsbSerialTransport private constructor(
    override val descriptor: UsbDeviceDescriptor,
    private val connection: UsbDeviceConnection,
    private val controlInterface: UsbInterface?,
    private val dataInterface: UsbInterface,
    private val inEndpoint: UsbEndpoint?,
    private val outEndpoint: UsbEndpoint?,
    private val logSink: (String) -> Unit
) : DacTransport {

    override val transportType: TransportType = TransportType.USB_SERIAL
    private val ioMutex = Mutex()

    @Volatile
    private var open = true

    override val isConnected: Boolean
        get() = open

    override suspend fun send(data: ByteArray, timeoutMs: Int): Boolean = ioMutex.withLock {
        if (!open) return false
        val out = outEndpoint ?: return false
        withContext(Dispatchers.IO) {
            val sent = connection.bulkTransfer(out, data, data.size, timeoutMs)
            logSink("SER TX[${data.size}] -> $sent")
            sent >= 0
        }
    }

    override suspend fun receive(expectedSize: Int, timeoutMs: Int): ByteArray? = ioMutex.withLock {
        if (!open) return null
        val ep = inEndpoint ?: return null
        withContext(Dispatchers.IO) {
            val buf = ByteArray(expectedSize.coerceAtLeast(128))
            val read = connection.bulkTransfer(ep, buf, buf.size, timeoutMs)
            if (read > 0) buf.copyOf(read) else null
        }
    }

    override fun close() {
        if (!open) return
        open = false
        runCatching { connection.releaseInterface(dataInterface) }
        controlInterface?.let { runCatching { connection.releaseInterface(it) } }
        runCatching { connection.close() }
    }

    companion object {
        fun open(
            usbManager: UsbManager,
            device: UsbDevice,
            descriptor: UsbDeviceDescriptor,
            baudRate: Int = 57_600,
            logSink: (String) -> Unit
        ): AndroidUsbSerialTransport? {
            var ctrlIntf: UsbInterface? = null
            var dataIntf: UsbInterface? = null
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                when (intf.interfaceClass) {
                    UsbConstants.USB_CLASS_COMM -> ctrlIntf = intf
                    UsbConstants.USB_CLASS_CDC_DATA -> dataIntf = intf
                }
            }
            val targetData = dataIntf ?: return null
            val conn = usbManager.openDevice(device) ?: return null
            ctrlIntf?.let { conn.claimInterface(it, true) }
            if (!conn.claimInterface(targetData, true)) {
                conn.close()
                return null
            }

            var inEp: UsbEndpoint? = null
            var outEp: UsbEndpoint? = null
            for (i in 0 until targetData.endpointCount) {
                val ep = targetData.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep
                    if (ep.direction == UsbConstants.USB_DIR_OUT) outEp = ep
                }
            }

            // CDC SET_LINE_CODING (57600 8N1) + SET_CONTROL_LINE_STATE (DTR | RTS)
            val lineCoding = byteArrayOf(
                (baudRate and 0xFF).toByte(),
                ((baudRate ushr 8) and 0xFF).toByte(),
                ((baudRate ushr 16) and 0xFF).toByte(),
                ((baudRate ushr 24) and 0xFF).toByte(),
                0x00, 0x00, 0x08
            )
            val ctrlIdx = ctrlIntf?.id ?: 0
            conn.controlTransfer(0x21, 0x20, 0, ctrlIdx, lineCoding, lineCoding.size, 500)
            conn.controlTransfer(0x21, 0x22, 0x03, ctrlIdx, null, 0, 500)

            logSink("Opened CDC Serial ${baudRate}bps on ${device.deviceName}")
            return AndroidUsbSerialTransport(descriptor, conn, ctrlIntf, targetData, inEp, outEp, logSink)
        }
    }
}
