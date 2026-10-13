package io.rojx.client.model

object ButtonBits {
    const val A = 1 shl 0
    const val B = 1 shl 1
    const val X = 1 shl 2
    const val Y = 1 shl 3
    const val LB = 1 shl 4
    const val RB = 1 shl 5
    const val BACK = 1 shl 6
    const val START = 1 shl 7
    const val LS = 1 shl 8
    const val RS = 1 shl 9
    const val DPAD_UP = 1 shl 10
    const val DPAD_DOWN = 1 shl 11
    const val DPAD_LEFT = 1 shl 12
    const val DPAD_RIGHT = 1 shl 13
    const val GUIDE = 1 shl 14

    // Simulator, Trucking Cockpit & Logitech G29 Controls
    const val IGNITION = 1 shl 15
    const val HEADLIGHTS = 1 shl 16
    const val HAZARD_LIGHTS = 1 shl 17
    const val HORN = 1 shl 18
    const val HANDBRAKE = 1 shl 19
    const val SHIFT_UP = 1 shl 20
    const val SHIFT_DOWN = 1 shl 21
    const val GEAR1 = 1 shl 22
    const val GEAR2 = 1 shl 23
    const val GEAR3 = 1 shl 24
    const val GEAR4 = 1 shl 25
    const val GEAR5 = 1 shl 26
    const val GEAR6 = 1 shl 27
    const val GEAR_REV = 1 shl 28
    const val DIFF_LOCK = 1 shl 29
    const val TRAILER = 1 shl 30
    const val CRUISE_CONTROL = 1 shl 31

    // Aliases
    const val WIPERS = 1 shl 17
    const val LIFT_AXLE = 1 shl 29
    const val HIGH_BEAM = 1 shl 16
    const val BEACON = 1 shl 17
    const val TURN_LEFT = 1 shl 12
    const val TURN_RIGHT = 1 shl 13
    const val CAMERA = 1 shl 6
    const val NITRO = 1 shl 0
}
