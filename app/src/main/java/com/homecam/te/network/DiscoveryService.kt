package com.homecam.te.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.homecam.te.HomeCamApp
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
    private var multicastLock: WifiManager.MulticastLock? = null

    companion object {
        private const val DISCOVER_PORT = 45678
        private const val COLLECT_DURATION_MS = 3000L
        private const val BROADCAST_ATTEMPTS = 3
        private const val BUFFER_SIZE = 1024
        private const val DISCOVER_MSG = "HOMECAM_DISCOVER"
        private const val RESPONSE_PREFIX = "HOMECAM_RESPONSE"
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        seenDevices.clear()

        // Acquire multicast lock for reliable UDP reception
        try {
            val wifi = HomeCamApp.instance.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("homecam-te-discovery")
            multicastLock?.setReferenceCounted(false)
            multicastLock?.acquire()
        } catch (e: Exception) {
            Log.w(tag, "Cannot acquire multicast lock", e)
        }

        scope.launch {
            try {
                broadcastDiscovery()
                listenForResponses()
            } catch (e: Exception) {
                Log.e(tag, "Discovery error", e)
            } finally {
                releaseMulticastLock()
                running.set(false)
                onDiscoveryComplete()
            }
        }
    }

    fun stop() {
        running.set(false)
        scope.cancel()
        releaseMulticastLock()
    }

    private fun broadcastDiscovery() {
        val broadcastAddr = InetAddress.getByName("255.255.255.255")
        val sendSocket = DatagramSocket()
        sendSocket.broadcast = true

        val sendData = DISCOVER_MSG.toByteArray(Charsets.UTF_8)
        repeat(BROADCAST_ATTEMPTS) {
            val packet = DatagramPacket(sendData, sendData.size, broadcastAddr, DISCOVER_PORT)
            sendSocket.send(packet)
            Thread.sleep(200)
        }
        sendSocket.close()
        Log.d(tag, "Broadcast discovery message ($BROADCAST_ATTEMPTS times)")
    }

    private fun listenForResponses() {
        val listenSocket = DatagramSocket(DISCOVER_PORT)
        listenSocket.soTimeout = COLLECT_DURATION_MS.toInt()
        listenSocket.broadcast = true

        val buffer = ByteArray(BUFFER_SIZE)
        val endTime = System.currentTimeMillis() + COLLECT_DURATION_MS

        Log.d(tag, "Listening for responses on port $DISCOVER_PORT...")

        while (System.currentTimeMillis() < endTime && running.get()) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                listenSocket.receive(packet)

                val response = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                Log.d(tag, "Received: $response from ${packet.address.hostAddress ?: "unknown"}")

                parseResponse(response, packet.address.hostAddress ?: "unknown")?.let { device ->
                    if (seenDevices.add(device.id)) {
                        Log.d(tag, "Discovered device: ${device.name} at ${device.ip}:${device.port}")
                        onDeviceFound(device)
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                break
            } catch (e: Exception) {
                Log.e(tag, "Listen error", e)
            }
        }

        listenSocket.close()
        Log.d(tag, "Discovery finished, ${seenDevices.size} device(s) found")
    }

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

    private fun releaseMulticastLock() {
        try {
            multicastLock?.release()
        } catch (_: Exception) { }
        multicastLock = null
    }

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
        } catch (_: Exception) { }
        return null
    }
}