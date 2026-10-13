package com.padlink

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable
fun PadLinkApp(state: ControllerState, client: UdpClient, tilt: TiltSteering) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("padlink", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(prefs.getString("host", "") ?: "") }
    var status by remember { mutableStateOf("Not connected") }
    var sendJob by remember { mutableStateOf<Job?>(null) }
    var tiltOn by remember { mutableStateOf(true) }
    var invert by remember { mutableStateOf(false) }

    fun connect(ip: String) {
        if (ip.isBlank()) return
        sendJob?.cancel()
        sendJob = scope.launch(Dispatchers.IO) {
            try {
                client.connect(ip.trim())
                prefs.edit().putString("host", ip.trim()).apply()
                withContext(Dispatchers.Main) { status = "Sending to ${ip.trim()}" }
                while (isActive) { client.send(state.toPacket()); delay(16) } // ~60 Hz
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Error: ${e.message}" }
            }
        }
    }

    Row(
        Modifier.fillMaxSize().background(Color(0xFF0F1115)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Pedal("BRAKE", Color(0xFFE53935), Modifier.weight(1f).fillMaxHeight()) { state.lt = it }

        Column(Modifier.weight(2.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = host, onValueChange = { host = it }, singleLine = true,
                    label = { Text("PC IP") }, modifier = Modifier.weight(1f)
                )
                Button(onClick = { connect(host) }) { Text("Connect") }
                Button(onClick = {
                    scope.launch {
                        status = "Searching…"
                        val ip = UdpClient.discover()
                        if (ip != null) { host = ip; connect(ip) } else status = "No PC found (is the receiver running?)"
                    }
                }) { Text("Find PC") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(status, Modifier.weight(1f), color = Color.LightGray)
                OutlinedButton(onClick = { tilt.calibrate() }) { Text("Center wheel") }
                Text("Tilt"); Switch(checked = tiltOn, onCheckedChange = { tiltOn = it; tilt.enabled = it; if (!it) state.lx = 0f })
                Text("Invert"); Switch(checked = invert, onCheckedChange = { invert = it; tilt.invert = it })
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PadButton("LB", Btn.LB, state, Modifier.weight(1f).fillMaxHeight())
                PadButton("RB", Btn.RB, state, Modifier.weight(1f).fillMaxHeight())
                PadButton("BACK", Btn.BACK, state, Modifier.weight(1f).fillMaxHeight())
                PadButton("START", Btn.START, state, Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { // D-pad
                    PadButton("▲", Btn.UP, state, Modifier.weight(1f).fillMaxWidth())
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PadButton("◀", Btn.LEFT, state, Modifier.weight(1f).fillMaxHeight())
                        PadButton("▶", Btn.RIGHT, state, Modifier.weight(1f).fillMaxHeight())
                    }
                    PadButton("▼", Btn.DOWN, state, Modifier.weight(1f).fillMaxWidth())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { // face buttons
                    PadButton("Y", Btn.Y, state, Modifier.weight(1f).fillMaxWidth())
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PadButton("X", Btn.X, state, Modifier.weight(1f).fillMaxHeight())
                        PadButton("B", Btn.B, state, Modifier.weight(1f).fillMaxHeight())
                    }
                    PadButton("A", Btn.A, state, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }

        Pedal("GAS", Color(0xFF43A047), Modifier.weight(1f).fillMaxHeight()) { state.rt = it }
    }
}

@Composable
fun PadButton(label: String, mask: Int, state: ControllerState, modifier: Modifier = Modifier) {
    var down by remember { mutableStateOf(false) }
    Box(
        modifier
            .background(if (down) Color(0xFF1E88E5) else Color(0xFF2A2F3A), RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    down = true; state.setButton(mask, true)
                    do {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                    down = false; state.setButton(mask, false)
                }
            },
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.White) }
}

/** Analog pedal: touch height = pressure (bottom 0, top 1). */
@Composable
fun Pedal(label: String, color: Color, modifier: Modifier, onValue: (Float) -> Unit) {
    var v by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .background(Color(0xFF1E222B), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown()
                    fun update(y: Float) { v = (1f - y / size.height).coerceIn(0f, 1f); onValue(v) }
                    update(first.position.y)
                    do {
                        val e = awaitPointerEvent()
                        e.changes.firstOrNull()?.let { update(it.position.y) }
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                    v = 0f; onValue(0f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(v).background(color, RoundedCornerShape(16.dp)))
        Text(label, color = Color.White)
    }
}
