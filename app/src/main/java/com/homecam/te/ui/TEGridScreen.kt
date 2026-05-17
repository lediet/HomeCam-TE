package com.homecam.te.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homecam.te.data.CameraRepository
import com.homecam.te.model.CameraState
import kotlinx.coroutines.flow.StateFlow

/**
 * Main grid screen that displays camera cards in adaptive layout.
 *
 * Grid rules:
 *   1 device  -> 1 column
 *   2 devices -> 1 column (vertical)
 *   3 devices -> 2 columns (first 2 side-by-side, 3rd spans full)
 *   4 devices -> 2 columns (2x2)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TEGridScreen(
    cameraStates: Map<String, CameraState>,
    repository: CameraRepository,
    isDiscovering: Boolean,
    fullscreenDeviceId: String?,
    settings: AlertSettings,
    onAddClick: () -> Unit,
    onDiscoveryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSetFullscreen: (String?) -> Unit,
    onShowEvents: (String) -> Unit,
    onRemoveDevice: (String) -> Unit,
    onSetPower: (String, Boolean) -> Unit,
    onSwitchCamera: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val deviceCount = cameraStates.size

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Homecam-TE")
                        if (deviceCount > 0) {
                            Text(
                                text = "已连接: $deviceCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onDiscoveryClick, enabled = !isDiscovering) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "自动发现",
                            tint = if (isDiscovering) MaterialTheme.colorScheme.primary
                                   else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = onAddClick) {
                        Icon(Icons.Default.Add, contentDescription = "添加摄像头")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { padding ->
        if (cameraStates.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "尚未添加摄像头",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "点击 + 手动添加，或点击搜索自动发现",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            CameraGrid(
                cameraStates = cameraStates.values.toList(),
                repository = repository,
                fullscreenDeviceId = fullscreenDeviceId,
                onSetFullscreen = onSetFullscreen,
                onShowEvents = onShowEvents,
                onRemoveDevice = onRemoveDevice,
                onSetPower = onSetPower,
                onSwitchCamera = onSwitchCamera,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun CameraGrid(
    cameraStates: List<CameraState>,
    repository: CameraRepository,
    fullscreenDeviceId: String?,
    onSetFullscreen: (String?) -> Unit,
    onShowEvents: (String) -> Unit,
    onRemoveDevice: (String) -> Unit,
    onSetPower: (String, Boolean) -> Unit,
    onSwitchCamera: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val count = cameraStates.size
    val columns = when {
        count <= 2 -> 1
        else -> 2
    }

    if (fullscreenDeviceId != null) {
        val state = cameraStates.find { it.device.id == fullscreenDeviceId }
        if (state != null) {
            FullscreenCamera(
                cameraState = state,
                frameFlow = repository.getFrameFlow(state.device.id),
                onClose = { onSetFullscreen(null) },
                onShowEvents = { onShowEvents(state.device.id) },
                onSetPower = { on -> onSetPower(state.device.id, on) },
                modifier = Modifier.fillMaxSize()
            )
            return
        }
    }

    Column(modifier = modifier.padding(4.dp)) {
        var index = 0
        while (index < count) {
            Row(modifier = Modifier.fillMaxWidth()) {
                if (columns == 1) {
                    val state = cameraStates[index]
                    CameraCard(
                        cameraState = state,
                        frameFlow = repository.getFrameFlow(state.device.id),
                        onFullscreen = { onSetFullscreen(state.device.id) },
                        onShowEvents = { onShowEvents(state.device.id) },
                        onLongPress = { },
                        modifier = Modifier.weight(1f)
                    )
                    index++
                } else {
                    if (count == 3 && index == count - 1) {
                        val state = cameraStates[index]
                        CameraCard(
                            cameraState = state,
                            frameFlow = repository.getFrameFlow(state.device.id),
                            onFullscreen = { onSetFullscreen(state.device.id) },
                            onShowEvents = { onShowEvents(state.device.id) },
                            onLongPress = { },
                            modifier = Modifier.weight(1f)
                        )
                        index++
                    } else if (index + 1 < count) {
                        val state1 = cameraStates[index]
                        val state2 = cameraStates[index + 1]
                        CameraCard(
                            cameraState = state1,
                            frameFlow = repository.getFrameFlow(state1.device.id),
                            onFullscreen = { onSetFullscreen(state1.device.id) },
                            onShowEvents = { onShowEvents(state1.device.id) },
                            onLongPress = { },
                            modifier = Modifier.weight(1f)
                        )
                        CameraCard(
                            cameraState = state2,
                            frameFlow = repository.getFrameFlow(state2.device.id),
                            onFullscreen = { onSetFullscreen(state2.device.id) },
                            onShowEvents = { onShowEvents(state2.device.id) },
                            onLongPress = { },
                            modifier = Modifier.weight(1f)
                        )
                        index += 2
                    } else {
                        val state = cameraStates[index]
                        CameraCard(
                            cameraState = state,
                            frameFlow = repository.getFrameFlow(state.device.id),
                            onFullscreen = { onSetFullscreen(state.device.id) },
                            onShowEvents = { onShowEvents(state.device.id) },
                            onLongPress = { },
                            modifier = Modifier.weight(1f)
                        )
                        index++
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenCamera(
    cameraState: CameraState,
    frameFlow: StateFlow<ByteArray?>,
    onClose: () -> Unit,
    onShowEvents: () -> Unit,
    onSetPower: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        MjpegView(
            frameFlow = frameFlow,
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭全屏",
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}
