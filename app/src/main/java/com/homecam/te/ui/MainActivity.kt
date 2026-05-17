package com.homecam.te.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homecam.te.ui.theme.HomecamTETheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomecamTETheme {
                HomecamTEApp()
            }
        }
    }
}

@Composable
fun HomecamTEApp() {
    val viewModel: MainViewModel = viewModel()
    val cameraStates by viewModel.cameraStates.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val fullscreenDeviceId by viewModel.fullscreenDeviceId.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val alertSettings by viewModel.alertSettings.collectAsState()
    val sheetDeviceId by viewModel.sheetDeviceId.collectAsState()

    // Navigation state: "main" or "settings"
    var currentScreen by remember { mutableStateOf("main") }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->
        when (currentScreen) {
            "settings" -> {
                SettingsScreen(
                    settings = alertSettings,
                    onUpdateSettings = { viewModel.updateAlertSettings(it) },
                    onBack = { currentScreen = "main" }
                )
            }
            else -> {
                // Main screen
                TEGridScreen(
                    cameraStates = cameraStates,
                    repository = viewModel.repository,
                    isDiscovering = isDiscovering,
                    fullscreenDeviceId = fullscreenDeviceId,
                    settings = alertSettings,
                    onAddClick = { viewModel.showAddDialog() },
                    onDiscoveryClick = { viewModel.startDiscovery() },
                    onSettingsClick = { currentScreen = "settings" },
                    onSetFullscreen = { viewModel.setFullscreen(it) },
                    onShowEvents = { viewModel.showEventSheet(it) },
                    onRemoveDevice = { viewModel.removeDevice(it) },
                    onSetPower = { id, on -> viewModel.setPower(id, on) },
                    onSwitchCamera = { id, cameraId, logicalId ->
                        viewModel.switchCamera(id, cameraId, logicalId)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding)
                )

                // Add camera dialog
                if (showAddDialog) {
                    AddCameraDialog(
                        onDismiss = { viewModel.hideAddDialog() },
                        onConfirm = { ip, port, name ->
                            viewModel.addManualDevice(ip, port, name)
                        }
                    )
                }

                // Event bottom sheet
                sheetDeviceId?.let { deviceId ->
                    val state = cameraStates[deviceId]
                    EventSheet(
                        cameraState = state,
                        onDismiss = { viewModel.hideEventSheet() }
                    )
                }
            }
        }
    }
}
