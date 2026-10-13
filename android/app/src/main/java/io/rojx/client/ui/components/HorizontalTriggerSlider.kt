package io.rojx.client.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun HorizontalTriggerSlider(
    label: String,
    value: Float, // 0.0f to 1.0f
    onValueChange: (Float) -> Unit,
    activeColor: Color = NeonCyan,
    gradientColors: List<Color> = listOf(Color(0xFF0284C7), NeonCyan),
    modifier: Modifier = Modifier,
    width: Dp = 135.dp,
    height: Dp = 42.dp,
    autoReturn: Boolean = true,
    enableHaptics: Boolean = true,
    slideToLeft: Boolean = false
) {
    val view = LocalView.current
    val animatedFill by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 900f),
        label = "triggerFill"
    )

    val isActive = value > 0.01f
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .shadow(
                elevation = if (isActive) 4.dp else 2.dp,
                shape = shape,
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .clip(shape)
            .background(ClaySurfaceSunken)
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) activeColor else ClayBorder,
                shape = shape
            )
            .pointerInput(autoReturn, slideToLeft) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (enableHaptics) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                        val rawFrac = if (slideToLeft) {
                            (size.width - offset.x) / size.width
                        } else {
                            offset.x / size.width
                        }
                        onValueChange(rawFrac.coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        if (autoReturn) {
                            onValueChange(0f)
                        }
                    },
                    onDragCancel = {
                        if (autoReturn) {
                            onValueChange(0f)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val rawFrac = if (slideToLeft) {
                            (size.width - change.position.x) / size.width
                        } else {
                            change.position.x / size.width
                        }
                        onValueChange(rawFrac.coerceIn(0f, 1f))
                    }
                )
            }
            .pointerInput(autoReturn, slideToLeft) {
                detectTapGestures(
                    onPress = { offset ->
                        if (enableHaptics) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                        val rawFrac = if (slideToLeft) {
                            (size.width - offset.x) / size.width
                        } else {
                            offset.x / size.width
                        }
                        onValueChange(rawFrac.coerceIn(0f, 1f))
                        val released = tryAwaitRelease()
                        if (released && autoReturn) {
                            onValueChange(0f)
                        }
                    }
                )
            },
        contentAlignment = if (slideToLeft) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        // Tactile track graduation ribs
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(5) {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(14.dp)
                        .background(ClayBorder, RoundedCornerShape(1.dp))
                )
            }
        }

        // Fill Level Gradient
        if (animatedFill > 0.005f) {
            val fillBrush = if (slideToLeft) {
                Brush.horizontalGradient(gradientColors.reversed())
            } else {
                Brush.horizontalGradient(gradientColors)
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFill)
                    .clip(shape)
                    .background(fillBrush)
            )
        }

        // Label and Percentage Readout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = if (isActive) Color.White else activeColor
            )

            Text(
                text = "${(value * 100).toInt()}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color.White else TextSecondary
            )
        }
    }
}
