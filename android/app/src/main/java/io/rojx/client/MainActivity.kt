package io.rojx.client

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.*
import io.rojx.client.model.AppSettings
import io.rojx.client.model.ControllerMode
import io.rojx.client.network.UdpTransmitter
import io.rojx.client.sensor.SteeringSensorManager
import io.rojx.client.ui.screens.MainDashboard
import io.rojx.client.ui.theme.RojxDashboardTheme

class MainActivity : ComponentActivity() {

    private lateinit var settings: AppSettings
    private val udpTransmitter = UdpTransmitter()
    private lateinit var sensorManager: SteeringSensorManager
    private var vibrator: Vibrator? = null

    // UI State Holders
    private var wheelAngleState by mutableFloatStateOf(0f)
    private var throttleState by mutableFloatStateOf(0f)
    private var brakeState by mutableFloatStateOf(0f)
    private var activeModeState by mutableStateOf(ControllerMode.STEERING_WHEEL)
    private var buttonsBitmaskState by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while controlling PC
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Immersive Fullscreen (Hide Status & Navigation Bars)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Load settings from persistent storage
        settings = AppSettings.load(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        // Initialize Sensor Manager
        sensorManager = SteeringSensorManager(this) { deg, norm, axisShort ->
            wheelAngleState = deg
            if (activeModeState == ControllerMode.STEERING_WHEEL) {
                udpTransmitter.currentAxisLX = axisShort
            }
        }
        applySettingsToSensorsAndNetwork()

        // Hook up Bidirectional Force Feedback / Rumble Receiver
        udpTransmitter.onRumbleReceived = { slot, large, small, led ->
            if (settings.hapticFeedback) {
                triggerHapticRumble(large, small)
            }
        }

        // Start UDP Transmitter
        udpTransmitter.start(
            ip = settings.targetIp,
            port = settings.targetPort,
            slot = settings.playerSlot,
            rateHz = settings.packetRateHz
        )

        setContent {
            RojxDashboardTheme {
                MainDashboard(
                    settings = settings,
                    wheelAngle = wheelAngleState,
                    throttle = throttleState,
                    brake = brakeState,
                    activeMode = activeModeState,
                    onModeChange = { mode ->
                        activeModeState = mode
                        udpTransmitter.currentControllerType = mode
                        if (mode == ControllerMode.STEERING_WHEEL) {
                            sensorManager.start()
                        } else {
                            sensorManager.stop()
                            udpTransmitter.currentAxisLX = 0
                        }
                    },
                    onThrottleChange = {
                        throttleState = it
                        udpTransmitter.currentTriggerR = (it * 255f).toInt().coerceIn(0, 255)
                        udpTransmitter.sendImmediate()
                    },
                    onBrakeChange = {
                        brakeState = it
                        udpTransmitter.currentTriggerL = (it * 255f).toInt().coerceIn(0, 255)
                        udpTransmitter.sendImmediate()
                    },
                    onLeftStickChange = { x, y ->
                        if (activeModeState == ControllerMode.GAMEPAD || activeModeState == ControllerMode.PLAYSTATION) {
                            udpTransmitter.currentAxisLX = (x * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                            udpTransmitter.currentAxisLY = (y * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                            udpTransmitter.sendImmediate()
                        }
                    },
                    onRightStickChange = { x, y ->
                        udpTransmitter.currentAxisRX = (x * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                        udpTransmitter.currentAxisRY = (y * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                        udpTransmitter.sendImmediate()
                    },
                    onButtonEvent = { bitmask, isPressed ->
                        buttonsBitmaskState = if (isPressed) {
                            buttonsBitmaskState or bitmask
                        } else {
                            buttonsBitmaskState and bitmask.inv()
                        }
                        udpTransmitter.currentButtonsBitmask0 = buttonsBitmaskState
                        udpTransmitter.sendImmediate()
                    },
                    onCenterWheelClick = {
                        sensorManager.calibrateCenter()
                    },
                    onSaveSettings = { updated ->
                        settings = updated
                        settings.save(this)
                        applySettingsToSensorsAndNetwork()
                    }
                )
            }
        }
    }

    private fun triggerHapticRumble(large: Int, small: Int) {
        val maxAmp = maxOf(large, small)
        if (maxAmp <= 0) return
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = ((maxAmp.toFloat() / 255f) * 255f).toInt().coerceIn(1, 255)
                vib.vibrate(VibrationEffect.createOneShot(100, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(100)
            }
        } catch (e: Exception) { }
    }

    private fun applySettingsToSensorsAndNetwork() {
        sensorManager.maxWheelAngle = settings.maxWheelDegrees
        sensorManager.invertSteering = settings.invertSteering
        sensorManager.sensitivity = settings.steeringSensitivity
        sensorManager.deadzone = settings.steeringDeadzone

        udpTransmitter.updateConfig(
            ip = settings.targetIp,
            port = settings.targetPort,
            slot = settings.playerSlot,
            rateHz = settings.packetRateHz
        )
    }

    override fun onResume() {
        super.onResume()
        if (activeModeState == ControllerMode.STEERING_WHEEL) {
            sensorManager.start()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.stop()
        udpTransmitter.stop()
    }
}
