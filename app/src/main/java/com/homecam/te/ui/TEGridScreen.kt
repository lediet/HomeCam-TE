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
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.homecam.te.R
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
    onShowUserManual: () -> Unit = {},
    streamFormats: Map<String, String> = emptyMap(),
    videoRotations: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val deviceCount = cameraStates.size
    var showHelpDialog by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_help),
                            contentDescription = "帮助"
                        )
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

        if (showHelpDialog) {
            HelpDialog(
                onDismiss = { showHelpDialog = false },
                onShowUserManual = onShowUserManual
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

@Composable
private fun HelpDialog(onDismiss: () -> Unit, onShowUserManual: () -> Unit = {}) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val qrBitmap = remember {
        try {
            context.assets.open("QR/QR.jpg").use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) { null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("关于 HomeCam-TE", fontWeight = FontWeight.Bold)
                TextButton(onClick = { onDismiss(); onShowUserManual() }) {
                    Text("使用说明", color = Color(0xFF1976D2))
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("软件说明", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("本软件配合HomeCam（https://github.com/lediet/HomeCam）使用，可以实现多个摄像头组播显示和日志提醒。", fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))

                Text("开源说明", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("本项目完全开源、永久免费，无广告、所有数据纯本地传输运行，无任何后门和隐私风险，所有功能开放使用，无任何付费门槛。", fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))

                Text("自愿支持", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("若你觉得工具实用，愿意支持开发者持续迭代、优化功能，可自愿打赏。\n赞赏纯属个人意愿，不会限制、影响任何软件功能。", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))

                qrBitmap?.let {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "赞赏二维码",
                            modifier = Modifier.width(160.dp).height(160.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("教程 & 源码地址", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("完整部署教程、源码文件、更新日志统一托管在 GitHub：", fontSize = 14.sp)
                Text(
                    text = "https://github.com/lediet/HomeCam-TE",
                    color = Color(0xFF1976D2),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { uriHandler.openUri("https://github.com/lediet/HomeCam-TE") }
                )
                Spacer(Modifier.height(12.dp))

                Text("交流反馈", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("项目相关问题、功能建议、Bug 反馈，请前往 GitHub 提交 Issue / 参与讨论。", fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))

                Text("免责声明", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Red)
                Spacer(Modifier.height(4.dp))
                Text(
                    "本软件仅限个人家庭非商用学习与使用；\n" +
                    "严禁用于偷拍、侵权、违法违规场景，违规使用产生的一切后果由使用者自行承担；\n" +
                    "软件 AI 检测、监控功能仅作日常辅助参考，不可替代专业安防、监控设备，长期插电使用可能损耗您的电池；\n" +
                    "使用者需自行保障设备、网络与数据安全，作者不对设备故障、数据丢失等问题承担责任。",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}

