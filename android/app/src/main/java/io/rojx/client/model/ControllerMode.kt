package io.rojx.client.model

enum class ControllerMode(val typeByte: Byte, val displayName: String) {
    GAMEPAD(0x01.toByte(), "Xbox Gamepad"),
    PLAYSTATION(0x02.toByte(), "PlayStation"),
    STEERING_WHEEL(0x03.toByte(), "Logitech G29"),
    BUTTON_BOX(0x04.toByte(), "Truck Button Box")
}
