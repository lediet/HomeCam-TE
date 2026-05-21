package com.homecam.te.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homecam.te.model.CameraState
import com.homecam.te.model.EventItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bottom sheet showing recent 20 events for a camera.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSheet(
    cameraState: CameraState?,
    onDismiss: () -> Unit
) {
    if (cameraState == null) return

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${cameraState.device.name} - 事件记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            Spacer(Modifier.height(8.dp))

            if (cameraState.events.isEmpty()) {
                Text(
                    text = "暂无事件记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp, horizontal = 8.dp)
                )
            } else {
                LazyColumn {
                    items(cameraState.events.reversed()) { event ->
                        EventItemRow(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventItemRow(event: EventItem) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Event type label
            Surface(
                shape = MaterialTheme.shapes.small,
                color = eventColor(event.type).copy(alpha = 0.15f)
            ) {
                Text(
                    text = eventTypeLabel(event.type),
                    fontSize = 10.sp,
                    color = eventColor(event.type),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            // Event text
            Text(
                text = eventText(event),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )

            // Timestamp
            Text(
                text = dateFormat.format(Date(event.time)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun eventText(event: EventItem): String {
    return event.displayText.ifEmpty {
        when (event.type) {
            "enter" -> "有${event.label}进入了"
            "leave" -> "有${event.label}离开了"
            "cry" -> "检测到婴儿哭声"
            "sleep" -> "宝宝睡着了"
            "wake_up" -> "宝宝睡醒了"
            "fall" -> "检测到有人摔倒"
            "get_up" -> "有人站起来了"
            "phone" -> if (event.label.isNotEmpty()) "有人在玩手机（${event.label}）" else "有人在玩手机"
            else -> event.type
        }
    }
}

private fun eventTypeLabel(type: String): String {
    return when (type) {
        "enter" -> "进入"
        "leave" -> "离开"
        "cry" -> "哭声"
        "sleep" -> "睡眠"
        "wake_up" -> "醒来"
        "fall" -> "跌倒"
        "get_up" -> "起身"
        "phone" -> "玩手机"
        else -> type
    }
}

private fun eventColor(type: String): androidx.compose.ui.graphics.Color {
    return when (type) {
        "enter" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        "leave" -> androidx.compose.ui.graphics.Color(0xFF9E9E9E)
        "cry" -> androidx.compose.ui.graphics.Color(0xFFF44336)
        "sleep" -> androidx.compose.ui.graphics.Color(0xFF2196F3)
        "wake_up" -> androidx.compose.ui.graphics.Color(0xFFFF9800)
        else -> androidx.compose.ui.graphics.Color(0xFF9E9E9E)
    }
}
