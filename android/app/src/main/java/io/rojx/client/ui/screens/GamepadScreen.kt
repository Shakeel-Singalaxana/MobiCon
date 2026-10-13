package io.rojx.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rojx.client.model.ButtonBits
import io.rojx.client.ui.components.CockpitButton
import io.rojx.client.ui.components.HorizontalTriggerSlider
import io.rojx.client.ui.components.VirtualThumbstick
import io.rojx.client.ui.theme.*

@Composable
fun GamepadScreen(
    isPlaystation: Boolean = false,
    triggerLeft: Float = 0f,
    triggerRight: Float = 0f,
    onTriggerLeftChange: (Float) -> Unit = {},
    onTriggerRightChange: (Float) -> Unit = {},
    onLeftStickChange: (x: Float, y: Float) -> Unit,
    onRightStickChange: (x: Float, y: Float) -> Unit,
    onButtonEvent: (bitmask: Int, isPressed: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val topLabel = if (isPlaystation) "△" else "Y"
    val bottomLabel = if (isPlaystation) "✕" else "A"
    val leftLabel = if (isPlaystation) "◻" else "X"
    val rightLabel = if (isPlaystation) "◯" else "B"

    val topColor = if (isPlaystation) PsTriangle else XboxY
    val bottomColor = if (isPlaystation) PsCross else XboxA
    val leftColor = if (isPlaystation) PsSquare else XboxX
    val rightColor = if (isPlaystation) PsCircle else XboxB

    val l1Label = if (isPlaystation) "L1" else "LB"
    val l2Label = if (isPlaystation) "L2" else "LT"
    val r1Label = if (isPlaystation) "R1" else "RB"
    val r2Label = if (isPlaystation) "R2" else "RT"

    val triggerColor = if (isPlaystation) PsCross else NeonCyan
    val triggerGradient = if (isPlaystation)
        listOf(Color(0xFF0284C7), PsCross)
    else
        listOf(Color(0xFF0891B2), NeonCyan)

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT WING: D-PAD & LEFT STICK & LB / LT SLIDER
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight()
        ) {
            // Bumpers & Horizontal Trigger Slider (Left)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CockpitButton(
                    label = l1Label,
                    activeColor = NeonCyan,
                    onStateChange = { onButtonEvent(ButtonBits.LB, it) },
                    minWidth = 55.dp,
                    minHeight = 42.dp
                )
                HorizontalTriggerSlider(
                    label = l2Label,
                    value = triggerLeft,
                    onValueChange = onTriggerLeftChange,
                    activeColor = triggerColor,
                    gradientColors = triggerGradient,
                    width = 135.dp,
                    height = 42.dp,
                    slideToLeft = false
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Left Thumbstick
            VirtualThumbstick(
                size = 135.dp,
                thumbRadius = 26.dp,
                onPositionChange = onLeftStickChange
            )

            Spacer(modifier = Modifier.height(6.dp))

            // D-Pad Cross in Clay Tactile Style
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CockpitButton(
                    label = "▲",
                    activeColor = TextPrimary,
                    onStateChange = { onButtonEvent(ButtonBits.DPAD_UP, it) },
                    minWidth = 42.dp,
                    minHeight = 38.dp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CockpitButton(
                        label = "◄",
                        activeColor = TextPrimary,
                        onStateChange = { onButtonEvent(ButtonBits.DPAD_LEFT, it) },
                        minWidth = 42.dp,
                        minHeight = 38.dp
                    )
                    Spacer(modifier = Modifier.width(32.dp))
                    CockpitButton(
                        label = "►",
                        activeColor = TextPrimary,
                        onStateChange = { onButtonEvent(ButtonBits.DPAD_RIGHT, it) },
                        minWidth = 42.dp,
                        minHeight = 38.dp
                    )
                }
                CockpitButton(
                    label = "▼",
                    activeColor = TextPrimary,
                    onStateChange = { onButtonEvent(ButtonBits.DPAD_DOWN, it) },
                    minWidth = 42.dp,
                    minHeight = 38.dp
                )
            }
        }

        // CENTER COLUMN: BACK/SHARE, GUIDE/PS, START/OPTIONS
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxHeight()
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                CockpitButton(
                    label = if (isPlaystation) "SHARE" else "BACK",
                    activeColor = TextSecondary,
                    onStateChange = { onButtonEvent(ButtonBits.BACK, it) },
                    minWidth = 55.dp,
                    minHeight = 36.dp
                )

                // 3D Clay Guide Logo
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(ClaySurfaceElevated)
                        .border(2.dp, if (isPlaystation) PsCross else NeonCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPlaystation) "PS" else "X",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                }

                CockpitButton(
                    label = if (isPlaystation) "OPTIONS" else "START",
                    activeColor = TextSecondary,
                    onStateChange = { onButtonEvent(ButtonBits.START, it) },
                    minWidth = 55.dp,
                    minHeight = 36.dp
                )
            }
        }

        // RIGHT WING: ACTION BUTTONS & RIGHT STICK & RB / RT SLIDER
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight()
        ) {
            // Bumpers & Horizontal Trigger Slider (Right)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalTriggerSlider(
                    label = r2Label,
                    value = triggerRight,
                    onValueChange = onTriggerRightChange,
                    activeColor = triggerColor,
                    gradientColors = triggerGradient,
                    width = 135.dp,
                    height = 42.dp,
                    slideToLeft = true
                )
                CockpitButton(
                    label = r1Label,
                    activeColor = NeonCyan,
                    onStateChange = { onButtonEvent(ButtonBits.RB, it) },
                    minWidth = 55.dp,
                    minHeight = 42.dp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Diamond Button Group (ABXY or Shapes)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CockpitButton(
                    label = topLabel,
                    activeColor = topColor,
                    onStateChange = { onButtonEvent(ButtonBits.Y, it) },
                    minWidth = 46.dp,
                    minHeight = 46.dp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CockpitButton(
                        label = leftLabel,
                        activeColor = leftColor,
                        onStateChange = { onButtonEvent(ButtonBits.X, it) },
                        minWidth = 46.dp,
                        minHeight = 46.dp
                    )
                    CockpitButton(
                        label = rightLabel,
                        activeColor = rightColor,
                        onStateChange = { onButtonEvent(ButtonBits.B, it) },
                        minWidth = 46.dp,
                        minHeight = 46.dp
                    )
                }
                CockpitButton(
                    label = bottomLabel,
                    activeColor = bottomColor,
                    onStateChange = { onButtonEvent(ButtonBits.A, it) },
                    minWidth = 46.dp,
                    minHeight = 46.dp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Right Thumbstick (Look Around)
            VirtualThumbstick(
                size = 135.dp,
                thumbRadius = 26.dp,
                onPositionChange = onRightStickChange
            )
        }
    }
}
