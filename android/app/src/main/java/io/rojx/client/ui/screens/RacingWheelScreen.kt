package io.rojx.client.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rojx.client.model.ButtonBits
import io.rojx.client.ui.components.CockpitButton
import io.rojx.client.ui.components.VerticalPedal
import io.rojx.client.ui.theme.*

@Composable
fun RacingWheelScreen(
    wheelAngle: Float,
    throttle: Float,
    brake: Float,
    onThrottleChange: (Float) -> Unit,
    onBrakeChange: (Float) -> Unit,
    onButtonEvent: (bitmask: Int, isPressed: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isHandbrakeActive by remember { mutableStateOf(false) }
    var isHazardsActive by remember { mutableStateOf(false) }
    var isLightsActive by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT COLUMN: BRAKE PEDAL & SECONDARY CONTROLS
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .width(130.dp)
                .fillMaxHeight()
        ) {
            // Analog Brake Pedal
            VerticalPedal(
                label = "Brake",
                value = brake,
                onValueChange = onBrakeChange,
                activeColor = RacingRed,
                gradientColors = listOf(Color(0xFFEF4444), Color(0xFF991B1B)),
                width = 85.dp,
                height = 200.dp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Cockpit Utility Buttons (Handbrake, Ignition, Hazards)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CockpitButton(
                    label = "PARK",
                    subLabel = "BRAKE",
                    activeColor = RacingRed,
                    isToggle = true,
                    isActive = isHandbrakeActive,
                    onStateChange = {
                        isHandbrakeActive = it
                        onButtonEvent(ButtonBits.HANDBRAKE, it)
                    },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                CockpitButton(
                    label = "HAZARD",
                    activeColor = AmberWarning,
                    isToggle = true,
                    isActive = isHazardsActive,
                    onStateChange = {
                        isHazardsActive = it
                        onButtonEvent(ButtonBits.HAZARD_LIGHTS, it)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // CENTER COLUMN: 900° STEERING WHEEL WITH RPM SHIFT LIGHTS
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                // RPM LED Shift Bar (Clay tactile sunken casing)
                Row(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .shadow(4.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(ClaySurfaceSunken)
                        .border(1.5.dp, ClayBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    repeat(10) { index ->
                        val threshold = index / 10f
                        val isLit = throttle > threshold
                        val ledColor = when {
                            index < 5 -> RacingGreen
                            index < 8 -> AmberWarning
                            else -> RacingRed
                        }

                        Box(
                            modifier = Modifier
                                .size(width = 16.dp, height = 9.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isLit) ledColor else ClayBorder)
                        )
                    }
                }

                // 900° Realistic Canvas Steering Wheel with Shift Paddles
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(230.dp)
                ) {
                    // Left Paddle Shifter (Downshift)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (-10).dp)
                    ) {
                        CockpitButton(
                            label = "▼",
                            subLabel = "DOWN",
                            activeColor = NeonCyan,
                            onStateChange = { onButtonEvent(ButtonBits.SHIFT_DOWN, it) },
                            minWidth = 50.dp,
                            minHeight = 70.dp
                        )
                    }

                    // Right Paddle Shifter (Upshift)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 10.dp)
                    ) {
                        CockpitButton(
                            label = "▲",
                            subLabel = "UP",
                            activeColor = NeonCyan,
                            onStateChange = { onButtonEvent(ButtonBits.SHIFT_UP, it) },
                            minWidth = 50.dp,
                            minHeight = 70.dp
                        )
                    }

                    // Rotating Steering Wheel Graphic
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .rotate(wheelAngle),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.minDimension / 2f

                            // Outer Thick Leather Rim
                            drawCircle(
                                color = ClaySurfaceElevated,
                                radius = radius - 12f,
                                style = Stroke(width = 24f)
                            )
                            // Outer Clay Trim Line
                            drawCircle(
                                color = NeonCyan,
                                radius = radius - 2f,
                                style = Stroke(width = 3f)
                            )

                            // Top Center Stripe (Yellow Marker)
                            drawLine(
                                color = AmberWarning,
                                start = Offset(center.x, 0f),
                                end = Offset(center.x, 24f),
                                strokeWidth = 10f,
                                cap = StrokeCap.Round
                            )

                            // 3-Spoke Wheel Structure
                            drawLine(
                                color = ClayBorderLight,
                                start = Offset(24f, center.y),
                                end = Offset(size.width - 24f, center.y),
                                strokeWidth = 18f,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = ClayBorderLight,
                                start = center,
                                end = Offset(center.x, size.height - 24f),
                                strokeWidth = 18f,
                                cap = StrokeCap.Round
                            )
                        }

                        // Center Horn Hub (Clay Tactile Button)
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(ClaySurfaceHigh)
                                .border(2.dp, NeonCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ROJX",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.5.sp,
                                color = TextPrimary,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Digital Steering Angle Readout
                Text(
                    text = "${wheelAngle.toInt()}°",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonCyan,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // RIGHT COLUMN: THROTTLE PEDAL & ACTION BUTTONS
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .width(130.dp)
                .fillMaxHeight()
        ) {
            // Analog Throttle Pedal
            VerticalPedal(
                label = "Throttle",
                value = throttle,
                onValueChange = onThrottleChange,
                activeColor = RacingGreen,
                gradientColors = listOf(Color(0xFF10B981), Color(0xFF047857)),
                width = 85.dp,
                height = 200.dp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Cockpit Controls (NOS, Lights, Horn)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CockpitButton(
                    label = "NOS",
                    activeColor = NeonCyan,
                    onStateChange = { onButtonEvent(ButtonBits.NITRO, it) },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                CockpitButton(
                    label = "LIGHTS",
                    activeColor = NeonCyan,
                    isToggle = true,
                    isActive = isLightsActive,
                    onStateChange = {
                        isLightsActive = it
                        onButtonEvent(ButtonBits.HEADLIGHTS, it)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
