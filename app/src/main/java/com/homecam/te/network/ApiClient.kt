package com.homecam.te.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.homecam.te.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * REST API client for communicating with HomeCam server.
 */
class ApiClient(private val baseUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private fun url(path: String): String = "$baseUrl$path"

    /** GET /api/status — device running status */
    suspend fun getStatus(): Result<StatusResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url("/api/status")).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val status = gson.fromJson(body, StatusResponse::class.java)
                Result.success(status)
            } else {
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** GET /api/events — event history list */
    suspend fun getEvents(): Result<List<EventResponseItem>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url("/api/events")).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "[]"
                val type = object : TypeToken<List<EventResponseItem>>() {}.type
                val events: List<EventResponseItem> = gson.fromJson(body, type)
                Result.success(events)
            } else {
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** GET /api/cameras — available cameras list */
    suspend fun getCameras(): Result<List<CameraInfo>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url("/api/cameras")).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "[]"
                val type = object : TypeToken<List<CameraInfo>>() {}.type
                val cameras: List<CameraInfo> = gson.fromJson(body, type)
                Result.success(cameras)
            } else {
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** GET /api/camera/switch — switch to a different camera lens */
    suspend fun switchCamera(
        cameraId: String,
        logicalCameraId: String = cameraId
    ): Result<CameraSwitchResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url("/api/camera/switch?cameraId=$cameraId&logicalCameraId=$logicalCameraId"))
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val res = gson.fromJson(body, CameraSwitchResponse::class.java)
                Result.success(res)
            } else {
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** GET /api/camera/power — turn camera on or off */
    suspend fun setPower(on: Boolean): Result<CameraPowerResponse> = withContext(Dispatchers.IO) {
        try {
            val action = if (on) "on" else "off"
            val request = Request.Builder()
                .url(url("/api/camera/power?action=$action"))
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val res = gson.fromJson(body, CameraPowerResponse::class.java)
                Result.success(res)
            } else {
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** GET /video — MJPEG stream URL (returns the URL string, not the stream itself) */
    fun getVideoUrl(): String = url("/video")
}

data class EventResponseItem(
    val fileName: String = "",
    val timestamp: Long = 0L,
    val eventType: String = "",
    val label: String = "",
    val durationSec: Int = 0,
    val fileSize: Long = 0L
)
