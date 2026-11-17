package com.suprcharg3d.dongleCTRL

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.suprcharg3d.dongleCTRL.protocol.DacTransport
import com.suprcharg3d.dongleCTRL.protocol.ProtocolRegistry
import com.suprcharg3d.dongleCTRL.protocol.TransportType
import com.suprcharg3d.dongleCTRL.protocol.UsbDeviceDescriptor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation smoke test for live Bluetooth communication when BTR17 is paired and powered on.
 * Skips cleanly on devices without a bonded FiiO BTR17.
 */
@RunWith(AndroidJUnit4::class)
class LiveBtr17BluetoothTest {

    @get:Rule
    val btPermissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GrantPermissionRule.grant(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        GrantPermissionRule.grant(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    }

    private class LoggingTransport(private val delegate: DacTransport) : DacTransport {
        override val descriptor: UsbDeviceDescriptor get() = delegate.descriptor
        override val transportType: TransportType get() = delegate.transportType
        override val isConnected: Boolean get() = delegate.isConnected

        private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

        override suspend fun send(data: ByteArray, timeoutMs: Int): Boolean {
            Log.d("dongleCTRL", "BT_TX: ${data.toHex()}")
            return delegate.send(data, timeoutMs)
        }

        override suspend fun receive(expectedSize: Int, timeoutMs: Int): ByteArray? {
            val rx = delegate.receive(expectedSize, timeoutMs)
            Log.d("dongleCTRL", "BT_RX: ${rx?.toHex() ?: "<null>"}")
            return rx
        }

        override suspend fun transceive(request: ByteArray, expectedSize: Int, timeoutMs: Int): ByteArray? {
            Log.d("dongleCTRL", "BT_TX: ${request.toHex()}")
            val rx = delegate.transceive(request, expectedSize, timeoutMs)
            Log.d("dongleCTRL", "BT_RX: ${rx?.toHex() ?: "<null>"}")
            return rx
        }

        override fun close() = delegate.close()
    }

    @Test
    fun readBtr17LiveState() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DongleCtrlApp
        // Ensure catalog and schemas are initialized before accessing btMonitor
        app.catalog
        assumeTrue("Bluetooth permission required", app.btMonitor.hasRequiredPermissions())

        app.btMonitor.refreshBondedAndDiscovered()
        val btr17 = app.btMonitor.detectedDevices.value.firstOrNull {
            it.matchedProfile.model.contains("BTR17", ignoreCase = true)
        }
        assumeTrue("Bonded BTR17 not present on device", btr17 != null)
        assumeTrue("BTR17 must be audio-connected for live check", btr17!!.isAudioConnected)

        Log.d("dongleCTRL", "Detected BTR17: name=${btr17.displayName}, addr=${btr17.address}, schema=${btr17.matchedProfile.fiioDeviceType}")

        val rawTransport = app.btMonitor.openTransport(btr17)
        assertNotNull("Failed to open BT transport to ${btr17.displayName}", rawTransport)
        val transport = LoggingTransport(rawTransport!!)

        try {
            val protocol = ProtocolRegistry.create(btr17.matchedProfile.protocol)
            val btProto = protocol as com.suprcharg3d.dongleCTRL.protocol.drivers.FiioBtProtocol

            val caps = protocol.buildCapabilities(btr17.matchedProfile)
            assertEquals("Linear Phase Fast Roll-off", caps.dacFilterOptions.first { it.id == 2 }.label)
            val state = protocol.readDeviceState(transport, btr17.matchedProfile)

            Log.d(
                "dongleCTRL",
                "BTR17 LIVE STATE: name=${state.deviceName}, vol=${state.volume}/${state.maxVolume}, eqEnabled=${state.eqEnabled}, " +
                    "presetId=${state.activePresetId}, filter=${state.dacFilterIndex}, " +
                    "bal=${state.channelBalance}, battery=${state.batteryPercent}, codec=${state.bluetoothCodec}, " +
                    "extraControls=${state.extraControlValues.size}, bands=${state.bands.size}, note=${state.diagnosticNote}"
            )
            state.extraControlValues.forEach { (k, v) ->
                Log.d("dongleCTRL", "BTR17 EXTRA[$k] = $v")
            }
            state.bands.forEachIndexed { idx, band ->
                Log.d("dongleCTRL", "BTR17 BAND $idx: $band")
            }

            assertTrue("Expected non-empty EQ bands on BTR17", state.bands.isNotEmpty())
            assertNotNull("Expected volume readback from BTR17", state.volume)
            assertTrue("Expected valid presetId", state.activePresetId >= 0)
            assertTrue("Expected populated BTR17 extra controls (got ${state.extraControlValues.size})", state.extraControlValues.size >= 15)
            assertTrue("Expected non-blank segmented BT deviceName", !state.deviceName.isNullOrBlank())
            assertTrue("Unexpected replacement char in deviceName=${state.deviceName}", !state.deviceName!!.contains('\uFFFD'))

            // Verify fast live telemetry refresh works cleanly without mutating state
            assertFalse("BTR17 EQ toggle should remain independent of Bypass preset", caps.eqToggleUsesBypass)

            // Verify fast + full live telemetry refresh works cleanly without mutating state
            val live = btProto.refreshLiveTelemetry(transport, btr17.matchedProfile, state, includeAllControls = true)
            assertNotNull("Expected non-null live telemetry refresh on BTR17", live)
            assertEquals(state.volume, live?.volume)
            assertEquals(state.extraControlValues["brightness"]?.intValue, live?.extraControlValues?.get("brightness")?.intValue)

            // Ensure EQ tests run on a writable User preset (160) if currently on Bypass (240)
            val testPresetId = if (state.activePresetId in caps.writablePresetIds) state.activePresetId else caps.writablePresetIds.first()
            if (testPresetId != state.activePresetId) {
                protocol.selectPreset(transport, btr17.matchedProfile, testPresetId)
                kotlinx.coroutines.delay(80)
            }
            val writableInitState = protocol.readDeviceState(transport, btr17.matchedProfile)
            val origBand0 = writableInitState.bands[0]
            val origBand1 = writableInitState.bands[1]
            val origBrightness = state.extraControlValues["brightness"]
            val origScreenOff = state.extraControlValues["auto_screen_off_time"]
            try {
                // 1. Verify writeEqBand + readback on Band 0
                assertTrue(protocol.writeEqBand(transport, btr17.matchedProfile, origBand0.copy(gainDb = 1.5), writableInitState.globalGainDb, testPresetId))
                kotlinx.coroutines.delay(100)
                val afterSingle = protocol.readDeviceState(transport, btr17.matchedProfile)
                assertEquals(1.5, afterSingle.bands[0].gainDb, 0.05)

                // 2. Verify writeAllEqBands + readback on Band 1 & restore Band 0
                val updatedBands = writableInitState.bands.mapIndexed { i, b ->
                    when (i) {
                        1 -> b.copy(gainDb = 1.0)
                        else -> b
                    }
                }
                assertTrue(protocol.writeAllEqBands(transport, btr17.matchedProfile, updatedBands, writableInitState.globalGainDb, testPresetId))
                kotlinx.coroutines.delay(120)
                val afterBatch = protocol.readDeviceState(transport, btr17.matchedProfile)
                assertEquals(origBand0.gainDb, afterBatch.bands[0].gainDb, 0.05)
                assertEquals(1.0, afterBatch.bands[1].gainDb, 0.05)

                // 3. Verify channel balance negative (-5) & positive (+5), DAC filter, brightness (4), auto screen off (15s)
                assertTrue(protocol.setChannelBalance(transport, btr17.matchedProfile, -5))
                kotlinx.coroutines.delay(80)
                assertEquals(-5, btProto.refreshLiveTelemetry(transport, btr17.matchedProfile, state, includeAllControls = true)?.channelBalance)

                assertTrue(protocol.setChannelBalance(transport, btr17.matchedProfile, 5))
                val testFilter = if (state.dacFilterIndex == 2) 1 else 2
                assertTrue(protocol.setDacFilter(transport, btr17.matchedProfile, testFilter))
                assertTrue(btProto.setExtraControl(transport, btr17.matchedProfile, "brightness", com.suprcharg3d.dongleCTRL.protocol.DacControlValue(intValue = 4)))
                assertTrue(btProto.setExtraControl(transport, btr17.matchedProfile, "auto_screen_off_time", com.suprcharg3d.dongleCTRL.protocol.DacControlValue(intValue = 15)))
                kotlinx.coroutines.delay(100)
                val afterCtrl = protocol.readDeviceState(transport, btr17.matchedProfile)
                assertEquals(5, afterCtrl.channelBalance)
                assertEquals(testFilter, afterCtrl.dacFilterIndex)
                assertEquals(4, afterCtrl.extraControlValues["brightness"]?.intValue)
                assertEquals(15, afterCtrl.extraControlValues["auto_screen_off_time"]?.intValue)

                // 4. Selecting Bypass (240) preserves eqEnabled == true when Hardware EQ switch is ON
                if (caps.bypassPresetId != null && state.eqEnabled) {
                    assertTrue(protocol.selectPreset(transport, btr17.matchedProfile, caps.bypassPresetId!!))
                    kotlinx.coroutines.delay(80)
                    val bypassState = protocol.readDeviceState(transport, btr17.matchedProfile)
                    assertEquals(caps.bypassPresetId, bypassState.activePresetId)
                    assertTrue("Selecting Bypass must keep Hardware EQ toggle enabled", bypassState.eqEnabled)
                }
            } finally {
                // Always restore exact initial BTR17 hardware state
                protocol.writeAllEqBands(transport, btr17.matchedProfile, writableInitState.bands, writableInitState.globalGainDb, testPresetId)
                if (testPresetId != state.activePresetId) {
                    protocol.selectPreset(transport, btr17.matchedProfile, state.activePresetId)
                }
                protocol.setChannelBalance(transport, btr17.matchedProfile, state.channelBalance)
                protocol.setDacFilter(transport, btr17.matchedProfile, state.dacFilterIndex)
                if (origBrightness != null) {
                    btProto.setExtraControl(transport, btr17.matchedProfile, "brightness", origBrightness)
                }
                if (origScreenOff != null) {
                    btProto.setExtraControl(transport, btr17.matchedProfile, "auto_screen_off_time", origScreenOff)
                }
                kotlinx.coroutines.delay(120)
                val restored = protocol.readDeviceState(transport, btr17.matchedProfile)
                Log.d("dongleCTRL", "AFTER RESTORE: preset=${restored.activePresetId}, eq=${restored.eqEnabled}, filter=${restored.dacFilterIndex}, bal=${restored.channelBalance}, bright=${restored.extraControlValues["brightness"]?.intValue}, screenOff=${restored.extraControlValues["auto_screen_off_time"]?.intValue}")
                assertEquals(state.activePresetId, restored.activePresetId)
                assertEquals(state.dacFilterIndex, restored.dacFilterIndex)
                assertEquals(state.channelBalance, restored.channelBalance)
            }
        } finally {
            transport.close()
        }
    }
}
