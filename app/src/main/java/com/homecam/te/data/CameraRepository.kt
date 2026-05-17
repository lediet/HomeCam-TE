package com.homecam.te.data

import android.util.Log
import com.homecam.te.HomeCamApp
import com.homecam.te.model.*
import com.homecam.te.network.ApiClient
import com.homecam.te.network.EventPoller
import com.homecam.te.network.MjpegClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Manages per-camera connections, MJPEG streams, and event polling.
 * Each camera gets its own ApiClient, MjpegClient, and EventPoller.
 */
class CameraRepository {

    private val database = HomeCamApp.instance.database
    private val dao = database.cameraDao()

    // Per-camera state flows, keyed by device id
    private val cameraStates = MutableStateFlow<Map<String, CameraState>>(emptyMap())
    private val connections = mutableMapOf<String, CameraConnection>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Observable state of all cameras */
    val camerasFlow: StateFlow<Map<String, CameraState>> = cameraStates.asStateFlow()

    /** Persisted device list */
    fun getSavedDevices(): Flow<List<CameraDeviceEntity>> = flow {
        emit(dao.getAll())
    }

    suspend fun saveDevice(device: CameraDevice) {
        dao.insert(
            CameraDeviceEntity(
                id = device.id,
                name = device.name,
                ip = device.ip,
                port = device.port,
                isAutoDiscovered = device.isAutoDiscovered,
                lastSeen = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeDevice(id: String) {
        disconnectDevice(id)
        dao.deleteById(id)
    }

    /**
     * Connect to a device: start MJPEG stream + event polling.
     */
    fun connectDevice(device: CameraDevice) {
        if (connections.containsKey(device.id)) return

        val baseUrl = "http://${device.ip}:${device.port}"
        val apiClient = ApiClient(baseUrl)

        // Update initial state
        updateCameraState(device.id) { it.copy(isOnline = true) }

        // MJPEG stream
        val mjpegClient = MjpegClient(
            url = "$baseUrl/video",
            onFrame = { jpegData ->
                frameFlows.getOrPut(device.id) { MutableStateFlow(null) }.value = jpegData
                // Mark online when frames arrive (e.g. after reconnection)
                val cur = cameraStates.value[device.id]
                if (cur != null && !cur.isOnline) {
                    updateCameraState(device.id) { it.copy(isOnline = true) }
                }
            },
            onError = { _ ->
                updateCameraState(device.id) { it.copy(isOnline = false) }
            }
        )

        // Event poller
        val eventPoller = EventPoller(
            apiClient = apiClient,
            onNewEvents = { events ->
                val state = cameraStates.value[device.id] ?: return@EventPoller
                val allEvents = (state.events + events).takeLast(20)
                val latest = events.last()
                updateCameraState(device.id) {
                    it.copy(
                        events = allEvents,
                        latestEvent = latest.type,
                        latestEventTime = latest.time,
                        latestEventLabel = latest.label
                    )
                }
            },
            onError = { /* silently ignore poll errors */ }
        )

        // Fetch cameras list once on connect
        scope.launch {
            delay(1000)
            val camResult = apiClient.getCameras()
            camResult.onSuccess { cameras ->
                updateCameraState(device.id) { it.copy(availableCameras = cameras) }
            }
        }

        connections[device.id] = CameraConnection(
            device = device,
            apiClient = apiClient,
            mjpegClient = mjpegClient,
            eventPoller = eventPoller
        )

        // Start MJPEG streaming
        scope.launch {
            mjpegClient.start()
        }

        eventPoller.start()

        // Initialize state
        cameraStates.update { map ->
            map + (device.id to CameraState(device = device, isOnline = true))
        }
    }

    /**
     * Disconnect from a device and clean up resources.
     */
    fun disconnectDevice(id: String) {
        connections.remove(id)?.let { conn ->
            conn.mjpegClient.stop()
            conn.eventPoller.stop()
        }
        cameraStates.update { map -> map - id }
    }

    fun disconnectAll() {
        connections.keys.toList().forEach { disconnectDevice(it) }
    }

    /** Get ApiClient for a device (for controls: power, switch) */
    fun getApiClient(deviceId: String): ApiClient? = connections[deviceId]?.apiClient

    /** Restart MJPEG stream for a device (e.g. after power toggle) */
    fun restartMjpeg(deviceId: String) {
        connections[deviceId]?.let { conn ->
            conn.mjpegClient.stop()
            scope.launch {
                delay(500) // wait for server to settle
                Log.d("CameraRepository", "Restarting MJPEG for $deviceId")
                conn.mjpegClient.start()
            }
        }
    }

    /** Per-device frame flow for MJPEG rendering */
    private val frameFlows = mutableMapOf<String, MutableStateFlow<ByteArray?>>()

    fun getFrameFlow(deviceId: String): StateFlow<ByteArray?> {
        return frameFlows.getOrPut(deviceId) { MutableStateFlow(null) }.asStateFlow()
    }

    private fun updateCameraState(id: String, transform: (CameraState) -> CameraState) {
        cameraStates.update { map ->
            val state = map[id] ?: return@update map
            map + (id to transform(state))
        }
    }

    fun release() {
        disconnectAll()
        scope.cancel()
    }

    /** Update the device reference in camera state (e.g. after editing name/IP/port) */
    fun updateDeviceInState(id: String, device: CameraDevice) {
        updateCameraState(id) { it.copy(device = device) }
    }

    /** Update local state after power/camera switch (no polling needed) */
    fun updateDeviceState(id: String, isPoweredOn: Boolean? = null, currentCameraId: String? = null) {
        updateCameraState(id) {
            it.copy(
                isPoweredOn = isPoweredOn ?: it.isPoweredOn,
                currentCameraId = currentCameraId ?: it.currentCameraId
            )
        }
    }

    private data class CameraConnection(
        val device: CameraDevice,
        val apiClient: ApiClient,
        val mjpegClient: MjpegClient,
        val eventPoller: EventPoller
    )
}
