package com.homecam.te.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homecam.te.data.CameraRepository
import com.homecam.te.model.*
import com.homecam.te.network.ApiClient
import com.homecam.te.network.DiscoveryService
import com.homecam.te.service.AlertManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Central ViewModel for the Homecam-TE app.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = CameraRepository()
    val alertManager = AlertManager(application)

    /** Observable camera states from repository */
    val cameraStates: StateFlow<Map<String, CameraState>> = repository.camerasFlow

    /** Saved device list loaded from Room */
    val savedDevices = repository.getSavedDevices().stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        emptyList()
    )

    /** Discovery state */
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()
    private var activeDiscovery: DiscoveryService? = null

    /** Selected device for fullscreen or control */
    private val _fullscreenDeviceId = MutableStateFlow<String?>(null)
    val fullscreenDeviceId: StateFlow<String?> = _fullscreenDeviceId.asStateFlow()

    /** Event sheet state */
    private val _sheetDeviceId = MutableStateFlow<String?>(null)
    val sheetDeviceId: StateFlow<String?> = _sheetDeviceId.asStateFlow()

    /** Alert settings (mirrored in AlertManager) */
    private val prefs = application.getSharedPreferences("homecam_te_prefs", Context.MODE_PRIVATE)
    private val _alertSettings = MutableStateFlow(loadAlertSettings())
    val alertSettings: StateFlow<AlertSettings> = _alertSettings.asStateFlow()

    private fun loadAlertSettings(): AlertSettings {
        return AlertSettings(
            enabled = prefs.getBoolean("alert_enabled", true),
            vibrate = prefs.getBoolean("alert_vibrate", true),
            voice = prefs.getBoolean("alert_voice", true),
            enterAlert = prefs.getBoolean("alert_enter", true),
            leaveAlert = prefs.getBoolean("alert_leave", true),
            cryAlert = prefs.getBoolean("alert_cry", true),
            sleepAlert = prefs.getBoolean("alert_sleep", true)
        )
    }

    /** Snackbar message */
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    /** Show add dialog */
    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    init {
        // Load saved devices on startup
        viewModelScope.launch {
            savedDevices.collect { devices ->
                devices.forEach { entity ->
                    val device = CameraDevice(
                        id = entity.id,
                        name = entity.name,
                        ip = entity.ip,
                        port = entity.port,
                        isAutoDiscovered = entity.isAutoDiscovered,
                        lastSeen = entity.lastSeen
                    )
                    repository.connectDevice(device)
                }
            }
        }

        // New events trigger alerts (dedup: skip events already alerted before restart)
        viewModelScope.launch {
            cameraStates.collect { states ->
                states.forEach { (deviceId, state) ->
                    state.latestEvent?.let { eventType ->
                        val lastTime = prefs.getLong("alert_last_time_$deviceId", 0L)
                        if (state.latestEventTime > lastTime) {
                            alertManager.onEvent(eventType, state.latestEventLabel)
                            prefs.edit().putLong("alert_last_time_$deviceId", state.latestEventTime).apply()
                        }
                    }
                }
            }
        }
    }

    // ----- Device Management -----

    fun startDiscovery() {
        if (_isDiscovering.value) return
        _isDiscovering.value = true

        var discoveredCount = 0

        activeDiscovery?.stop()
        activeDiscovery = DiscoveryService(
            onDeviceFound = { device ->
                discoveredCount++
                viewModelScope.launch {
                    repository.saveDevice(device)
                    repository.connectDevice(device)
                }
            },
            onDiscoveryComplete = {
                _isDiscovering.value = false
                activeDiscovery = null
                if (discoveredCount == 0) {
                    _snackbarMessage.value = "未发现设备，请检查网络或手动添加"
                } else {
                    _snackbarMessage.value = "发现 $discoveredCount 台设备"
                }
            }
        )
        activeDiscovery?.start()
    }

    fun showAddDialog() {
        _showAddDialog.value = true
    }

    fun hideAddDialog() {
        _showAddDialog.value = false
    }

    fun addManualDevice(ip: String, port: String, name: String) {
        val portInt = port.toIntOrNull() ?: 8080
        val deviceId = "manual_${ip}_$portInt"
        val device = CameraDevice(
            id = deviceId,
            name = name.ifEmpty { ip },
            ip = ip.trim(),
            port = portInt,
            isAutoDiscovered = false,
            lastSeen = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.saveDevice(device)
            repository.connectDevice(device)
            _showAddDialog.value = false
        }
    }

    fun removeDevice(id: String) {
        viewModelScope.launch {
            repository.removeDevice(id)
        }
    }

    fun editDevice(oldId: String, ip: String, port: String, name: String) {
        viewModelScope.launch {
            val portInt = port.toIntOrNull() ?: 8080
            val newId = "manual_${ip.trim()}_$portInt"
            val oldState = cameraStates.value[oldId]

            val device = CameraDevice(
                id = newId,
                name = name.ifEmpty { ip.trim() },
                ip = ip.trim(),
                port = portInt,
                isAutoDiscovered = oldState?.device?.isAutoDiscovered ?: false,
                lastSeen = System.currentTimeMillis()
            )

            if (oldId != newId) {
                // IP/port changed → remove old connection, add new
                repository.removeDevice(oldId)
                repository.saveDevice(device)
                repository.connectDevice(device)
            } else {
                // Same ID, just update name
                repository.saveDevice(device)
                repository.updateDeviceInState(oldId, device)
            }
        }
    }

    // ----- Fullscreen -----

    fun setFullscreen(deviceId: String?) {
        _fullscreenDeviceId.value = deviceId
    }

    // ----- Event Sheet -----

    fun showEventSheet(deviceId: String) {
        _sheetDeviceId.value = deviceId
    }

    fun hideEventSheet() {
        _sheetDeviceId.value = null
    }

    // ----- Camera Controls -----

    fun setPower(deviceId: String, on: Boolean) {
        viewModelScope.launch {
            val client = repository.getApiClient(deviceId)
            client?.setPower(on)?.onSuccess {
                repository.updateDeviceState(deviceId, isPoweredOn = on)
            }
            if (on) {
                // Restart MJPEG stream so video resumes after power-on
                delay(300)
                repository.restartMjpeg(deviceId)
            }
        }
    }

    fun switchCamera(deviceId: String, cameraId: String, logicalCameraId: String) {
        viewModelScope.launch {
            val client = repository.getApiClient(deviceId)
            client?.switchCamera(cameraId, logicalCameraId)?.onSuccess {
                repository.updateDeviceState(deviceId, currentCameraId = cameraId)
            }
        }
    }

    // ----- Alert Settings -----

    fun updateAlertSettings(settings: AlertSettings) {
        _alertSettings.value = settings
        alertManager.alertEnabled = settings.enabled
        alertManager.vibrateEnabled = settings.vibrate
        alertManager.voiceEnabled = settings.voice
        alertManager.enterAlertEnabled = settings.enterAlert
        alertManager.leaveAlertEnabled = settings.leaveAlert
        alertManager.cryAlertEnabled = settings.cryAlert
        alertManager.sleepAlertEnabled = settings.sleepAlert
        // Persist to SharedPreferences
        prefs.edit()
            .putBoolean("alert_enabled", settings.enabled)
            .putBoolean("alert_vibrate", settings.vibrate)
            .putBoolean("alert_voice", settings.voice)
            .putBoolean("alert_enter", settings.enterAlert)
            .putBoolean("alert_leave", settings.leaveAlert)
            .putBoolean("alert_cry", settings.cryAlert)
            .putBoolean("alert_sleep", settings.sleepAlert)
            .apply()
    }

    // ----- Snackbar -----

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        repository.release()
        alertManager.release()
    }
}

data class AlertSettings(
    val enabled: Boolean = true,
    val vibrate: Boolean = true,
    val voice: Boolean = true,
    val enterAlert: Boolean = true,
    val leaveAlert: Boolean = true,
    val cryAlert: Boolean = true,
    val sleepAlert: Boolean = true
)
