package com.example.core.multiplayer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Handles automatic, zero-config local network discovery of Chill Games host sessions
 * via a dual-protocol approach:
 * 1. Android NsdManager (mDNS / DNS-SD) for standard Wi-Fi LANs.
 * 2. UDP Broadcast Beacons on port 8889 for Mobile Hotspots and ad-hoc networks where mDNS is filtered.
 */
class WifiLanDiscovery(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private var udpHostJob: Job? = null
    private var udpDiscoveryJob: Job? = null
    private var udpHostSocket: DatagramSocket? = null
    private var udpClientSocket: DatagramSocket? = null

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()
                // Prioritize wireless / hotspot interfaces (wlan, ap, rndis)
                if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("softap") || name.startsWith("rndis")) {
                    for (addr in intf.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            return addr.hostAddress ?: "127.0.0.1"
                        }
                    }
                }
            }
            // Fallback: any valid non-loopback IPv4
            val fallbackInterfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in fallbackInterfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve local IP", e)
        }
        return "127.0.0.1"
    }

    /**
     * Announces a hosted session across mDNS and UDP Broadcast.
     */
    fun startAnnouncing(
        session: DiscoveredSession,
        scope: CoroutineScope
    ) {
        stopAnnouncing()
        acquireMulticastLock()

        // 1. Register via NSD
        try {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "ChillGames_${session.sessionId.take(6)}"
                serviceType = SERVICE_TYPE
                port = session.port
                setAttribute("sid", session.sessionId)
                setAttribute("host", session.hostName)
                setAttribute("game", session.gameId)
                setAttribute("title", session.gameTitle)
                setAttribute("max", session.maxPlayers.toString())
            }

            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                    Log.d(TAG, "NSD Service registered: ${serviceInfo?.serviceName}")
                }
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.w(TAG, "NSD Registration failed: $errorCode")
                }
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                    Log.d(TAG, "NSD Service unregistered")
                }
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.w(TAG, "NSD Unregistration failed: $errorCode")
                }
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.w(TAG, "NSD registerService error: ${e.message}")
        }

        // 2. Start UDP Beacon Listener on Port 8889
        udpHostJob = scope.launch(Dispatchers.IO) {
            try {
                udpHostSocket = DatagramSocket(UDP_PORT).apply {
                    broadcast = true
                    reuseAddress = true
                }
                val buffer = ByteArray(1024)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        udpHostSocket?.receive(packet)
                    } catch (e: Exception) {
                        break
                    }
                    val message = String(packet.data, 0, packet.length).trim()
                    if (message.startsWith("CHILL_GAMES_QUERY")) {
                        // Reply with session payload
                        val replyJson = JSONObject().apply {
                            put("sessionId", session.sessionId)
                            put("hostName", session.hostName)
                            put("gameId", session.gameId)
                            put("gameTitle", session.gameTitle)
                            put("port", session.port)
                            put("currentPlayers", session.currentPlayers)
                            put("maxPlayers", session.maxPlayers)
                        }.toString()

                        val replyBytes = replyJson.toByteArray()
                        val replyPacket = DatagramPacket(
                            replyBytes,
                            replyBytes.size,
                            packet.address,
                            packet.port
                        )
                        udpHostSocket?.send(replyPacket)
                        Log.d(TAG, "Responded to UDP discovery query from ${packet.address.hostAddress}")
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException && isActive) {
                    Log.e(TAG, "UDP Host listener exception", e)
                }
            } finally {
                udpHostSocket?.close()
                udpHostSocket = null
            }
        }
    }

    fun stopAnnouncing() {
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering NSD service: ${e.message}")
        }
        registrationListener = null

        udpHostJob?.cancel()
        udpHostJob = null
        try {
            udpHostSocket?.close()
        } catch (ignored: Exception) {}
        udpHostSocket = null

        releaseMulticastLock()
    }

    /**
     * Discovers sessions via NSD and periodic UDP broadcast queries.
     */
    fun startDiscovery(
        scope: CoroutineScope,
        onSessionDiscovered: (DiscoveredSession) -> Unit
    ) {
        stopDiscovery()
        acquireMulticastLock()

        // 1. NSD Discovery
        try {
            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String?) {
                    Log.d(TAG, "NSD discovery started: $regType")
                }
                override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                    if (serviceInfo == null) return
                    Log.d(TAG, "NSD service found: ${serviceInfo.serviceName}")
                    try {
                        nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                                if (resolvedInfo == null) return
                                val host = resolvedInfo.host?.hostAddress ?: return
                                val port = resolvedInfo.port
                                val sid = resolvedInfo.attributes["sid"]?.let { String(it) } ?: "unknown"
                                val hostName = resolvedInfo.attributes["host"]?.let { String(it) } ?: "Host"
                                val gameId = resolvedInfo.attributes["game"]?.let { String(it) } ?: "chess"
                                val gameTitle = resolvedInfo.attributes["title"]?.let { String(it) } ?: "Game"
                                val max = resolvedInfo.attributes["max"]?.let { String(it).toIntOrNull() } ?: 2

                                val session = DiscoveredSession(
                                    sessionId = sid,
                                    hostName = hostName,
                                    gameId = gameId,
                                    gameTitle = gameTitle,
                                    hostIp = host,
                                    port = port,
                                    currentPlayers = 1,
                                    maxPlayers = max
                                )
                                onSessionDiscovered(session)
                            }
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                                Log.w(TAG, "NSD resolve failed: $errorCode")
                            }
                        })
                    } catch (e: Exception) {
                        Log.w(TAG, "NSD resolve call error: ${e.message}")
                    }
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                    Log.d(TAG, "NSD service lost: ${serviceInfo?.serviceName}")
                }
                override fun onDiscoveryStopped(serviceType: String?) {
                    Log.d(TAG, "NSD discovery stopped: $serviceType")
                }
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.w(TAG, "NSD start discovery failed: $errorCode")
                }
                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.w(TAG, "NSD stop discovery failed: $errorCode")
                }
            }

            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.w(TAG, "NSD discoverServices error: ${e.message}")
        }

        // 2. Periodic UDP Broadcast Discovery
        udpDiscoveryJob = scope.launch(Dispatchers.IO) {
            try {
                udpClientSocket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 2000
                }

                // Launch receiver loop
                launch {
                    val buffer = ByteArray(2048)
                    while (isActive) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            udpClientSocket?.receive(packet)
                            val jsonStr = String(packet.data, 0, packet.length).trim()
                            val json = JSONObject(jsonStr)
                            val session = DiscoveredSession(
                                sessionId = json.getString("sessionId"),
                                hostName = json.getString("hostName"),
                                gameId = json.getString("gameId"),
                                gameTitle = json.getString("gameTitle"),
                                hostIp = packet.address.hostAddress ?: "127.0.0.1",
                                port = json.getInt("port"),
                                currentPlayers = json.optInt("currentPlayers", 1),
                                maxPlayers = json.optInt("maxPlayers", 2)
                            )
                            onSessionDiscovered(session)
                            Log.d(TAG, "Discovered session via UDP from ${packet.address.hostAddress}")
                        } catch (e: Exception) {
                            // Timeout or socket closed, continue
                        }
                    }
                }

                // Broadcast query every 1.5 seconds
                val queryBytes = "CHILL_GAMES_QUERY".toByteArray()
                while (isActive) {
                    try {
                        val broadcastAddr = InetAddress.getByName("255.255.255.255")
                        val packet = DatagramPacket(queryBytes, queryBytes.size, broadcastAddr, UDP_PORT)
                        udpClientSocket?.send(packet)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to broadcast UDP query", e)
                    }
                    delay(1500)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException && isActive) {
                    Log.e(TAG, "UDP Client discovery exception", e)
                }
            } finally {
                udpClientSocket?.close()
                udpClientSocket = null
            }
        }
    }

    fun stopDiscovery() {
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping NSD discovery: ${e.message}")
        }
        discoveryListener = null

        udpDiscoveryJob?.cancel()
        udpDiscoveryJob = null
        try {
            udpClientSocket?.close()
        } catch (ignored: Exception) {}
        udpClientSocket = null

        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("ChillGamesMulticastLock")?.apply {
                    setReferenceCounted(true)
                    acquire()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire multicast lock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not release multicast lock: ${e.message}")
        }
        multicastLock = null
    }

    companion object {
        private const val TAG = "WifiLanDiscovery"
        private const val SERVICE_TYPE = "_chillgames._tcp."
        const val UDP_PORT = 8889
    }
}
