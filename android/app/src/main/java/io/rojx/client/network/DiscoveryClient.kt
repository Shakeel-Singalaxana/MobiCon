package io.rojx.client.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets

data class DiscoveredServer(
    val ip: String,
    val hostname: String,
    val udpPort: Int = 7777,
    val httpPort: Int = 8080,
    val isDriverReady: Boolean = true
)

object DiscoveryClient {

    suspend fun scanForServers(timeoutMs: Int = 2000): List<DiscoveredServer> = withContext(Dispatchers.IO) {
        val servers = mutableListOf<DiscoveredServer>()
        var socket: DatagramSocket? = null

        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 400
            }

            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val portsToScan = listOf(7777, 8080)

            // Broadcast discovery requests across both 7777 and 8080
            for (port in portsToScan) {
                val reqRojx = "ROJX_DISCOVER".toByteArray(StandardCharsets.UTF_8)
                val reqPadlink = "PADLINK_DISCOVER".toByteArray(StandardCharsets.UTF_8)
                socket.send(DatagramPacket(reqRojx, reqRojx.size, broadcastAddr, port))
                socket.send(DatagramPacket(reqPadlink, reqPadlink.size, broadcastAddr, port))
            }

            val receiveBuffer = ByteArray(2048)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

            val endTime = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < endTime) {
                try {
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length, StandardCharsets.UTF_8).trim()

                    if (response.startsWith("ROJX_SERVER") || response.startsWith("PADLINK_HERE")) {
                        val parts = response.split("|")
                        val hostname = parts.getOrNull(1) ?: "PC Receiver"
                        
                        // Parse UDP & HTTP ports flexibly
                        var parsedUdpPort = 7777
                        var parsedHttpPort = 8080
                        var isReady = true

                        if (parts.size >= 5) {
                            // ROJX_SERVER|hostname|udpPort|httpPort|status
                            parsedUdpPort = parts[2].toIntOrNull() ?: 7777
                            parsedHttpPort = parts[3].toIntOrNull() ?: 8080
                            isReady = parts[4].equals("READY", ignoreCase = true)
                        } else if (parts.size == 4) {
                            // ROJX_SERVER|hostname|udpPort|status  OR  PADLINK_HERE|hostname|udpPort|httpPort
                            if (parts[3].equals("READY", true) || parts[3].equals("NO_DRIVER", true)) {
                                parsedUdpPort = parts[2].toIntOrNull() ?: 7777
                                isReady = parts[3].equals("READY", true)
                            } else {
                                parsedUdpPort = parts[2].toIntOrNull() ?: 7777
                                parsedHttpPort = parts[3].toIntOrNull() ?: 8080
                            }
                        } else if (parts.size == 3) {
                            parsedUdpPort = parts[2].toIntOrNull() ?: 7777
                        }

                        val senderIp = receivePacket.address.hostAddress ?: ""
                        if (senderIp.isNotEmpty() && servers.none { it.ip == senderIp }) {
                            servers.add(
                                DiscoveredServer(
                                    ip = senderIp,
                                    hostname = hostname,
                                    udpPort = parsedUdpPort,
                                    httpPort = parsedHttpPort,
                                    isDriverReady = isReady
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Socket timeout or finished
                    if (System.currentTimeMillis() >= endTime) break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket?.close()
        }

        servers
    }
}
