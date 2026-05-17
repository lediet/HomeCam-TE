package com.homecam.te.model

import com.google.gson.annotations.SerializedName

/**
 * Local-stored camera device info (Room Entity)
 */
data class CameraDevice(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int = 8080,
    val isAutoDiscovered: Boolean = false,
    val lastSeen: Long = 0L
)

/**
 * Runtime camera device state
 */
data class CameraState(
    val device: CameraDevice,
    val isOnline: Boolean = false,
    val isPoweredOn: Boolean = true,
    val latestEvent: String? = null,
    val latestEventTime: Long = 0L,
    val latestEventLabel: String = "",
    val events: List<EventItem> = emptyList(),
    val availableCameras: List<CameraInfo> = emptyList(),
    val currentCameraId: String = ""
)

/**
 * Event log item
 */
data class EventItem(
    val type: String,
    val time: Long,
    val label: String = "",
    val displayText: String = ""
)

/**
 * Camera info from GET /api/cameras
 */
data class CameraInfo(
    @SerializedName("cameraId")
    val cameraId: String,
    @SerializedName("logicalCameraId")
    val logicalCameraId: String = "",
    @SerializedName("label")
    val label: String = ""
)

/**
 * Cameras list response from GET /api/cameras
 * Returns {"cameras": [...], "currentCameraId": "...", ...}
 */
data class CamerasResponse(
    @SerializedName("cameras")
    val cameras: List<CameraInfo> = emptyList(),
    @SerializedName("currentCameraId")
    val currentCameraId: String = "",
    @SerializedName("currentLogicalCameraId")
    val currentLogicalCameraId: String = ""
)

/**
 * Status response from GET /api/status
 * Server returns snake_case JSON, mapped via @SerializedName
 */
data class StatusResponse(
    val running: Boolean = false,
    val ip: String = "",
    val port: Int = 0,
    val url: String = "",
    @SerializedName("latest_event")
    val latestEvent: String? = null,
    @SerializedName("latest_event_time")
    val latestEventTime: Long = 0L,
    @SerializedName("latest_event_label")
    val latestEventLabel: String = "",
    @SerializedName("camera_powered")
    val cameraPowered: Boolean = true,
    @SerializedName("current_camera_id")
    val currentCameraId: String = "",
    @SerializedName("current_logical_camera_id")
    val currentLogicalCameraId: String = ""
)

/**
 * Camera switch response
 */
data class CameraSwitchResponse(
    val success: Boolean = false,
    @SerializedName("cameraId")
    val cameraId: String = "",
    val switching: Boolean = false,
    val error: String = ""
)

/**
 * Camera power response
 */
data class CameraPowerResponse(
    val success: Boolean = false,
    val power: Boolean = false,
    val error: String = ""
)
