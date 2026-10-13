package io.rojx.client.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.rojx.client.model.ButtonBits
import io.rojx.client.ui.components.CockpitButton
import io.rojx.client.ui.theme.AmberWarning
import io.rojx.client.ui.theme.NeonCyan
import io.rojx.client.ui.theme.RacingGreen
import io.rojx.client.ui.theme.RacingRed

@Composable
fun TruckButtonBoxScreen(
    onButtonEvent: (bitmask: Int, isPressed: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Toggle States
    var isEngineOn by remember { mutableStateOf(false) }
    var isParkBrakeOn by remember { mutableStateOf(false) }
    var isLowBeamOn by remember { mutableStateOf(false) }
    var isHighBeamOn by remember { mutableStateOf(false) }
    var isBeaconOn by remember { mutableStateOf(false) }
    var isHazardsOn by remember { mutableStateOf(false) }
    var isWipersOn by remember { mutableStateOf(false) }
    var isDiffLockOn by remember { mutableStateOf(false) }
    var isAxleLiftOn by remember { mutableStateOf(false) }
    var isCruiseOn by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ROW 1: PRIMARY SYSTEMS (Engine, Park Brake, Hazards, Horn)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CockpitButton(
                label = "ENGINE",
                subLabel = if (isEngineOn) "RUNNING" else "STOPPED",
                activeColor = RacingRed,
                isToggle = true,
                isActive = isEngineOn,
                onStateChange = {
                    isEngineOn = it
                    onButtonEvent(ButtonBits.IGNITION, it)
                },
                modifier = Modifier.weight(1f),
                minHeight = 65.dp
            )

            CockpitButton(
                label = "PARK BRAKE",
                subLabel = if (isParkBrakeOn) "ENGAGED" else "RELEASED",
                activeColor = RacingRed,
                isToggle = true,
                isActive = isParkBrakeOn,
                onStateChange = {
                    isParkBrakeOn = it
                    onButtonEvent(ButtonBits.HANDBRAKE, it)
                },
                modifier = Modifier.weight(1f),
                minHeight = 65.dp
            )

            CockpitButton(
                label = "HAZARDS",
                subLabel = "EMERGENCY",
                activeColor = AmberWarning,
                isToggle = true,
                isActive = isHazardsOn,
                onStateChange = {
                    isHazardsOn = it
                    onButtonEvent(ButtonBits.HAZARD_LIGHTS, it)
                },
                modifier = Modifier.weight(1f),
                minHeight = 65.dp
            )

            CockpitButton(
                label = "AIR HORN",
                subLabel = "HOLD",
                activeColor = NeonCyan,
                onStateChange = { onButtonEvent(ButtonBits.HORN, it) },
                modifier = Modifier.weight(1f),
                minHeight = 65.dp
            )
        }

        // ROW 2: LIGHTING CONTROLS (Low Beam, High Beam, Beacon, Turn Signals)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CockpitButton(
                label = "HEADLIGHTS",
                subLabel = "LOW BEAM",
                activeColor = NeonCyan,
                isToggle = true,
                isActive = isLowBeamOn,
                onStateChange = {
                    isLowBeamOn = it
                    onButtonEvent(ButtonBits.HEADLIGHTS, it)
                },
                modifier = Modifier.weight(1f),
                minHeight = 65.dp
            )

            CockpitButton(
                label = "HIGH BEAM",
                subLabel = "MAIN",
                activeColor = Color(0xFF3B82F6),
                isToggle = true,
                isActive = isHighBeamOn,
                onStateChange = {
                    isHighBeamOn = it
                    onButtonEvent(ButtonBits.HIGH_BEAM, it)
                },
                modifier = Modifier.weight(1f),
                minHeight = 65.dp
            )

            CockpitButton(
                label = "BEACON",
                subLabel = "STROBE",
                activeColor = AmberWarning,
                isToggle = true,
                isActive = isBeaconOn,
                onStateChange = {
                    isBeaconOn = it
                    onButtonEvent(ButtonBits.BEACON, it)
                },
                modifier = Modifier.weight(1f),
                minHeight = 65.dp
            )

            CockpitButton(
                label = "◄ SIGNAL L",
                activeColor = RacingGreen,
                onStateChange = { onButtonEvent(ButtonBits.TURN_LEFT, it) },
                modifier = Modifier.weight(1f),
                minHeight = 65.dp
            )

            CockpitButton(
                label = "SIGNAL R ►",
                activeColor = RacingGreen,
                onStateChange = { onButtonEvent(ButtonBits.TURN_RIGHT, it) },
                modifier = Modifier.weight(1f),
                minHeight = 65.dp
            )
        }

        // ROW 3: DRIVETRAIN & CRUISE (Diff Lock, Axle Lift, Wipers, Cruise, Camera)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CockpitButton(
                label = "DIFF LOCK",
                subLabel = "AXLE",
                activeColor = AmberWarning,
                isToggle = true,
                isActive = isDiffLockOn,
                onStateChange = {
                    isDiffLockOn = it
                    onButtonEvent(ButtonBits.DIFF_LOCK, it)
                },
                modifier = Modifier.weight(1f),
                minHeight = 65.dp
            )

            CockpitButton(
                label = "LIFT AXLE",
                subLabel = "TAG / MID",
                activeColor = RacingGreen,
                isToggle = true,
                isActive = isAxleLiftOn,
                onStateChange = {
                    isAxleLiftOn = it
                    onButtonEvent(ButtonBits.LIFT_AXLE, it)
                },
                modifier = Modifier.weight(1f),
                minHeight = 65.dp
            )

            CockpitButton(
                label = "WIPERS",
                subLabel = "RAIN",
                activeColor = NeonCyan,
                isToggle = true,
                isActive = isWipersOn,
                onStateChange = {
                    isWipersOn = it
                    onButtonEvent(ButtonBits.WIPERS, it)
                },
                modifier = Modifier.weight(1f),
                minHeight = 65.dp
            )

            CockpitButton(
                label = "CRUISE",
                subLabel = "SET / RES",
                activeColor = RacingGreen,
                isToggle = true,
                isActive = isCruiseOn,
                onStateChange = {
                    isCruiseOn = it
                    onButtonEvent(ButtonBits.CRUISE_CONTROL, it)
                },
                modifier = Modifier.weight(1f),
                minHeight = 65.dp
            )

            CockpitButton(
                label = "CAMERA",
                subLabel = "CYCLE",
                activeColor = Color.White,
                onStateChange = { onButtonEvent(ButtonBits.CAMERA, it) },
                modifier = Modifier.weight(1f),
                minHeight = 65.dp
            )
        }
    }
}
