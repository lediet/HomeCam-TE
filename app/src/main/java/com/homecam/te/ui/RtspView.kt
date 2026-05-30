package com.homecam.te.ui

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * RTSP stream viewer using ExoPlayer.
 * Requires API 31+ for RTSP support.
 */
@Composable
fun RtspView(
    rtspUrl: String,
    modifier: Modifier = Modifier
) {

    AndroidView(
        factory = { ctx ->
            val player = ExoPlayer.Builder(ctx).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(rtspUrl)))
                prepare()
                playWhenReady = true
            }
            PlayerView(ctx).apply {
                this.player = player
                useController = false  // fullscreen, no controls
            }
        },
        modifier = modifier
    )
}