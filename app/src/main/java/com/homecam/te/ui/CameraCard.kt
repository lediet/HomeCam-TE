package com.homecam.te.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homecam.te.data.CameraRepository
import com.homecam.te.model.CameraState
import kotlinx.coroutines.flow.StateFlow

/**
 * Camera card displays MJPEG video + latest event + expand button.
 *
 * Click video -> fullscreen
 * Long press -> context menu (power/switch)
 * Click arrow -> event sheet
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraCard(
    cameraState: CameraState,
    frameFlow: StateFlow<ByteArray?>,
    onFullscreen: () -> Unit,
    onShowEvents: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(4.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // MJPEG Video area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .combinedClickable(
                        onClick = onFullscreen,
                        onLongClick = onLongPress
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (cameraState.isOnline && cameraState.isPoweredOn) {
                    MjpegView(
                        frameFlow = frameFlow,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Offline or powered off placeholder
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (!cameraState.isOnline) Icons.Default.Videocam else Icons.Default.PowerOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (!cameraState.isOnline) "设备离线" else "摄像头已关闭",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Status bar
            StatusBar(
                cameraState = cameraState,
                onExpand = onShowEvents,
                modifier = Modifier.fillMaxWidth()
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
            // Online indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .padding(end = 6.dp)
            ) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = if (cameraState.isOnline) Color(0xFF4CAF50) else Color.Gray
                ) {}
            }

            // Device name
            Text(
                text = cameraState.device.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Latest event or placeholder
            if (cameraState.latestEvent != null) {
                Text(
                    text = cameraState.latestEventLabel.ifEmpty {
                        formatEventDisplay(cameraState.latestEvent)
                    }.let { label ->
                        if (label.isNotEmpty()) label else cameraState.latestEvent ?: ""
                    },
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

            // Expand button
            IconButton(
                onClick = onExpand,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "展开事件",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatEventDisplay(type: String): String {
    return when (type) {
        "enter" -> "有人进入"
        "leave" -> "有人离开"
        "cry" -> "婴儿哭声"
        "sleep" -> "宝宝睡着了"
        "wake_up" -> "宝宝睡醒了"
        "motion" -> "画面变动"
        else -> type
    }
}
