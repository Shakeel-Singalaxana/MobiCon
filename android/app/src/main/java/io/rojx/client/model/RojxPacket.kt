package io.rojx.client.model

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

data class RojxPacket(
    val sequenceNumber: Long,
    val slot: Int = 0, // 0..3 (P1..P4)
    val controllerType: ControllerMode,
    val buttonsBitmask0: Int,
    val buttonsBitmask1: Int = 0,
    val buttonsBitmask2: Int = 0,
    val buttonsBitmask3: Int = 0,
    val axisLeftX: Short,
    val axisLeftY: Short = 0,
    val axisRightX: Short = 0,
    val axisRightY: Short = 0,
    val triggerLeft: Int = 0,  // 0 to 255 (Brake)
    val triggerRight: Int = 0  // 0 to 255 (Throttle)
) {
    fun toByteArray(): ByteArray {
        val baos = ByteArrayOutputStream(33)
        val dos = DataOutputStream(baos)

        // 1. Header (2 bytes: 'R', 'O')
        dos.writeByte(0x52)
        dos.writeByte(0x4F)

        // 2. Sequence Number (4 bytes uint32)
        dos.writeInt(sequenceNumber.toInt())

        // 3. Controller Type & Slot (1 byte: Upper 4 bits = Slot 0..3, Lower 4 bits = Mode)
        val combinedType = ((slot and 0x0F) shl 4) or (controllerType.typeByte.toInt() and 0x0F)
        dos.writeByte(combinedType)

        // 4. Buttons Bitmask (16 bytes: 128-bit mask, high-order words default to zero)
        dos.writeInt(buttonsBitmask0)
        dos.writeInt(buttonsBitmask1)
        dos.writeInt(buttonsBitmask2)
        dos.writeInt(buttonsBitmask3)

        // 5. Analog Axes (4 x 2 bytes short)
        dos.writeShort(axisLeftX.toInt())
        dos.writeShort(axisLeftY.toInt())
        dos.writeShort(axisRightX.toInt())
        dos.writeShort(axisRightY.toInt())

        // 6. Triggers (2 x 1 byte: Left / Brake, Right / Throttle)
        dos.writeByte(triggerLeft.coerceIn(0, 255))
        dos.writeByte(triggerRight.coerceIn(0, 255))

        dos.flush()
        return baos.toByteArray()
    }
}
