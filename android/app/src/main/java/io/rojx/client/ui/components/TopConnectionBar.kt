package io.rojx.client.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rojx.client.model.ControllerMode
import io.rojx.client.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun TopConnectionBar(
    targetIp: String,
    targetPort: Int = 7777,
    playerSlot: Int = 0,
    packetRateHz: Int,
    activeMode: ControllerMode,
    onModeChange: (ControllerMode) -> Unit,
    isCalibrating: Boolean = false,
    onCenterWheelClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    enableHaptics: Boolean = true
) {
    val view = LocalView.current
    var isExpanded by remember { mutableStateOf(false) }
    val slotColors = listOf(Color(0xFF38BDF8), Color(0xFFF43F5E), Color(0xFF10B981), Color(0xFFF59E0B))
    val currentSlotColor = slotColors.getOrElse(playerSlot) { Color(0xFF38BDF8) }

    // Autohide after 5 seconds of opening if no interaction
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            delay(5000)
            isExpanded = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // EXPANDED PANEL
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInVertically(initialOffsetY = { -it }) + expandVertically() + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)),
                color = ClaySurface,
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, ClayBorderLight)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top Info & Actions Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: ROJX Logo & Connection Indicator with Player Slot Badge
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .shadow(4.dp, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(currentSlotColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "P${playerSlot + 1}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = ClayBg
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(RacingGreen)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (targetIp.isNotBlank()) targetIp else "127.0.0.1",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                                Text(
                                    text = "UDP :$targetPort • $packetRateHz Hz",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Right: Actions (Center Wheel & Settings)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Center Wheel Button
                            Button(
                                onClick = {
                                    if (enableHaptics) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    }
                                    onCenterWheelClick()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ClaySurfaceElevated,
                                    contentColor = NeonCyan
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .shadow(3.dp, RoundedCornerShape(10.dp))
                                    .border(1.dp, ClayBorderLight, RoundedCornerShape(10.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Center Wheel",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Center Wheel",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Settings Button
                            IconButton(
                                onClick = {
                                    if (enableHaptics) {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    }
                                    onSettingsClick()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .shadow(3.dp, RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ClaySurfaceElevated)
                                    .border(1.dp, ClayBorderLight, RoundedCornerShape(10.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    // Mode Selection Tabs in Clay Style
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ClaySurfaceSunken)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ControllerMode.values().forEach { mode ->
                            val isSelected = activeMode == mode
                            val tabShape = RoundedCornerShape(8.dp)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(tabShape)
                                    .background(if (isSelected) ClaySurfaceHigh else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) NeonCyan else Color.Transparent,
                                        shape = tabShape
                                    )
                                    .clickable {
                                        if (enableHaptics) {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        }
                                        onModeChange(mode)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.displayName,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                    color = if (isSelected) NeonCyan else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // TINY CLAY ISLAND NOTCH (CENTERED AT TOP)
        val islandShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        Box(
            modifier = Modifier
                .wrapContentSize()
                .shadow(elevation = 8.dp, shape = islandShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(islandShape)
                .background(ClaySurfaceElevated)
                .border(
                    width = 1.5.dp,
                    color = if (isExpanded) NeonCyan else ClayBorderLight,
                    shape = islandShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (enableHaptics) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    isExpanded = !isExpanded
                }
                .padding(horizontal = 16.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                // Status / Player Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(currentSlotColor)
                )

                Text(
                    text = "P${playerSlot + 1}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    color = currentSlotColor
                )

                Text(
                    text = "•",
                    fontSize = 10.sp,
                    color = TextMuted
                )

                Text(
                    text = activeMode.displayName,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse Topbar" else "Expand Topbar",
                    tint = if (isExpanded) NeonCyan else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
