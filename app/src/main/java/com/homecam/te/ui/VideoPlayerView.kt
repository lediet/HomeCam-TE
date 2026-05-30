package com.homecam.te.ui

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * ExoPlayer wrapper for MP4 video playback (video history).
 */
@Composable
fun VideoPlayerView(
    videoUrl: String,
    modifier: Modifier = Modifier,
    onPlayerReady: (ExoPlayer) -> Unit = {}
) {
    var currentUrl by remember { mutableStateOf(videoUrl) }

    AndroidView(
        factory = { ctx ->
            val p = ExoPlayer.Builder(ctx).build().apply {
                playWhenReady = true
                setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
                prepare()
            }
            currentUrl = videoUrl
            onPlayerReady(p)
            PlayerView(ctx).apply {
                this.player = p
                useController = true
            }
        },
        update = { view ->
            if (videoUrl != currentUrl) {
                currentUrl = videoUrl
                (view.player as? ExoPlayer)?.run {
                    setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
                    prepare()
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun rememberVideoPlayer(
    videoUrl: String?
): ExoPlayer? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(videoUrl) {
        if (videoUrl != null) {
            val p = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
                prepare()
                playWhenReady = true
            }
            player.value = p
            onDispose {
                p.release()
                player.value = null
            }
        } else {
            onDispose { }
        }
    }
    return player.value
}