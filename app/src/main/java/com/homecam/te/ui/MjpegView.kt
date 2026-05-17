package com.homecam.te.ui

import android.graphics.BitmapFactory
import android.widget.ImageView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow

/**
 * Composable that renders MJPEG frames on an AndroidView ImageView.
 * Uses a single ImageView to avoid Compose recomposition on every frame.
 */
@Composable
fun MjpegView(
    frameFlow: StateFlow<ByteArray?>,
    modifier: Modifier = Modifier
) {
    var imageView by remember { mutableStateOf<ImageView?>(null) }

    AndroidView(
        factory = { ctx ->
            ImageView(ctx).also { iv ->
                iv.scaleType = ImageView.ScaleType.FIT_CENTER
                imageView = iv
            }
        },
        modifier = modifier
    )

    LaunchedEffect(Unit) {
        frameFlow.collect { jpegData ->
            if (jpegData != null) {
                withContext(Dispatchers.Default) {
                    try {
                        val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
                        withContext(Dispatchers.Main) {
                            imageView?.setImageBitmap(bitmap)
                        }
                    } catch (_: Exception) { }
                }
            }
        }
    }
}