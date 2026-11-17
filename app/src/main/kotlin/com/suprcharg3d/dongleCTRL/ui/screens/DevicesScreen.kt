package com.suprcharg3d.dongleCTRL.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suprcharg3d.dongleCTRL.bt.DetectedBluetoothDac
import com.suprcharg3d.dongleCTRL.ui.ActiveDacSession
import com.suprcharg3d.dongleCTRL.ui.components.DacDongleIllustration
import com.suprcharg3d.dongleCTRL.ui.components.DacProductImage
import com.suprcharg3d.dongleCTRL.usb.DetectedUsbDac
import com.suprcharg3d.dongleCTRL.protocol.DacProfile
import com.suprcharg3d.dongleCTRL.protocol.ProtocolFamily
import com.suprcharg3d.dongleCTRL.protocol.TransportType

@Composable
fun DevicesScreen(
    detectedDevices: List<DetectedUsbDac>,
    detectedBtDevices: List<DetectedBluetoothDac>,
    hasBtPermission: Boolean,
    isBtScanning: Boolean,
    activeSession: ActiveDacSession?,
    catalogProfiles: List<DacProfile>,
    onRefreshScan: () -> Unit,
    onScanBluetooth: () -> Unit,
    onRequestBtPermission: () -> Unit,
    onConnectUsb: (DetectedUsbDac) -> Unit,
    onConnectBt: (DetectedBluetoothDac) -> Unit,
    onOpenActiveSession: () -> Unit,
    onOverrideProtocol: (DetectedUsbDac, ProtocolFamily) -> Unit,
    modifier: Modifier = Modifier
) {
    var protocolEditTarget by remember { mutableStateOf<DetectedUsbDac?>(null) }
    val totalCount = detectedDevices.size + detectedBtDevices.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Minimalist header inspired by "MY DEVICES [count]" reference
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MY DEVICES",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = totalCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onScanBluetooth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.BluetoothSearching,
                    contentDescription = "Scan Bluetooth DACs",
                    tint = if (isBtScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onRefreshScan) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Scan USB DACs"
                )
            }
        }

        if (!hasBtPermission) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bluetooth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Enable Bluetooth to detect wireless DACs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRequestBtPermission) {
                        Text("Allow")
                    }
                }
            }
        }

        if (totalCount == 0) {
            EmptyUsbState(
                catalogCount = catalogProfiles.size,
                onScanClick = onRefreshScan,
                onScanBtClick = onScanBluetooth,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(detectedDevices, key = { it.device.deviceName }) { entry ->
                    val isLiveConnected = activeSession?.deviceKey == entry.device.deviceName

                    UsbDacCard(
                        entry = entry,
                        isConnected = isLiveConnected,
                        onPrimaryClick = {
                            if (isLiveConnected) onOpenActiveSession() else onConnectUsb(entry)
                        },
                        onChangeProtocol = { protocolEditTarget = entry }
                    )
                }

                items(detectedBtDevices, key = { it.descriptor.deviceKey }) { bt ->
                    val isLiveConnected = activeSession?.deviceKey == bt.descriptor.deviceKey

                    BluetoothDacCard(
                        entry = bt,
                        isConnected = isLiveConnected,
                        onClick = {
                            if (isLiveConnected) onOpenActiveSession() else onConnectBt(bt)
                        }
                    )
                }
            }
        }
    }

    protocolEditTarget?.let { target ->
        ProtocolOverrideDialog(
            entry = target,
            onSelect = { family ->
                onOverrideProtocol(target, family)
                protocolEditTarget = null
            },
            onDismiss = { protocolEditTarget = null }
        )
    }
}

@Composable
private fun UsbDacCard(
    entry: DetectedUsbDac,
    isConnected: Boolean,
    onPrimaryClick: () -> Unit,
    onChangeProtocol: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val profile = entry.effectiveProfile

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPrimaryClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 74.dp, height = 54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center
            ) {
                DacProductImage(
                    profile = profile,
                    isConnected = isConnected,
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Usb,
                        contentDescription = null,
                        tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = profile.model,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${profile.brand} · ${profile.protocol.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (isConnected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = when {
                                isConnected -> "CONNECTED"
                                entry.hasPermission -> "READY"
                                else -> "TAP TO AUTHORIZE"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "VID ${"%04X".format(entry.device.vendorId)}:${"%04X".format(entry.device.productId)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isConnected) "Open Controls" else "Connect") },
                        onClick = {
                            menuExpanded = false
                            onPrimaryClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Change Protocol Driver") },
                        onClick = {
                            menuExpanded = false
                            onChangeProtocol()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BluetoothDacCard(
    entry: DetectedBluetoothDac,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    val profile = entry.matchedProfile
    val transportLabel = if (profile.transport == TransportType.BLUETOOTH_GATT) "BLE GATT" else "BT SPP"

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 74.dp, height = 54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center
            ) {
                DacProductImage(
                    profile = profile,
                    isConnected = isConnected || entry.isAudioConnected,
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Bluetooth,
                        contentDescription = null,
                        tint = if (isConnected || entry.isAudioConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${profile.model} · $transportLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (isConnected) MaterialTheme.colorScheme.primaryContainer
                        else if (entry.isAudioConnected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = when {
                                isConnected -> "CONNECTED"
                                entry.isAudioConnected -> "AUDIO ACTIVE · TAP"
                                entry.isBonded -> "PAIRED"
                                else -> "NEARBY"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer
                            else if (entry.isAudioConnected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = entry.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyUsbState(
    catalogCount: Int,
    onScanClick: () -> Unit,
    onScanBtClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DacDongleIllustration(
                    isConnected = false,
                    modifier = Modifier.size(width = 160.dp, height = 96.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Connect a USB or Bluetooth DAC",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Auto-detects EPZ, WalkPlay, and FiiO DACs offline ($catalogCount+ models). Plug in via USB-C OTG or pair via Bluetooth.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onScanClick) {
                        Icon(Icons.Rounded.Usb, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan USB")
                    }
                    FilledTonalButton(onClick = onScanBtClick) {
                        Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bluetooth")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolOverrideDialog(
    entry: DetectedUsbDac,
    onSelect: (ProtocolFamily) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Protocol Driver") },
        text = {
            LazyColumn(modifier = Modifier.height(320.dp)) {
                items(ProtocolFamily.entries) { fam ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(fam) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = entry.effectiveProfile.protocol == fam,
                            onClick = { onSelect(fam) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(fam.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
