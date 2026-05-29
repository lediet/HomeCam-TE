package com.homecam.te.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TEGridScreen(
    cameraStates: Map<String, CameraState>,
    repository: CameraRepository,
    isDiscovering: Boolean,
    onAddClick: () -> Unit,
    onDiscoveryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSetFullscreen: (String?) -> Unit,
    onShowEvents: (String) -> Unit,
    onDeleteCamera: (String) -> Unit,
    onEditCamera: (String) -> Unit = {},
    onPowerToggle: (String) -> Unit = {},
    onSwitchCamera: (deviceId: String, cameraId: String) -> Unit = { _, _ -> },
    onShowVideoHistory: (String) -> Unit = {},
    onStreamFormatChange: (deviceId: String, format: String) -> Unit = { _, _ -> },
    onVideoRotationChange: (deviceId: String, rotation: Int) -> Unit = { _, _ -> },
    streamFormats: Map<String, String> = emptyMap(),
    videoRotations: Map<String, Int> = emptyMap(),
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
                onSetFullscreen = onSetFullscreen,
                onShowEvents = onShowEvents,
                onDeleteCamera = onDeleteCamera,
                onEditCamera = onEditCamera,
                onPowerToggle = onPowerToggle,
                onSwitchCamera = onSwitchCamera,
                onShowVideoHistory = onShowVideoHistory,
                onStreamFormatChange = onStreamFormatChange,
                onVideoRotationChange = onVideoRotationChange,
                streamFormats = streamFormats,
                videoRotations = videoRotations,
                isEditMode = false,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun CameraGrid(
    cameraStates: List<CameraState>,
    repository: CameraRepository,
    onSetFullscreen: (String?) -> Unit,
    onShowEvents: (String) -> Unit,
    onDeleteCamera: (String) -> Unit,
    onEditCamera: (String) -> Unit = {},
    onPowerToggle: (String) -> Unit = {},
    onSwitchCamera: (deviceId: String, cameraId: String) -> Unit = { _, _ -> },
    onShowVideoHistory: (String) -> Unit = {},
    onStreamFormatChange: (deviceId: String, format: String) -> Unit = { _, _ -> },
    onVideoRotationChange: (deviceId: String, rotation: Int) -> Unit = { _, _ -> },
    streamFormats: Map<String, String> = emptyMap(),
    videoRotations: Map<String, Int> = emptyMap(),
    isEditMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val count = cameraStates.size
    val scrollState = rememberScrollState()

    var order by remember(cameraStates) { mutableStateOf(cameraStates.indices.toList()) }
    var draggedIndex by remember { mutableStateOf(-1) }
    var dragAccumulated by remember { mutableStateOf(0f) }

    val orderedStates = remember(order, cameraStates) {
        order.filter { it < cameraStates.size }.map { cameraStates[it] }
    }

    fun moveItem(from: Int, to: Int) {
        if (from < 0 || from >= order.size || to < 0 || to >= order.size) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }

    Column(modifier = modifier.padding(4.dp).verticalScroll(scrollState)) {
        val rows = remember(orderedStates, videoRotations) {
            val result = mutableListOf<List<CameraState>>()
            var i = 0
            while (i < orderedStates.size) {
                val rot = videoRotations[orderedStates[i].device.id] ?: 0
                if (rot == 90 || rot == 270) {
                    result.add(listOf(orderedStates[i]))
                    i++
                } else {
                    if (i + 1 < orderedStates.size) {
                        val nextRot = videoRotations[orderedStates[i + 1].device.id] ?: 0
                        if (nextRot == 90 || nextRot == 270) {
                            result.add(listOf(orderedStates[i]))
                            i++
                        } else {
                            result.add(listOf(orderedStates[i], orderedStates[i + 1]))
                            i += 2
                        }
                    } else {
                        result.add(listOf(orderedStates[i]))
                        i++
                    }
                }
            }
            result
        }

        var flatIndex = 0
        rows.forEach { rowStates ->
            Row(modifier = Modifier.fillMaxWidth()) {
                if (rowStates.size == 1) {
                    val state = rowStates[0]
                    CameraCard(
                        cameraState = state,
                        frameFlow = repository.getFrameFlow(state.device.id),
                        onFullscreen = { onSetFullscreen(state.device.id) },
                        onShowEvents = { onShowEvents(state.device.id) },
                        onDelete = { onDeleteCamera(state.device.id) },
                        onEdit = { onEditCamera(state.device.id) },
                        onPowerToggle = { onPowerToggle(state.device.id) },
                        onSwitchCamera = { cameraId -> onSwitchCamera(state.device.id, cameraId) },
                        onShowVideoHistory = { onShowVideoHistory(state.device.id) },
                        onStreamFormatChange = { format -> onStreamFormatChange(state.device.id, format) },
                        streamFormat = streamFormats[state.device.id] ?: "mjpg",
                        onVideoRotationChange = { rotation -> onVideoRotationChange(state.device.id, rotation) },
                        videoRotation = videoRotations[state.device.id] ?: 0,
                        onDragStart = { draggedIndex = flatIndex },
                        onDrag = { offset ->
                            if (draggedIndex >= 0) {
                                dragAccumulated += offset.y
                                val rowHeight = 400f
                                while (dragAccumulated > rowHeight && draggedIndex < count - 1) {
                                    moveItem(draggedIndex, draggedIndex + 1)
                                    dragAccumulated -= rowHeight
                                    draggedIndex++
                                }
                                while (dragAccumulated < -rowHeight && draggedIndex > 0) {
                                    moveItem(draggedIndex, draggedIndex - 1)
                                    dragAccumulated += rowHeight
                                    draggedIndex--
                                }
                            }
                        },
                        onDragEnd = {
                            draggedIndex = -1
                            dragAccumulated = 0f
                        },
                        isEditMode = isEditMode,
                        modifier = Modifier.weight(1f)
                    )
                    flatIndex++
                } else {
                    val state1 = rowStates[0]
                    val state2 = rowStates[1]
                    CameraCard(
                        cameraState = state1,
                        frameFlow = repository.getFrameFlow(state1.device.id),
                        onFullscreen = { onSetFullscreen(state1.device.id) },
                        onShowEvents = { onShowEvents(state1.device.id) },
                        onDelete = { onDeleteCamera(state1.device.id) },
                        onEdit = { onEditCamera(state1.device.id) },
                        onPowerToggle = { onPowerToggle(state1.device.id) },
                        onSwitchCamera = { cameraId -> onSwitchCamera(state1.device.id, cameraId) },
                        onShowVideoHistory = { onShowVideoHistory(state1.device.id) },
                        streamFormat = streamFormats[state1.device.id] ?: "mjpg",
                        onVideoRotationChange = { rotation -> onVideoRotationChange(state1.device.id, rotation) },
                        videoRotation = videoRotations[state1.device.id] ?: 0,
                        onDragStart = { draggedIndex = flatIndex },
                        onDrag = { offset ->
                            if (draggedIndex >= 0) {
                                dragAccumulated += offset.y
                                val rowHeight = 400f
                                while (dragAccumulated > rowHeight && draggedIndex < count - 1) {
                                    moveItem(draggedIndex, draggedIndex + 1)
                                    dragAccumulated -= rowHeight
                                    draggedIndex++
                                }
                                while (dragAccumulated < -rowHeight && draggedIndex > 0) {
                                    moveItem(draggedIndex, draggedIndex - 1)
                                    dragAccumulated += rowHeight
                                    draggedIndex--
                                }
                            }
                        },
                        onDragEnd = {
                            draggedIndex = -1
                            dragAccumulated = 0f
                        },
                        isEditMode = isEditMode,
                        modifier = Modifier.weight(1f)
                    )
                    CameraCard(
                        cameraState = state2,
                        frameFlow = repository.getFrameFlow(state2.device.id),
                        onFullscreen = { onSetFullscreen(state2.device.id) },
                        onShowEvents = { onShowEvents(state2.device.id) },
                        onDelete = { onDeleteCamera(state2.device.id) },
                        onEdit = { onEditCamera(state2.device.id) },
                        onPowerToggle = { onPowerToggle(state2.device.id) },
                        onSwitchCamera = { cameraId -> onSwitchCamera(state2.device.id, cameraId) },
                        onShowVideoHistory = { onShowVideoHistory(state2.device.id) },
                        streamFormat = streamFormats[state2.device.id] ?: "mjpg",
                        onVideoRotationChange = { rotation -> onVideoRotationChange(state2.device.id, rotation) },
                        videoRotation = videoRotations[state2.device.id] ?: 0,
                        onDragStart = { draggedIndex = flatIndex + 1 },
                        onDrag = { offset ->
                            if (draggedIndex >= 0) {
                                dragAccumulated += offset.y
                                val rowHeight = 400f
                                while (dragAccumulated > rowHeight && draggedIndex < count - 1) {
                                    moveItem(draggedIndex, draggedIndex + 1)
                                    dragAccumulated -= rowHeight
                                    draggedIndex++
                                }
                                while (dragAccumulated < -rowHeight && draggedIndex > 0) {
                                    moveItem(draggedIndex, draggedIndex - 1)
                                    dragAccumulated += rowHeight
                                    draggedIndex--
                                }
                            }
                        },
                        onDragEnd = {
                            draggedIndex = -1
                            dragAccumulated = 0f
                        },
                        isEditMode = isEditMode,
                        modifier = Modifier.weight(1f)
                    )
                    flatIndex += 2
                }
            }
        }
    }
}
