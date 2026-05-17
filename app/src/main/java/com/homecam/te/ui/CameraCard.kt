package com.homecam.te.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homecam.te.model.CameraState
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check

@Composable
fun CameraCard(
    cameraState: CameraState,
    frameFlow: StateFlow<ByteArray?>,
    onFullscreen: () -> Unit,
    onShowEvents: () -> Unit,
    onDelete: () -> Unit,
    onPowerToggle: () -> Unit = {},
    onSwitchCamera: (cameraId: String) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    isEditMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .pointerInput(isEditMode) {
                            if (!isEditMode) {
                                detectTapGestures(
                                    onTap = { onFullscreen() },
                                    onLongPress = { showMenu = true }
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                if (cameraState.isOnline) {
                    MjpegView(
                        frameFlow = frameFlow,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(text = "设备离线", color = Color.Gray, fontSize = 14.sp)
                    }
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    offset = DpOffset(8.dp, 0.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("删除设备", color = Color.Red) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }

                // Name overlay (top-left)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = cameraState.device.name.ifEmpty { cameraState.device.ip },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Power toggle overlay (top-right)
                IconButton(
                    onClick = onPowerToggle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = if (cameraState.isPoweredOn) "关闭摄像头" else "打开摄像头",
                            tint = if (cameraState.isPoweredOn) Color(0xFF4CAF50) else Color.Gray,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }

                // Camera switch overlay (bottom-left)
                if (cameraState.availableCameras.size > 1) {
                    var showCameraMenu by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showCameraMenu = true },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .size(32.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "切换摄像头",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showCameraMenu,
                        onDismissRequest = { showCameraMenu = false }
                    ) {
                        cameraState.availableCameras.forEach { cam ->
                            DropdownMenuItem(
                                text = { Text(cam.label.ifEmpty { cam.cameraId }) },
                                onClick = {
                                    showCameraMenu = false
                                    onSwitchCamera(cam.cameraId)
                                },
                                trailingIcon = if (cam.cameraId == cameraState.currentCameraId) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            StatusBar(
                cameraState = cameraState,
                onExpand = onShowEvents,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Edit mode transparent overlay for drag gestures
    if (isEditMode) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(4.dp)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() }
                    )
                }
        )
    }
}
}

@Composable
private fun StatusBar(
    cameraState: CameraState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (cameraState.latestEvent != null) {
                val timeStr = remember(cameraState.latestEventTime) {
                    SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(Date(cameraState.latestEventTime))
                }
                Text(
                    text = "${formatEventShort(cameraState.latestEvent, cameraState.latestEventLabel)} $timeStr",
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = "等待事件...",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.weight(1f)
                )
            }

            IconButton(onClick = onExpand, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatEventShort(type: String, label: String): String {
    return when (type) {
        "enter" -> "有${label}进入了"
        "leave" -> "有${label}离开了"
        "cry" -> "婴儿哭声"
        "sleep" -> "宝宝睡着了"
        "wake_up" -> "宝宝睡醒了"
        else -> type
    }
}