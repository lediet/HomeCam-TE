package com.homecam.te.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homecam.te.model.VideoRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoHistoryScreen(
    deviceName: String,
    deviceIp: String,
    devicePort: Int,
    videos: List<VideoRecord>,
    onBack: () -> Unit,
    onFetchVideos: suspend () -> List<VideoRecord>,
    modifier: Modifier = Modifier
) {
    var videoList by remember { mutableStateOf(videos) }
    var selectedVideo by remember { mutableStateOf<VideoRecord?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Refresh on enter
    LaunchedEffect(Unit) {
        isLoading = true
        videoList = onFetchVideos()
        isLoading = false
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("$deviceName - 历史录像") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Video list (top half)
            if (isLoading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (videoList.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("暂无录像", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(videoList) { video ->
                        VideoListItem(
                            video = video,
                            isSelected = selectedVideo?.fileName == video.fileName,
                            baseUrl = "http://$deviceIp:$devicePort",
                            onClick = { selectedVideo = video }
                        )
                    }
                }
            }

            // Video player (bottom half)
            HorizontalDivider()
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (selectedVideo != null) {
                    val fullUrl = "http://$deviceIp:$devicePort${selectedVideo!!.url}"
                    VideoPlayerView(
                        videoUrl = fullUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("请选择上方录像进行播放", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun VideoListItem(
    video: VideoRecord,
    isSelected: Boolean,
    baseUrl: String,
    onClick: () -> Unit
) {
    val thumbUrl = "$baseUrl/api/thumbnails/${video.fileName}"
    var thumbnail by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(video.fileName) {
        try {
            withContext(Dispatchers.IO) {
                val url = URL(thumbUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val stream = conn.inputStream
                val baos = ByteArrayOutputStream()
                stream.copyTo(baos)
                val bmp = BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.size())
                thumbnail = bmp
            }
        } catch (_: Exception) { }
    }

    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val dateStr = remember(video.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(Date(video.timestamp))
    }
    val eventLabel = remember(video.eventType) { getEventTypeLabel(video.eventType) }
    val eventColor = remember(video.eventType) { getEventTypeColor(video.eventType) }
    val sizeStr = remember(video.fileSize) {
        if (video.fileSize > 1024 * 1024) "%.1f MB".format(video.fileSize / (1024.0 * 1024))
        else "%d KB".format(video.fileSize / 1024)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier.size(80.dp, 45.dp),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("加载中...", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(dateStr, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = eventColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = eventLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            fontSize = 11.sp,
                            color = eventColor
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${video.durationSec}秒 | $sizeStr",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            IconButton(onClick = onClick) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun getEventTypeLabel(type: String): String = when (type) {
    "motion" -> "人物移动"
    "cry" -> "婴儿哭声"
    "sleep" -> "宝宝睡着了"
    "wake_up" -> "宝宝睡醒了"
    "enter" -> "有人进入"
    "leave" -> "有人离开"
    "fall" -> "有人摔倒"
    "get_up" -> "有人站起来了"
    "phone" -> "玩手机"
    else -> type
}

private fun getEventTypeColor(type: String): Color = when (type) {
    "motion", "enter", "leave" -> Color(0xFF4CAF50)
    "cry" -> Color(0xFFFF9800)
    "sleep", "wake_up" -> Color(0xFF2196F3)
    "fall", "get_up" -> Color(0xFFF44336)
    "phone" -> Color(0xFF9C27B0)
    else -> Color(0xFF888888)
}