package io.rojx.client.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.rojx.client.ui.theme.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun VirtualThumbstick(
    size: Dp = 150.dp,
    thumbRadius: Dp = 32.dp,
    onPositionChange: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
    enableHaptics: Boolean = true
) {
    val view = LocalView.current
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .clip(CircleShape)
            .background(ClaySurfaceSunken)
            .border(2.5.dp, ClayBorder, CircleShape)
            .pointerInput(Unit) {
                val maxRadius = (this.size.width / 2f) - thumbRadius.toPx()

                detectDragGestures(
                    onDragStart = {
                        if (enableHaptics) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    },
                    onDragEnd = {
                        thumbOffset = Offset.Zero
                        onPositionChange(0f, 0f)
                    },
                    onDragCancel = {
                        thumbOffset = Offset.Zero
                        onPositionChange(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = thumbOffset + dragAmount
                        val distance = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)

                        thumbOffset = if (distance > maxRadius) {
                            val angle = atan2(newOffset.y, newOffset.x)
                            Offset(cos(angle) * maxRadius, sin(angle) * maxRadius)
                        } else {
                            newOffset
                        }

                        val normX = (thumbOffset.x / maxRadius).coerceIn(-1f, 1f)
                        val normY = (thumbOffset.y / maxRadius).coerceIn(-1f, 1f)
                        onPositionChange(normX, normY)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            
            // Draw concave dish concentric guide rings
            drawCircle(
                color = ClayBorder,
                radius = this.size.width * 0.35f,
                center = center,
                style = Stroke(width = 1.5f)
            )

            // Draw subtle tactile crosshairs
            drawLine(
                color = ClayBorderLight.copy(alpha = 0.6f),
                start = Offset(center.x, 24f),
                end = Offset(center.x, this.size.height - 24f),
                strokeWidth = 2f
            )
            drawLine(
                color = ClayBorderLight.copy(alpha = 0.6f),
                start = Offset(24f, center.y),
                end = Offset(this.size.width - 24f, center.y),
                strokeWidth = 2f
            )

            val capCenter = center + thumbOffset

            // 3D Clay Thumbstick Cap Base Shadow
            drawCircle(
                color = Color(0x66000000),
                radius = thumbRadius.toPx() + 2f,
                center = capCenter + Offset(0f, 3f)
            )

            // 3D Clay Thumbstick Cap Outer Rim
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ClaySurfaceElevated, ClaySurfaceHigh),
                    center = capCenter - Offset(4f, 4f),
                    radius = thumbRadius.toPx()
                ),
                radius = thumbRadius.toPx(),
                center = capCenter
            )

            // Outer highlight border
            drawCircle(
                color = ClayBorderLight,
                radius = thumbRadius.toPx(),
                center = capCenter,
                style = Stroke(width = 2f)
            )

            // Tactile Center Silicone Grip
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NeonCyan, Color(0xFF0284C7)),
                    center = capCenter,
                    radius = thumbRadius.toPx() * 0.55f
                ),
                radius = thumbRadius.toPx() * 0.52f,
                center = capCenter
            )

            // Center tactile dot
            drawCircle(
                color = Color.White,
                radius = thumbRadius.toPx() * 0.18f,
                center = capCenter
            )
        }
    }
}
