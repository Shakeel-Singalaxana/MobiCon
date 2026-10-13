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
fun VerticalPedal(
    label: String,
    value: Float, // 0.0f to 1.0f
    onValueChange: (Float) -> Unit,
    activeColor: Color,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    width: Dp = 80.dp,
    height: Dp = 230.dp,
    autoReturn: Boolean = true,
    enableHaptics: Boolean = true
) {
    val view = LocalView.current
    val animatedFill by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 800f),
        label = "pedalFill"
    )

    val shape = RoundedCornerShape(16.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = activeColor,
            letterSpacing = 1.sp
        )

        Text(
            text = "${(value * 100).toInt()}%",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .shadow(
                    elevation = 6.dp,
                    shape = shape,
                    ambientColor = Color.Black,
                    spotColor = Color.Black
                )
                .clip(shape)
                .background(ClaySurfaceSunken)
                .border(2.dp, ClayBorder, shape)
                .pointerInput(autoReturn) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (enableHaptics) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                            val newVal = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                            onValueChange(newVal)
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
                            val newVal = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                            onValueChange(newVal)
                        }
                    )
                }
                .pointerInput(autoReturn) {
                    detectTapGestures(
                        onPress = { offset ->
                            if (enableHaptics) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                            val newVal = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                            onValueChange(newVal)
                            val released = tryAwaitRelease()
                            if (released && autoReturn) {
                                onValueChange(0f)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            // Tactile footplate grip ribs
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(6) {
                    Box(
                        modifier = Modifier
                            .width(width * 0.62f)
                            .height(4.dp)
                            .background(ClayBorder, RoundedCornerShape(2.dp))
                    )
                }
            }

            // Fill Level Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animatedFill)
                    .clip(shape)
                    .background(Brush.verticalGradient(gradientColors))
            )
        }
    }
}
