package com.homecam.te.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MJPEG stream client that parses multipart/x-mixed-replace format
 * and delivers JPEG frames via callback.
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
        .build()

    private var job: Job? = null
    private val running = AtomicBoolean(false)

    companion object {
        private val BOUNDARY_PREFIX = "--".toByteArray()
        private val CRLF = "\r\n".toByteArray()
        private val HEADER_END = "\r\n\r\n".toByteArray()
        private val CONTENT_LENGTH = "Content-Length: ".toByteArray()
        private const val DEFAULT_BUFFER_SIZE = 65536
        private const val MAX_BUFFER_SIZE = 2 * 1024 * 1024
    }

    /**
     * Start reading MJPEG stream in the current coroutine.
     * Should be launched in a coroutine scope.
     */
    suspend fun start() {
        if (!running.compareAndSet(false, true)) return

        withContext(Dispatchers.IO) {
            var retryCount = 0
            while (isActive && running.get()) {
                try {
                    retryCount = 0
                    connectAndRead()
                } catch (e: Exception) {
                    if (!isActive || !running.get()) break
                    retryCount++
                    val delayMs = minOf(retryCount * 1000L, 10_000L)
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
        val response = client.newCall(request).execute()
        val body = response.body ?: throw Exception("Empty response body")
        val contentType = body.contentType().toString()

        // Expect: multipart/x-mixed-replace; boundary=--boundary
        val boundaryMarker = extractBoundary(contentType)
            ?: throw Exception("Cannot parse boundary from Content-Type: $contentType")

        val inputStream = body.byteStream()
        readMultipartStream(inputStream, boundaryMarker)
    }

    private fun readMultipartStream(inputStream: InputStream, boundary: ByteArray) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var data = ByteArray(0)

        while (running.get()) {
            val bytesRead = inputStream.read(buffer)
            if (bytesRead == -1) break

            // Append to accumulated data
            data = data + buffer.copyOf(bytesRead)

            // Process all complete frames in the buffer
            while (true) {
                val frame = extractNextFrame(data, boundary) ?: break
                data = frame.remaining
                onFrame(frame.jpegData)
            }

            // Prevent unbounded growth: keep only last 2MB if no boundary found
            if (data.size > MAX_BUFFER_SIZE) {
                data = ByteArray(0)
            }
        }
    }

    private fun extractNextFrame(data: ByteArray, boundary: ByteArray): ParsedFrame? {
        // Find boundary start
        val boundaryStart = indexOf(data, boundary) ?: return null
        val contentStart = boundaryStart + boundary.size

        // Skip the optional CRLF after boundary
        var headerStart = contentStart
        if (data.size > headerStart + 1 && data[headerStart] == '\r'.code.toByte() && data[headerStart + 1] == '\n'.code.toByte()) {
            headerStart += 2
        }

        // Find end of headers (double CRLF)
        val headerEnd = indexOf(data, HEADER_END, headerStart) ?: return null
        val headerBlock = data.sliceArray(headerStart until headerEnd)

        // Parse Content-Length
        val contentLength = parseContentLength(headerBlock) ?: return null
        val jpegStart = headerEnd + HEADER_END.size
        val jpegEnd = jpegStart + contentLength

        if (data.size < jpegEnd) return null // Not enough data yet

        val jpegData = data.sliceArray(jpegStart until jpegEnd)

        // Skip CRLF after JPEG to get remaining data
        val remainingStart = if (data.size > jpegEnd && data[jpegEnd] == '\r'.code.toByte()) {
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

    private fun extractBoundary(contentType: String): ByteArray? {
        val marker = "boundary="
        val idx = contentType.indexOf(marker)
        if (idx == -1) return null
        val boundary = contentType.substring(idx + marker.length)
            .trim()
            .removePrefix("\"").removeSuffix("\"")
        return "--$boundary".toByteArray()
    }

    private fun parseContentLength(header: ByteArray): Int? {
        val clStr = CONTENT_LENGTH
        val idx = indexOf(header, clStr) ?: return null
        var end = idx + clStr.size
        while (end < header.size && header[end].toInt() != '\r'.code && header[end].toInt() != '\n'.code) {
            end++
        }
        return String(header.sliceArray(idx + clStr.size until end)).trim().toIntOrNull()
    }

    private data class ParsedFrame(val jpegData: ByteArray, val remaining: ByteArray)

    /** Boyer-Moore-like byte array search */
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

}
