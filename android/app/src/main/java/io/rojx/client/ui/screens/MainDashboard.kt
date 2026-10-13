package io.rojx.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.rojx.client.model.AppSettings
import io.rojx.client.model.ControllerMode
import io.rojx.client.ui.components.TopConnectionBar
import io.rojx.client.ui.theme.ClayBg

@Composable
fun MainDashboard(
    settings: AppSettings,
    wheelAngle: Float,
    throttle: Float,
    brake: Float,
    activeMode: ControllerMode,
    onModeChange: (ControllerMode) -> Unit,
    onThrottleChange: (Float) -> Unit,
    onBrakeChange: (Float) -> Unit,
    onLeftStickChange: (Float, Float) -> Unit,
    onRightStickChange: (Float, Float) -> Unit,
    onButtonEvent: (bitmask: Int, isPressed: Boolean) -> Unit,
    onCenterWheelClick: () -> Unit,
    onSaveSettings: (AppSettings) -> Unit
) {
    var showSettingsModal by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ClayBg
    ) {
        if (showSettingsModal) {
            SettingsScreen(
                currentSettings = settings,
                onSaveSettings = {
                    onSaveSettings(it)
                    showSettingsModal = false
                },
                onClose = { showSettingsModal = false }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                // Active Mode Screen Content (Full Screen)
                Box(modifier = Modifier.fillMaxSize()) {
                    when (activeMode) {
                        ControllerMode.STEERING_WHEEL -> {
                            RacingWheelScreen(
                                wheelAngle = wheelAngle,
                                throttle = throttle,
                                brake = brake,
                                onThrottleChange = onThrottleChange,
                                onBrakeChange = onBrakeChange,
                                onButtonEvent = onButtonEvent
                            )
                        }
                        ControllerMode.BUTTON_BOX -> {
                            TruckButtonBoxScreen(
                                onButtonEvent = onButtonEvent
                            )
                        }
                        ControllerMode.PLAYSTATION -> {
                            GamepadScreen(
                                isPlaystation = true,
                                triggerLeft = brake,
                                triggerRight = throttle,
                                onTriggerLeftChange = onBrakeChange,
                                onTriggerRightChange = onThrottleChange,
                                onLeftStickChange = onLeftStickChange,
                                onRightStickChange = onRightStickChange,
                                onButtonEvent = onButtonEvent
                            )
                        }
                        ControllerMode.GAMEPAD -> {
                            GamepadScreen(
                                isPlaystation = false,
                                triggerLeft = brake,
                                triggerRight = throttle,
                                onTriggerLeftChange = onBrakeChange,
                                onTriggerRightChange = onThrottleChange,
                                onLeftStickChange = onLeftStickChange,
                                onRightStickChange = onRightStickChange,
                                onButtonEvent = onButtonEvent
                            )
                        }
                    }
                }

                // Autohiding Top Connection Bar with Centered Tiny Island
                TopConnectionBar(
                    targetIp = settings.targetIp,
                    targetPort = settings.targetPort,
                    playerSlot = settings.playerSlot,
                    packetRateHz = settings.packetRateHz,
                    activeMode = activeMode,
                    onModeChange = onModeChange,
                    onCenterWheelClick = onCenterWheelClick,
                    onSettingsClick = { showSettingsModal = true },
                    enableHaptics = settings.hapticFeedback,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}
