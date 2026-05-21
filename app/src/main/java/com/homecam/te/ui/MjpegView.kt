package com.homecam.te.ui

import android.graphics.Bitmap
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
 *
 * Optimizations:
 * - RGB565: reduces pixel memory by 50% vs ARGB8888
 * - Bitmap reuse (inBitmap): eliminates GC allocations
 * - Frame dropping: tracks latest frame index, skips stale decodes
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
        // Reusable decode options (RGB565 + inBitmap)
        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inMutable = true
        }
        // Double buffer for safe bitmap reuse (one displays while other decodes)
        var buffer0: Bitmap? = null
        var buffer1: Bitmap? = null
        var writeIndex = 0
        var setupDone = false
        // Frame counter: skip display if a newer frame arrived during decode
        var frameCount = 0L
        var displayedFrame = 0L

        frameFlow.collect { jpegData ->
            if (jpegData == null) return@collect
            frameCount++
            val localFrame = frameCount

            withContext(Dispatchers.Default) {
                try {
                    // First frame: decode bounds to create reusable bitmaps
                    if (!setupDone) {
                        val boundsOpt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size, boundsOpt)
                        val w = boundsOpt.outWidth
                        val h = boundsOpt.outHeight
                        if (w > 0 && h > 0) {
                            buffer0 = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
                            buffer1 = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
                            setupDone = true
                        }
                    }

                    val reuse = if (writeIndex == 0) buffer0 else buffer1
                    if (reuse != null) {
                        decodeOptions.inBitmap = reuse
                        val decoded = BitmapFactory.decodeByteArray(
                            jpegData, 0, jpegData.size, decodeOptions
                        )
                        if (decoded != null) {
                            // Frame dropping: only display if no newer frame arrived
                            if (localFrame > displayedFrame) {
                                withContext(Dispatchers.Main) {
                                    imageView?.setImageBitmap(decoded)
                                }
                                displayedFrame = localFrame
                            }
                            writeIndex = (writeIndex + 1) % 2
                        }
                    } else {
                        // Fallback: create new bitmap (first frame before setup)
                        val bitmap = BitmapFactory.decodeByteArray(
                            jpegData, 0, jpegData.size, decodeOptions
                        )
                        if (bitmap != null) {
                            withContext(Dispatchers.Main) {
                                imageView?.setImageBitmap(bitmap)
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }
}