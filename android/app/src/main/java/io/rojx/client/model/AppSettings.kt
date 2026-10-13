package io.rojx.client.model

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    var targetIp: String = "192.168.1.100",
    var targetPort: Int = 7777,
    var playerSlot: Int = 0, // 0=P1, 1=P2, 2=P3, 3=P4
    var packetRateHz: Int = 120, // 60, 90, 120
    var maxWheelDegrees: Float = 900f, // 180, 360, 540, 900, 1080
    var invertSteering: Boolean = false,
    var steeringSensitivity: Float = 1.0f,
    var steeringDeadzone: Float = 0.02f,
    var hapticFeedback: Boolean = true,
    var autoCenterSpring: Boolean = true
) {
    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("targetIp", targetIp)
            putInt("targetPort", targetPort)
            putInt("playerSlot", playerSlot)
            putInt("packetRateHz", packetRateHz)
            putFloat("maxWheelDegrees", maxWheelDegrees)
            putBoolean("invertSteering", invertSteering)
            putFloat("steeringSensitivity", steeringSensitivity)
            putFloat("steeringDeadzone", steeringDeadzone)
            putBoolean("hapticFeedback", hapticFeedback)
            putBoolean("autoCenterSpring", autoCenterSpring)
            apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "rojx_settings"

        fun load(context: Context): AppSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AppSettings(
                targetIp = prefs.getString("targetIp", "192.168.1.100") ?: "192.168.1.100",
                targetPort = prefs.getInt("targetPort", 7777),
                playerSlot = prefs.getInt("playerSlot", 0),
                packetRateHz = prefs.getInt("packetRateHz", 120),
                maxWheelDegrees = prefs.getFloat("maxWheelDegrees", 900f),
                invertSteering = prefs.getBoolean("invertSteering", false),
                steeringSensitivity = prefs.getFloat("steeringSensitivity", 1.0f),
                steeringDeadzone = prefs.getFloat("steeringDeadzone", 0.02f),
                hapticFeedback = prefs.getBoolean("hapticFeedback", true),
                autoCenterSpring = prefs.getBoolean("autoCenterSpring", true)
            )
        }
    }
}
