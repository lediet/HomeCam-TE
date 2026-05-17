package com.homecam.te.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    var currentScreen by remember { mutableStateOf("main") }

    // Double-back exit
    var backPressTime = 0L
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle back press for main screen — double-back to exit
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    BackHandler(enabled = currentScreen == "main" && fullscreenDeviceId == null) {
        if (System.currentTimeMillis() - backPressTime > 2000) {
            backPressTime = System.currentTimeMillis()
            Toast.makeText(context, "\u518D\u6309\u4E00\u6B21\u9000\u51FA", Toast.LENGTH_SHORT).show()
        } else {
            activity?.finish()
        }
    }

    // Handle back press in fullscreen
    BackHandler(enabled = fullscreenDeviceId != null) {
        viewModel.setFullscreen(null)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                "settings" -> {
                    SettingsScreen(
                        settings = alertSettings,
                        onUpdateSettings = { viewModel.updateAlertSettings(it) },
                        onBack = { currentScreen = "main" }
                    )
                }
                else -> {
                    TEGridScreen(
                        cameraStates = cameraStates,
                        repository = viewModel.repository,
                        isDiscovering = isDiscovering,
                        onAddClick = { viewModel.showAddDialog() },
                        onDiscoveryClick = { viewModel.startDiscovery() },
                        onSettingsClick = { currentScreen = "settings" },
                        onSetFullscreen = { viewModel.setFullscreen(it) },
                        onShowEvents = { viewModel.showEventSheet(it) },
                        onDeleteCamera = { viewModel.removeDevice(it) },
                        modifier = Modifier.padding(scaffoldPadding)
                    )
                }
            }

            // Fullscreen overlay (on top of everything)
            fullscreenDeviceId?.let { devId ->
                val state = cameraStates[devId]
                if (state != null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            MjpegView(
                                frameFlow = viewModel.repository.getFrameFlow(devId),
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { viewModel.setFullscreen(null) },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "关闭全屏",
                                        modifier = Modifier.padding(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

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
