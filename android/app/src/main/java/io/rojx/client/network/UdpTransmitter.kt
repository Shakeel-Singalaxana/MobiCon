package io.rojx.client.network

import io.rojx.client.model.ControllerMode
import io.rojx.client.model.RojxPacket
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class UdpTransmitter {

    private var socket: DatagramSocket? = null
    private var scope: CoroutineScope? = null
    private var transmissionJob: Job? = null
    private var receiverJob: Job? = null

    private val isRunning = AtomicBoolean(false)
    private val sequenceNumber = AtomicLong(1)

    // Current State Holders
    private val targetIp = AtomicReference("192.168.1.100")
    private val targetPort = AtomicInteger(7777)
    private val packetRateHz = AtomicInteger(120)
    val playerSlot = AtomicInteger(0)

    // Axis and Button States
    @Volatile var currentControllerType = ControllerMode.STEERING_WHEEL
    @Volatile var currentButtonsBitmask0: Int = 0
    @Volatile var currentButtonsBitmask1: Int = 0
    @Volatile var currentButtonsBitmask2: Int = 0
    @Volatile var currentButtonsBitmask3: Int = 0
    @Volatile var currentAxisLX: Short = 0
    @Volatile var currentAxisLY: Short = 0
    @Volatile var currentAxisRX: Short = 0
    @Volatile var currentAxisRY: Short = 0
    @Volatile var currentTriggerL: Int = 0 // Brake (0..255)
    @Volatile var currentTriggerR: Int = 0 // Throttle (0..255)

    // Status & Feedback Listeners
    var onTransmissionError: ((String) -> Unit)? = null
    var onPacketSent: ((seq: Long) -> Unit)? = null
    var onRumbleReceived: ((slot: Int, large: Int, small: Int, led: Int) -> Unit)? = null

    fun start(ip: String, port: Int = 7777, slot: Int = 0, rateHz: Int = 120) {
        stop()

        targetIp.set(ip)
        targetPort.set(port)
        playerSlot.set(slot)
        packetRateHz.set(rateHz)

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        isRunning.set(true)

        try {
            socket = DatagramSocket().apply {
                sendBufferSize = 65536
                receiveBufferSize = 65536
                trafficClass = 0x10 // IPTOS_LOWDELAY
            }
        } catch (e: Exception) {
            onTransmissionError?.invoke("Socket creation failed: ${e.message}")
            return
        }

        // 1. Transmission loop
        transmissionJob = scope?.launch {
            val intervalMs = (1000L / packetRateHz.get().coerceIn(30, 144)).coerceAtLeast(6L)

            while (isActive && isRunning.get()) {
                val startTime = System.currentTimeMillis()
                sendCurrentStatePacket()

                val elapsed = System.currentTimeMillis() - startTime
                val sleepTime = intervalMs - elapsed
                if (sleepTime > 0) {
                    delay(sleepTime)
                }
            }
        }

        // 2. Incoming Force Feedback / Rumble Receiver loop
        receiverJob = scope?.launch {
            val buf = ByteArray(64)
            val packet = DatagramPacket(buf, buf.size)
            while (isActive && isRunning.get()) {
                try {
                    val sock = socket ?: break
                    sock.receive(packet)
                    val data = packet.data
                    val len = packet.length

                    // Check for Rojx Feedback 'R', 'F' (0x52, 0x46)
                    if (len >= 6 && data[0] == 0x52.toByte() && data[1] == 0x46.toByte()) {
                        val recvSlot = data[2].toInt() and 0xFF
                        val large = data[3].toInt() and 0xFF
                        val small = data[4].toInt() and 0xFF
                        val led = data[5].toInt() and 0xFF

                        if (recvSlot == playerSlot.get()) {
                            onRumbleReceived?.invoke(recvSlot, large, small, led)
                        }
                    }
                } catch (e: Exception) {
                    if (!isRunning.get()) break
                }
            }
        }
    }

    fun updateConfig(ip: String, port: Int, slot: Int, rateHz: Int) {
        targetIp.set(ip)
        targetPort.set(port)
        playerSlot.set(slot)
        packetRateHz.set(rateHz)
    }

    fun sendImmediate() {
        if (!isRunning.get()) return
        scope?.launch(Dispatchers.IO) {
            sendCurrentStatePacket()
        }
    }

    private fun sendCurrentStatePacket() {
        try {
            val sock = socket ?: return
            val ipStr = targetIp.get() ?: return
            val port = targetPort.get()

            val packet = RojxPacket(
                sequenceNumber = sequenceNumber.getAndIncrement(),
                slot = playerSlot.get(),
                controllerType = currentControllerType,
                buttonsBitmask0 = currentButtonsBitmask0,
                buttonsBitmask1 = currentButtonsBitmask1,
                buttonsBitmask2 = currentButtonsBitmask2,
                buttonsBitmask3 = currentButtonsBitmask3,
                axisLeftX = currentAxisLX,
                axisLeftY = currentAxisLY,
                axisRightX = currentAxisRX,
                axisRightY = currentAxisRY,
                triggerLeft = currentTriggerL,
                triggerRight = currentTriggerR
            )

            val bytes = packet.toByteArray()
            val address = InetAddress.getByName(ipStr)
            val datagramPacket = DatagramPacket(bytes, bytes.size, address, port)

            sock.send(datagramPacket)
            onPacketSent?.invoke(packet.sequenceNumber)
        } catch (e: Exception) {
            onTransmissionError?.invoke("Send error: ${e.message}")
        }
    }

    fun stop() {
        isRunning.set(false)
        transmissionJob?.cancel()
        transmissionJob = null
        receiverJob?.cancel()
        receiverJob = null
        socket?.close()
        socket = null
        scope?.cancel()
        scope = null
    }
}
