package com.homecam.te.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MJPEG stream client that parses multipart/x-mixed-replace format
 * and delivers JPEG frames via callback.
 *
 * Uses JPEG magic bytes (SOI=0xFFD8, EOI=0xFFD9) to locate frames,
 * which is more robust than boundary-based parsing.
 *
 * Each camera device gets its own MjpegClient instance.
 */
class MjpegClient(
    private val url: String,
    private val onFrame: (ByteArray) -> Unit,
    private val onError: (Exception) -> Unit = {}
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No timeout for stream
        .retryOnConnectionFailure(true)
        .build()

    private var job: Job? = null
    private val running = AtomicBoolean(false)

    companion object {
        private const val TAG = "MjpegClient"
        private const val READ_BUFFER_SIZE = 65536
        // JPEG markers
        private const val SOI_MARKER: Short = 0xFFD8.toShort()  // Start of Image
        private const val EOI_MARKER: Short = 0xFFD9.toShort()  // End of Image
        // Boundary parser constants (legacy)
        private val CRLF = "\r\n".toByteArray()
        private val HEADER_END = "\r\n\r\n".toByteArray()
        private val CONTENT_LENGTH = "Content-Length: ".toByteArray()
    }

    /**
     * Start reading MJPEG stream in the current coroutine.
     */
    suspend fun start() {
        if (!running.compareAndSet(false, true)) return

        withContext(Dispatchers.IO) {
            var retryCount = 0
            while (isActive && running.get()) {
                try {
                    retryCount = 0
                    connectAndRead()
                    // connectAndRead returned normally (stream ended), wait before reconnect
                    if (running.get()) {
                        Log.d(TAG, "Stream ended, reconnecting...")
                        Thread.sleep(1000)
                    }
                } catch (e: Exception) {
                    if (!isActive || !running.get()) break
                    retryCount++
                    val delayMs = minOf(retryCount * 1000L, 10_000L)
                    Log.e(TAG, "Stream error (retry $retryCount in ${delayMs}ms): ${e.message}")
                    onError(e)
                    Thread.sleep(delayMs)
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        job?.cancel()
    }

    private fun connectAndRead() {
        val request = Request.Builder().url(url).header("Connection", "close").build()
        Log.d(TAG, "Connecting to $url...")
        val response = client.newCall(request).execute()
        val body = response.body ?: throw Exception("Empty response body")
        val contentType = body.contentType()?.toString() ?: "unknown"

        Log.d(TAG, "Connected! HTTP ${response.code}, Content-Type: $contentType")

        val inputStream = BufferedInputStream(body.byteStream())
        readFramesByJpegMarkers(inputStream)
    }

    /**
     * Read MJPEG stream by locating JPEG SOI (0xFFD8) and EOI (0xFFD9) markers.
     * This approach does NOT rely on boundary parsing.
     */
    private var frameCount = 0

    private fun readFramesByJpegMarkers(inputStream: InputStream) {
        val frameBuffer = java.io.ByteArrayOutputStream(256 * 1024) // 256KB initial
        var foundSOI = false
        var byte0 = -1  // Previous byte
        var skippedHeaderBytes = 0

        while (running.get()) {
            val b = inputStream.read()
            if (b == -1) {
                Log.d(TAG, "Stream ended (read returned -1)")
                break
            }

            if (!foundSOI) {
                skippedHeaderBytes++
                // Look for JPEG SOI: 0xFF 0xD8
                if (byte0 == 0xFF && b == 0xD8) {
                    foundSOI = true
                    frameBuffer.reset()
                    frameBuffer.write(0xFF)
                    frameBuffer.write(0xD8)
                    skippedHeaderBytes = 0
                }
                byte0 = b
            } else {
                // Collect bytes until EOI: 0xFF 0xD9
                frameBuffer.write(b)
                if (byte0 == 0xFF && b == 0xD9) {
                    // Complete frame found
                    val jpegData = frameBuffer.toByteArray()
                    if (jpegData.size > 100) { // Ignore tiny invalid frames
                        frameCount++
                        if (frameCount <= 3 || frameCount % 100 == 0) {
                            Log.d(TAG, "Frame #$frameCount received: ${jpegData.size} bytes")
                        }
                        onFrame(jpegData)
                    } else {
                        Log.w(TAG, "Skipped small frame: ${jpegData.size} bytes")
                    }
                    foundSOI = false
                }
                byte0 = b
            }
        }
    }

    /**
     * Legacy boundary-based parser (kept as reference).
     */
    @Suppress("UNUSED")
    private fun readByBoundary(inputStream: InputStream, boundary: ByteArray) {
        var data = ByteArray(0)
        val buffer = ByteArray(READ_BUFFER_SIZE)

        while (running.get()) {
            val bytesRead = inputStream.read(buffer)
            if (bytesRead == -1) break

            data = data + buffer.copyOfRange(0, bytesRead)

            while (true) {
                val frame = extractNextFrame(data, boundary) ?: break
                data = frame.remaining
                if (frame.jpegData.size > 100) {
                    onFrame(frame.jpegData)
                }
            }

            if (data.size > 2 * 1024 * 1024) {
                data = ByteArray(0)
            }
        }
    }

    private fun extractBoundary(contentType: String): ByteArray? {
        val marker = "boundary="
        val idx = contentType.indexOf(marker)
        if (idx == -1) return null
        var boundary = contentType.substring(idx + marker.length)
            .trim()
            .removePrefix("\"").removeSuffix("\"")
        // Avoid double-prepending --
        if (!boundary.startsWith("--")) {
            boundary = "--$boundary"
        }
        return boundary.toByteArray()
    }

    private fun extractNextFrame(data: ByteArray, boundary: ByteArray): ParsedFrame? {
        val boundaryStart = indexOf(data, boundary) ?: return null
        val contentStart = boundaryStart + boundary.size

        var headerStart = contentStart
        if (data.size > headerStart + 1 &&
            data[headerStart] == '\r'.code.toByte() &&
            data[headerStart + 1] == '\n'.code.toByte()
        ) {
            headerStart += 2
        }

        val headerEnd = indexOf(data, HEADER_END, headerStart) ?: return null
        val headerBlock = data.sliceArray(headerStart until headerEnd)
        val contentLength = parseContentLength(headerBlock) ?: return null
        val jpegStart = headerEnd + HEADER_END.size
        val jpegEnd = jpegStart + contentLength

        if (data.size < jpegEnd) return null

        val jpegData = data.sliceArray(jpegStart until jpegEnd)
        val remainingStart = if (data.size > jpegEnd &&
            data[jpegEnd] == '\r'.code.toByte()
        ) {
            jpegEnd + 2
        } else {
            jpegEnd
        }

        val remaining = if (remainingStart < data.size) {
            data.sliceArray(remainingStart until data.size)
        } else {
            ByteArray(0)
        }

        return ParsedFrame(jpegData, remaining)
    }

    private fun parseContentLength(header: ByteArray): Int? {
        val clStr = CONTENT_LENGTH
        val idx = indexOf(header, clStr) ?: return null
        var end = idx + clStr.size
        while (end < header.size &&
            header[end].toInt() != '\r'.code &&
            header[end].toInt() != '\n'.code
        ) {
            end++
        }
        return String(header.sliceArray(idx + clStr.size until end)).trim().toIntOrNull()
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, start: Int = 0): Int? {
        if (pattern.isEmpty()) return start
        outer@ for (i in start..data.size - pattern.size) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return null
    }

    private data class ParsedFrame(val jpegData: ByteArray, val remaining: ByteArray)
}
