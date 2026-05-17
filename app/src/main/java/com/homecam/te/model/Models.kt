package com.homecam.te.model

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
    val isPoweredOn: Boolean = false,
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
    val cameraId: String,
    val logicalCameraId: String = "",
    val label: String = ""
)

/**
 * Status response from GET /api/status
 */
data class StatusResponse(
    val running: Boolean = false,
    val ip: String = "",
    val port: Int = 0,
    val url: String = "",
    val latestEvent: String? = null,
    val latestEventTime: Long = 0L,
    val latestEventLabel: String = "",
    val cameraPowered: Boolean = true,
    val currentCameraId: String = "",
    val currentLogicalCameraId: String = ""
)

/**
 * Camera switch response
 */
data class CameraSwitchResponse(
    val success: Boolean = false,
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
