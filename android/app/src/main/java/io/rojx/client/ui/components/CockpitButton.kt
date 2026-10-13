package io.rojx.client.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rojx.client.ui.theme.*

@Composable
fun CockpitButton(
    label: String,
    subLabel: String? = null,
    activeColor: Color = NeonCyan,
    isToggle: Boolean = false,
    isActive: Boolean = false,
    onStateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = 75.dp,
    minHeight: Dp = 60.dp,
    enableHaptics: Boolean = true
) {
    val view = LocalView.current
    var isPressedLocally by remember { mutableStateOf(false) }

    val isIlluminated = if (isToggle) isActive else (isPressedLocally || isActive)
    val isPhysicallyPressed = isPressedLocally || (isToggle && isActive)

    val elevationAnim by animateDpAsState(
        targetValue = if (isPhysicallyPressed) 1.dp else 6.dp,
        animationSpec = tween(durationMillis = 80),
        label = "clayElevation"
    )

    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressedLocally) 0.96f else 1.0f,
        animationSpec = tween(durationMillis = 80),
        label = "clayScale"
    )

    val cornerRadius = 14.dp
    val buttonShape = RoundedCornerShape(cornerRadius)

    // Clay tactile surface gradient
    val backgroundBrush = if (isIlluminated) {
        Brush.verticalGradient(
            colors = listOf(
                activeColor.copy(alpha = 0.85f),
                activeColor.copy(alpha = 0.65f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                ClaySurfaceElevated,
                ClaySurface
            )
        )
    }

    val borderColor = if (isIlluminated) {
        activeColor
    } else if (isPhysicallyPressed) {
        ClayBorderPressed
    } else {
        ClayBorderLight
    }

    Box(
        modifier = modifier
            .widthIn(min = minWidth)
            .heightIn(min = minHeight)
            .scale(scaleAnim)
            .shadow(
                elevation = elevationAnim,
                shape = buttonShape,
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .clip(buttonShape)
            .background(backgroundBrush)
            .border(
                width = if (isIlluminated) 2.dp else 1.5.dp,
                color = borderColor,
                shape = buttonShape
            )
            .pointerInput(isToggle, isActive) {
                if (isToggle) {
                    detectTapGestures(
                        onTap = {
                            if (enableHaptics) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            }
                            onStateChange(!isActive)
                        }
                    )
                } else {
                    detectTapGestures(
                        onPress = {
                            if (enableHaptics) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                            isPressedLocally = true
                            onStateChange(true)
                            tryAwaitRelease()
                            isPressedLocally = false
                            onStateChange(false)
                        }
                    )
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = if (isIlluminated) Color.White else TextPrimary,
                letterSpacing = 0.5.sp
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isIlluminated) Color(0xFFF1F5F9) else TextSecondary,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}
