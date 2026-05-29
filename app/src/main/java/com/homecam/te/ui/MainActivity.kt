package com.homecam.te.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
    var editingDeviceId by remember { mutableStateOf<String?>(null) }
    var videoHistoryDeviceId by remember { mutableStateOf<String?>(null) }
    val streamFormats by viewModel.streamFormats.collectAsState()
    val videoRotations by viewModel.videoRotations.collectAsState()

    // Double-back exit
    var backPressTime = 0L
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle back press on settings screen — return to main
    BackHandler(enabled = currentScreen == "settings") {
        currentScreen = "main"
    }

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

    // Handle back press in video history
    BackHandler(enabled = videoHistoryDeviceId != null) {
        videoHistoryDeviceId = null
    }

    // Handle back press in fullscreen
    BackHandler(enabled = fullscreenDeviceId != null) {
        viewModel.setFullscreen(null)
    }

    // Collect snackbar messages from ViewModel
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                videoHistoryDeviceId != null -> {
                    val devId = videoHistoryDeviceId!!
                    val state = cameraStates[devId]
                    if (state != null) {
                        VideoHistoryScreen(
                            deviceName = state.device.name.ifEmpty { state.device.ip },
                            deviceIp = state.device.ip,
                            devicePort = state.device.port,
                            videos = emptyList(),
                            onBack = { videoHistoryDeviceId = null },
                            onFetchVideos = { viewModel.repository.getVideos(devId).getOrDefault(emptyList()) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                currentScreen == "settings" -> {
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
                        onEditCamera = { deviceId -> editingDeviceId = deviceId },
                        onPowerToggle = { deviceId -> viewModel.setPower(deviceId, !(cameraStates[deviceId]?.isPoweredOn ?: true)) },
                        onShowVideoHistory = { videoHistoryDeviceId = it },
                        onStreamFormatChange = { deviceId, format -> viewModel.setStreamFormat(deviceId, format) },
                        onVideoRotationChange = { deviceId, rotation -> viewModel.setVideoRotation(deviceId, rotation) },
                        streamFormats = streamFormats,
                        videoRotations = videoRotations,
                        onSwitchCamera = { deviceId, cameraId ->
                            val s = cameraStates[deviceId]
                            val logicalId = s?.availableCameras?.find { it.cameraId == cameraId }?.logicalCameraId ?: cameraId
                            viewModel.switchCamera(deviceId, cameraId, logicalId)
                        },
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

                            // Power toggle (top-right)
                            IconButton(
                                onClick = { viewModel.setPower(devId, !(state.isPoweredOn)) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(48.dp)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                ) {
                                    Icon(
                                        Icons.Default.PowerSettingsNew,
                                        contentDescription = if (state.isPoweredOn) "关闭摄像头" else "打开摄像头",
                                        tint = if (state.isPoweredOn) Color(0xFF4CAF50) else Color.Gray,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }

                            // Camera switch (bottom-left)
                            if (state.availableCameras.size > 1) {
                                var showCamMenu by remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = { showCamMenu = true },
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp)
                                        .size(48.dp)
                                ) {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                    ) {
                                        Icon(
                                            Icons.Default.CameraAlt,
                                            contentDescription = "切换摄像头",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showCamMenu,
                                    onDismissRequest = { showCamMenu = false }
                                ) {
                                    state.availableCameras.forEach { cam ->
                                        DropdownMenuItem(
                                            text = { Text(cam.label.ifEmpty { cam.cameraId }) },
                                            onClick = {
                                                showCamMenu = false
                                                viewModel.switchCamera(devId, cam.cameraId, cam.logicalCameraId.ifEmpty { cam.cameraId })
                                            }
                                        )
                                    }
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

            // Edit camera dialog
            editingDeviceId?.let { deviceId ->
                val state = cameraStates[deviceId]
                if (state != null) {
                    EditCameraDialog(
                        currentName = state.device.name,
                        currentIp = state.device.ip,
                        currentPort = state.device.port,
                        onDismiss = { editingDeviceId = null },
                        onConfirm = { ip, port, name ->
                            viewModel.editDevice(deviceId, ip, port, name)
                            editingDeviceId = null
                        }
                    )
                }
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
