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
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

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
        private const val BROADCAST_ATTEMPTS = 5
        private const val BUFFER_SIZE = 1024
        private const val DISCOVER_MSG = "HOMECAM_DISCOVER"
        private const val RESPONSE_PREFIX = "HOMECAM_RESPONSE"
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        seenDevices.clear()
        try {
            val wifi = HomeCamApp.instance.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("homecam-te-discovery")
            multicastLock?.setReferenceCounted(false)
            multicastLock?.acquire()
        } catch (e: Exception) {
            Log.w(tag, "Cannot acquire multicast lock", e)
        }
        val localIp = getLocalIpAddress()
        Log.d(tag, "Local IP: $localIp")
        scope.launch {
            try {
                broadcastAndListen(localIp)
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

    private fun broadcastAndListen(localIp: String?) {
        val socket = DatagramSocket(DISCOVER_PORT)
        try {
            socket.broadcast = true
            socket.soTimeout = COLLECT_DURATION_MS.toInt()
            val buffer = ByteArray(BUFFER_SIZE)
            val endTime = System.currentTimeMillis() + COLLECT_DURATION_MS
            val broadcastAddrs = mutableListOf<InetAddress>()
            broadcastAddrs.add(InetAddress.getByName("255.255.255.255"))
            val subnetBroadcast = computeSubnetBroadcast(localIp)
            if (subnetBroadcast != null && subnetBroadcast != "255.255.255.255") {
                broadcastAddrs.add(InetAddress.getByName(subnetBroadcast))
                Log.d(tag, "Also broadcasting to subnet: $subnetBroadcast")
            }
            val sendData = DISCOVER_MSG.toByteArray(Charsets.UTF_8)
            Log.d(tag, "Broadcasting discovery message (${BROADCAST_ATTEMPTS} times to ${broadcastAddrs.size} addr(s))")
            repeat(BROADCAST_ATTEMPTS) {
                for (addr in broadcastAddrs) {
                    try {
                        val packet = DatagramPacket(sendData, sendData.size, addr, DISCOVER_PORT)
                        socket.send(packet)
                    } catch (e: Exception) {
                        Log.w(tag, "Send to $addr failed", e)
                    }
                }
                if (it < BROADCAST_ATTEMPTS - 1) {
                    Thread.sleep(150)
                }
            }
            Log.d(tag, "Listening for responses on port $DISCOVER_PORT...")
            while (System.currentTimeMillis() < endTime && running.get()) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val fromAddr = packet.address.hostAddress ?: "unknown"
                    // Skip our own broadcast echo
                    if (fromAddr == localIp) continue
                    val fromPort = packet.port
                    val response = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    Log.d(tag, "Received: $response from ${fromAddr}:${fromPort}")
                    parseResponse(response, fromAddr)?.let { device ->
                        if (seenDevices.add(device.id)) {
                            Log.d(tag, "Discovered device: ${device.name} at ${device.ip}:${device.port}")
                            onDeviceFound(device)
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    break
                } catch (e: Exception) {
                    Log.e(tag, "Listen error", e)
                }
            }
            Log.d(tag, "Discovery finished, ${seenDevices.size} device(s) found")
        } finally {
            socket.close()
        }
    }

    private fun computeSubnetBroadcast(localIp: String?): String? {
        if (localIp == null) return null
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (nif.isLoopback || !nif.isUp) continue
                for (addr in nif.interfaceAddresses) {
                    if (addr.address is java.net.Inet4Address) {
                        val ip = addr.address.hostAddress
                        if (ip == localIp) {
                            val broadcast = addr.broadcast
                            if (broadcast != null) {
                                return broadcast.hostAddress
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to compute subnet broadcast", e)
        }
        return null
    }

    private fun parseResponse(response: String, sourceIp: String): CameraDevice? {
        val parts = response.trim().split("|")
        if (parts.size < 5 || parts[0] != RESPONSE_PREFIX) {
            Log.w(tag, "Ignored malformed response: $response")
            return null
        }
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
