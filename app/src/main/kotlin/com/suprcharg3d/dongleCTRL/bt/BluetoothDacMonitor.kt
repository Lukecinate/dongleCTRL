package com.suprcharg3d.dongleCTRL.bt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.suprcharg3d.dongleCTRL.protocol.DacCatalog
import com.suprcharg3d.dongleCTRL.protocol.DacProfile
import com.suprcharg3d.dongleCTRL.protocol.DacTransport
import com.suprcharg3d.dongleCTRL.protocol.TransportType
import com.suprcharg3d.dongleCTRL.protocol.UsbDeviceDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DetectedBluetoothDac(
    val device: BluetoothDevice,
    val address: String,
    val displayName: String,
    val isBonded: Boolean,
    val isAudioConnected: Boolean,
    val descriptor: UsbDeviceDescriptor,
    val matchedProfile: DacProfile
)

/**
 * Discovers bonded & nearby Bluetooth DACs/amps (BTR17, BTR15, BTR13, BTR7, BTR5, BTR3, RETRO NANO, K13 R2R, etc.)
 * and opens either Classic SPP RFCOMM or BLE GATT transport matching the device's schema.
 */
@SuppressLint("MissingPermission")
class BluetoothDacMonitor(
    private val context: Context,
    private val catalog: DacCatalog,
    private val logSink: (String) -> Unit
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val _detectedDevices = MutableStateFlow<List<DetectedBluetoothDac>>(emptyList())
    val detectedDevices: StateFlow<List<DetectedBluetoothDac>> = _detectedDevices.asStateFlow()

    private val _hasPermission = MutableStateFlow(checkConnectPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val discoveredLe = LinkedHashMap<String, BluetoothDevice>()
    private val handler = Handler(Looper.getMainLooper())
    private var scanStopRunnable: Runnable? = null

    private val leCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val name = result.scanRecord?.deviceName ?: dev.name ?: return
            val addr = dev.address ?: return
            if (catalog.matchBluetoothDevice(name, addr) != null) {
                if (!discoveredLe.containsKey(addr)) {
                    discoveredLe[addr] = dev
                    refreshBondedAndDiscovered()
                }
            }
        }
    }

    fun hasRequiredPermissions(): Boolean = checkConnectPermission()

    fun refreshBondedAndDiscovered() {
        val granted = checkConnectPermission()
        _hasPermission.value = granted
        val bt = adapter
        if (!granted || bt == null || !bt.isEnabled) {
            _detectedDevices.value = emptyList()
            return
        }

        val combined = LinkedHashMap<String, BluetoothDevice>()
        runCatching {
            bt.bondedDevices?.forEach { dev ->
                val addr = dev.address ?: return@forEach
                combined[addr] = dev
            }
        }
        discoveredLe.forEach { (addr, dev) ->
            combined.putIfAbsent(addr, dev)
        }

        val list = combined.values.mapNotNull { dev ->
            val name = runCatching { dev.name }.getOrNull() ?: return@mapNotNull null
            val addr = dev.address ?: return@mapNotNull null
            val profile = catalog.matchBluetoothDevice(name, addr) ?: return@mapNotNull null
            val desc = UsbDeviceDescriptor(
                deviceKey = "bt_$addr",
                vendorId = profile.vendorId,
                productId = profile.productId ?: 0,
                productName = name,
                manufacturerName = profile.brand,
                hasHidInterface = false,
                hasCdcInterface = false,
                bluetoothAddress = addr
            )
            DetectedBluetoothDac(
                device = dev,
                address = addr,
                displayName = name,
                isBonded = runCatching { dev.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false),
                isAudioConnected = isDeviceConnected(dev),
                descriptor = desc,
                matchedProfile = profile
            )
        }.sortedWith(
            compareByDescending<DetectedBluetoothDac> { it.isAudioConnected }
                .thenByDescending { it.isBonded }
                .thenBy { it.displayName }
        )

        _detectedDevices.value = list
    }

    fun startLeScan(durationMs: Long = 8_000L) {
        if (!checkScanPermission() || !checkConnectPermission()) {
            refreshBondedAndDiscovered()
            return
        }
        val bt = adapter ?: return
        if (!bt.isEnabled) return
        val scanner = bt.bluetoothLeScanner ?: run {
            refreshBondedAndDiscovered()
            return
        }

        refreshBondedAndDiscovered()
        if (_isScanning.value) return
        runCatching {
            scanner.startScan(leCallback)
            _isScanning.value = true
            logSink("Started Bluetooth LE DAC scan...")
            scanStopRunnable?.let { handler.removeCallbacks(it) }
            val r = Runnable { stopLeScan() }
            scanStopRunnable = r
            handler.postDelayed(r, durationMs)
        }.onFailure {
            logSink("BLE scan unavailable: ${it.message}")
            _isScanning.value = false
        }
    }

    fun stopLeScan() {
        scanStopRunnable?.let { handler.removeCallbacks(it) }
        scanStopRunnable = null
        if (!_isScanning.value) return
        _isScanning.value = false
        if (checkScanPermission()) {
            runCatching { adapter?.bluetoothLeScanner?.stopScan(leCallback) }
        }
    }

    suspend fun openTransport(entry: DetectedBluetoothDac): DacTransport? {
        val bt = adapter ?: return null
        if (!checkConnectPermission()) return null

        return if (entry.matchedProfile.transport == TransportType.BLUETOOTH_GATT) {
            AndroidBtGattTransport.open(context, entry.device, entry.descriptor, logSink)
        } else {
            // Dual / Classic BT: try SPP RFCOMM first, then BLE GATT fallback if SPP fails
            AndroidBtSppTransport.open(bt, entry.device, entry.descriptor, logSink)
                ?: AndroidBtGattTransport.open(context, entry.device, entry.descriptor, logSink)
        }
    }

    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return runCatching {
            val method = device.javaClass.getMethod("isConnected")
            (method.invoke(device) as? Boolean) ?: false
        }.getOrDefault(false)
    }

    private fun checkConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
