package io.rojx.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rojx.client.model.AppSettings
import io.rojx.client.network.DiscoveredServer
import io.rojx.client.network.DiscoveryClient
import io.rojx.client.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    currentSettings: AppSettings,
    onSaveSettings: (AppSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var ipText by remember { mutableStateOf(currentSettings.targetIp) }
    var portText by remember { mutableStateOf(currentSettings.targetPort.toString()) }
    var playerSlot by remember { mutableIntStateOf(currentSettings.playerSlot) }
    var packetRate by remember { mutableIntStateOf(currentSettings.packetRateHz) }
    var maxAngle by remember { mutableFloatStateOf(currentSettings.maxWheelDegrees) }
    var invertSteering by remember { mutableStateOf(currentSettings.invertSteering) }
    var sensitivity by remember { mutableFloatStateOf(currentSettings.steeringSensitivity) }
    var deadzone by remember { mutableFloatStateOf(currentSettings.steeringDeadzone) }
    var haptics by remember { mutableStateOf(currentSettings.hapticFeedback) }

    var isScanning by remember { mutableStateOf(false) }
    var discoveredServers by remember { mutableStateOf<List<DiscoveredServer>>(emptyList()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClayBg)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MOBICON CONFIGURATION",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = 0.5.sp
            )

            Button(
                onClick = {
                    val updated = currentSettings.copy(
                        targetIp = ipText.trim(),
                        targetPort = portText.toIntOrNull() ?: 7777,
                        playerSlot = playerSlot,
                        packetRateHz = packetRate,
                        maxWheelDegrees = maxAngle,
                        invertSteering = invertSteering,
                        steeringSensitivity = sensitivity,
                        steeringDeadzone = deadzone,
                        hapticFeedback = haptics
                    )
                    onSaveSettings(updated)
                    onClose()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ClayBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.shadow(4.dp, RoundedCornerShape(10.dp))
            ) {
                Text("Save & Close", fontWeight = FontWeight.Black)
            }
        }

        // Section 1: Network & Target PC (Clay Card)
        Card(
            colors = CardDefaults.cardColors(containerColor = ClaySurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(16.dp))
                .border(1.5.dp, ClayBorderLight, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "TARGET PC CONNECTION",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonCyan,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = ipText,
                        onValueChange = { ipText = it },
                        label = { Text("PC IP (e.g. 192.168.1.50)") },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = ClayBorder,
                            focusedContainerColor = ClaySurfaceSunken,
                            unfocusedContainerColor = ClaySurfaceSunken
                        )
                    )

                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text("Port") },
                        modifier = Modifier.weight(0.7f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = ClayBorder,
                            focusedContainerColor = ClaySurfaceSunken,
                            unfocusedContainerColor = ClaySurfaceSunken
                        )
                    )

                    Button(
                        onClick = {
                            isScanning = true
                            coroutineScope.launch {
                                discoveredServers = DiscoveryClient.scanForServers()
                                isScanning = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ClaySurfaceElevated),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .shadow(3.dp, RoundedCornerShape(10.dp))
                            .border(1.dp, ClayBorderLight, RoundedCornerShape(10.dp))
                    ) {
                        Text(if (isScanning) "Scanning..." else "Auto-Scan", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Discovered Servers List
                if (discoveredServers.isNotEmpty()) {
                    Text("Discovered Receivers (Tap to Connect):", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    discoveredServers.forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ClaySurfaceElevated)
                                .border(1.dp, ClayBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    ipText = s.ip
                                    portText = s.udpPort.toString()
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(s.hostname, fontWeight = FontWeight.Black, color = TextPrimary, fontSize = 13.5.sp)
                                Text("${s.ip} • UDP :${s.udpPort} | Web :${s.httpPort}", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                            Text(
                                if (s.isDriverReady) "Driver Ready" else "Simulation Mode",
                                color = if (s.isDriverReady) RacingGreen else AmberWarning,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // Multi-Player Slot Selection (P1..P4)
                Text("Multiplayer Slot Assignment (Up to 4 Players)", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val slotColors = listOf(Color(0xFF38BDF8), Color(0xFFF43F5E), Color(0xFF10B981), Color(0xFFF59E0B))
                    listOf(0 to "🎮 Player 1", 1 to "🎮 Player 2", 2 to "🎮 Player 3", 3 to "🎮 Player 4").forEach { (sIndex, label) ->
                        FilterChip(
                            selected = playerSlot == sIndex,
                            onClick = { playerSlot = sIndex },
                            label = { Text(label, fontWeight = FontWeight.Black) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = slotColors[sIndex],
                                selectedLabelColor = ClayBg,
                                containerColor = ClaySurfaceElevated,
                                labelColor = TextPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = playerSlot == sIndex,
                                borderColor = if (playerSlot == sIndex) slotColors[sIndex] else ClayBorderLight
                            )
                        )
                    }
                }

                // Mobile Web Browser Alternative Action
                Button(
                    onClick = {
                        try {
                            val url = "http://${ipText.trim()}:8080"
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ClaySurfaceElevated),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .shadow(3.dp, RoundedCornerShape(10.dp))
                        .border(1.dp, ClayBorderLight, RoundedCornerShape(10.dp))
                ) {
                    Text("🌐 Launch Mobicon Web Controller in Browser", color = NeonCyan, fontSize = 12.5.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        // Section 2: Sensor & Steering Controls (Clay Card)
        Card(
            colors = CardDefaults.cardColors(containerColor = ClaySurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(16.dp))
                .border(1.5.dp, ClayBorderLight, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "STEERING WHEEL & SENSORS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonCyan,
                    letterSpacing = 0.5.sp
                )

                // Max Wheel Rotation Lock
                Text("Max Rotation Angle: ${maxAngle.toInt()}°", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(180f, 360f, 540f, 900f, 1080f).forEach { angle ->
                        FilterChip(
                            selected = maxAngle == angle,
                            onClick = { maxAngle = angle },
                            label = { Text("${angle.toInt()}°", fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan,
                                selectedLabelColor = ClayBg,
                                containerColor = ClaySurfaceElevated,
                                labelColor = TextPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = maxAngle == angle,
                                borderColor = if (maxAngle == angle) NeonCyan else ClayBorderLight
                            )
                        )
                    }
                }

                // Sensitivity
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sensitivity", fontSize = 12.5.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("${(sensitivity * 100).toInt()}%", fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = NeonCyan)
                }
                Slider(
                    value = sensitivity,
                    onValueChange = { sensitivity = it },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan, inactiveTrackColor = ClaySurfaceSunken)
                )

                // Deadzone
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Deadzone", fontSize = 12.5.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("${(deadzone * 100).toInt()}%", fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = NeonCyan)
                }
                Slider(
                    value = deadzone,
                    onValueChange = { deadzone = it },
                    valueRange = 0.0f..0.15f,
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan, inactiveTrackColor = ClaySurfaceSunken)
                )

                // Invert Steering & Haptics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Invert Steering Direction", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = invertSteering,
                        onCheckedChange = { invertSteering = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextPrimary,
                            checkedTrackColor = NeonCyan,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = ClaySurfaceSunken
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Force Feedback Haptics (Vibration)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = haptics,
                        onCheckedChange = { haptics = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextPrimary,
                            checkedTrackColor = NeonCyan,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = ClaySurfaceSunken
                        )
                    )
                }
            }
        }
    }
}
