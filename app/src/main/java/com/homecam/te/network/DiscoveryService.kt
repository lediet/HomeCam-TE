package com.homecam.te.network

import android.util.Log
import com.homecam.te.model.CameraDevice
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UDP discovery service for finding HomeCam devices on the LAN.
 *
 * Broadcasts "HOMECAM_DISCOVER" on port 45678 and collects
 * "HOMECAM_RESPONSE|name|ip|port|id" responses for 3 seconds.
 */
class DiscoveryService(
    private val onDeviceFound: (CameraDevice) -> Unit,
    private val onDiscoveryComplete: () -> Unit = {}
) {

    private val tag = "DiscoveryService"
    private val running = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val seenDevices = HashSet<String>()

    companion object {
        private const val DISCOVER_PORT = 45678
        private const val COLLECT_DURATION_MS = 3000L
        private const val BROADCAST_ATTEMPTS = 3
        private const val BUFFER_SIZE = 1024
        private const val DISCOVER_MSG = "HOMECAM_DISCOVER"
        private const val RESPONSE_PREFIX = "HOMECAM_RESPONSE"
    }

    /** Start discovery: broadcast and collect responses */
    fun start() {
        if (!running.compareAndSet(false, true)) return
        seenDevices.clear()

        scope.launch {
            try {
                // Broadcast discovery message
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val sendSocket = DatagramSocket()
                sendSocket.broadcast = true

                val sendData = DISCOVER_MSG.toByteArray(Charsets.UTF_8)
                repeat(BROADCAST_ATTEMPTS) {
                    val packet = DatagramPacket(sendData, sendData.size, broadcastAddr, DISCOVER_PORT)
                    sendSocket.send(packet)
                    delay(200)
                }
                sendSocket.close()

                Log.d(tag, "Broadcasted discovery message ($BROADCAST_ATTEMPTS times)")

                // Listen for responses
                listenForResponses()

            } catch (e: Exception) {
                Log.e(tag, "Discovery error", e)
            } finally {
                running.set(false)
                onDiscoveryComplete()
            }
        }
    }

    private suspend fun listenForResponses() {
        // Find the local broadcast address to bind to
        val listenSocket = DatagramSocket(DISCOVER_PORT)
        listenSocket.soTimeout = COLLECT_DURATION_MS.toInt()
        listenSocket.broadcast = true

        val buffer = ByteArray(BUFFER_SIZE)
        val endTime = System.currentTimeMillis() + COLLECT_DURATION_MS

        while (System.currentTimeMillis() < endTime) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                listenSocket.receive(packet)

                val response = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                Log.d(tag, "Received: $response from ${packet.address.hostAddress}")

                parseResponse(response, packet.address.hostAddress)?.let { device ->
                    if (seenDevices.add(device.id)) {
                        onDeviceFound(device)
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                break // Collection period ended
            } catch (e: Exception) {
                Log.e(tag, "Listen error", e)
            }
        }

        listenSocket.close()
        Log.d(tag, "Discovery finished, ${seenDevices.size} device(s) found")
    }

    /**
     * Parse "HOMECAM_RESPONSE|name|ip|port|id" into CameraDevice
     */
    private fun parseResponse(response: String, sourceIp: String): CameraDevice? {
        val parts = response.trim().split("|")
        if (parts.size < 5 || parts[0] != RESPONSE_PREFIX) return null

        return CameraDevice(
            id = parts[4].trim(),
            name = parts[1].trim(),
            ip = parts[2].trim().ifEmpty { sourceIp },
            port = parts[3].trim().toIntOrNull() ?: 8080,
            isAutoDiscovered = true,
            lastSeen = System.currentTimeMillis()
        )
    }

    fun stop() {
        running.set(false)
        scope.cancel()
    }

    /** Helper: get local IP address (wifi interface preferred) */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (nif.isLoopback || !nif.isUp) continue
                val addresses = nif.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "getLocalIpAddress error", e)
        }
        return null
    }
}
