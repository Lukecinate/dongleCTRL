package com.suprcharg3d.dongleCTRL

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.suprcharg3d.dongleCTRL.ui.AppScreen
import com.suprcharg3d.dongleCTRL.ui.MainViewModel
import com.suprcharg3d.dongleCTRL.ui.screens.DeviceDashboardScreen
import com.suprcharg3d.dongleCTRL.ui.screens.DevicesScreen
import com.suprcharg3d.dongleCTRL.ui.screens.EqualizerScreen
import com.suprcharg3d.dongleCTRL.ui.screens.SettingsScreen
import com.suprcharg3d.dongleCTRL.ui.theme.DongleCtrlTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }

            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (text != null) viewModel.importAutoEqText(text)
                }
            }

            val exportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/plain")
            ) { uri ->
                if (uri != null) {
                    val content = viewModel.exportCurrentAutoEqText()
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
                }
            }

            val btPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) {
                viewModel.onBluetoothPermissionResult()
            }

            val requestBtPermissions: () -> Unit = {
                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                } else {
                    arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
                }
                btPermissionLauncher.launch(perms)
            }

            LaunchedEffect(uiState.toastMessage) {
                val msg = uiState.toastMessage ?: return@LaunchedEffect
                snackbarHostState.showSnackbar(msg)
                viewModel.clearToast()
            }

            BackHandler(
                enabled = uiState.currentScreen == AppScreen.EQUALIZER ||
                    uiState.currentScreen == AppScreen.DASHBOARD
            ) {
                when (uiState.currentScreen) {
                    AppScreen.EQUALIZER -> viewModel.navigateTo(AppScreen.DASHBOARD)
                    AppScreen.DASHBOARD -> viewModel.navigateTo(AppScreen.DEVICES)
                    else -> Unit
                }
            }

            DongleCtrlTheme(themeMode = uiState.prefs.themeMode) {
                val showBottomBar = uiState.currentScreen == AppScreen.DEVICES ||
                    uiState.currentScreen == AppScreen.SETTINGS

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = uiState.currentScreen == AppScreen.DEVICES,
                                    onClick = { viewModel.navigateTo(AppScreen.DEVICES) },
                                    icon = { Icon(Icons.Rounded.Headphones, contentDescription = "Device List") },
                                    label = { Text("Device List") }
                                )
                                NavigationBarItem(
                                    selected = uiState.currentScreen == AppScreen.SETTINGS,
                                    onClick = { viewModel.navigateTo(AppScreen.SETTINGS) },
                                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (uiState.currentScreen) {
                            AppScreen.DEVICES -> DevicesScreen(
                                detectedDevices = uiState.detectedDevices,
                                detectedBtDevices = uiState.detectedBtDevices,
                                hasBtPermission = uiState.hasBtPermission,
                                isBtScanning = uiState.isBtScanning,
                                activeSession = uiState.activeSession,
                                catalogProfiles = uiState.catalogProfiles,
                                onRefreshScan = viewModel::refreshUsbScan,
                                onScanBluetooth = {
                                    if (!uiState.hasBtPermission) {
                                        requestBtPermissions()
                                    } else {
                                        viewModel.refreshBluetoothScan()
                                    }
                                },
                                onRequestBtPermission = requestBtPermissions,
                                onConnectUsb = { viewModel.connectToUsbDevice(it, navigateToDashboard = true) },
                                onConnectBt = { viewModel.connectToBluetoothDevice(it, navigateToDashboard = true) },
                                onOpenActiveSession = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                                onOverrideProtocol = viewModel::overrideProtocol
                            )

                            AppScreen.DASHBOARD -> {
                                val session = uiState.activeSession
                                if (session == null) {
                                    viewModel.navigateTo(AppScreen.DEVICES)
                                } else {
                                    DeviceDashboardScreen(
                                        session = session,
                                        viewModel = viewModel,
                                        onBack = { viewModel.navigateTo(AppScreen.DEVICES) },
                                        onOpenEqualizer = { viewModel.navigateTo(AppScreen.EQUALIZER) }
                                    )
                                }
                            }

                            AppScreen.EQUALIZER -> {
                                val session = uiState.activeSession
                                if (session == null) {
                                    viewModel.navigateTo(AppScreen.DEVICES)
                                } else {
                                    EqualizerScreen(
                                        session = session,
                                        selectedBandIndex = uiState.selectedBandIndex,
                                        presets = uiState.presets,
                                        autoApplyEnabled = uiState.prefs.autoApplyEq,
                                        viewModel = viewModel,
                                        onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                                        onRequestImportFile = {
                                            importLauncher.launch(arrayOf("text/*", "application/xml", "*/*"))
                                        },
                                        onRequestExportFile = {
                                            val slug = session.profile.model.lowercase().replace(" ", "_")
                                            exportLauncher.launch("${slug}_peq.txt")
                                        }
                                    )
                                }
                            }

                            AppScreen.SETTINGS -> SettingsScreen(
                                prefs = uiState.prefs,
                                catalogProfiles = uiState.catalogProfiles,
                                usbLogs = uiState.usbLogs,
                                onThemeModeChange = viewModel::setThemeMode,
                                onAutoApplyChange = viewModel::setAutoApplyEq,
                                onClearLogs = viewModel::clearUsbLogs
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.refreshUsbScan()
    }
}
