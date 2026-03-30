package com.screening.mobile.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screening.shared.data.DashboardRepository
import com.screening.shared.model.ClientMessage
import com.screening.shared.model.DashboardState
import kotlinx.coroutines.delay

@Composable
fun SceneControllerTab(state: DashboardState, repo: DashboardRepository) {
    val context = LocalContext.current
    var useGyro by remember { mutableStateOf(false) }
    var distance by remember { mutableFloatStateOf(5f) }
    var orbitX by remember { mutableFloatStateOf(0f) }
    var orbitY by remember { mutableFloatStateOf(0.3f) }

    // Gyro sensor
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    var gyroQuaternion by remember { mutableStateOf(floatArrayOf(0f, 0f, 0f, 1f)) }

    // Gyro: track orientation changes as deltas to orbit angles
    var calibrated by remember { mutableStateOf(false) }
    var refPitch by remember { mutableFloatStateOf(0f) }
    var refRoll by remember { mutableFloatStateOf(0f) }

    DisposableEffect(useGyro) {
        if (!useGyro) return@DisposableEffect onDispose {}

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val rotMat = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotMat, event.values)
                SensorManager.getOrientation(rotMat, orientation)
                // orientation[0] = azimuth, [1] = pitch, [2] = roll
                val pitch = orientation[1] // tilt forward/back
                val roll = orientation[2]  // tilt left/right

                if (!calibrated) {
                    refPitch = pitch
                    refRoll = roll
                    calibrated = true
                    return
                }

                // Map phone tilt to orbit: roll → azimuth, pitch → elevation
                orbitX = (roll - refRoll) * 2f  // left/right tilt orbits horizontally
                orbitY = (0.3f + (pitch - refPitch) * 1.5f).coerceIn(-1.2f, 1.5f) // forward/back tilt orbits vertically
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            sensorManager.unregisterListener(listener)
            calibrated = false
        }
    }

    // Send orbit angles (not quaternions) at 60Hz — renderer converts to camera position
    LaunchedEffect(useGyro, orbitX, orbitY, distance) {
        while (true) {
            repo.sendRaw("""{"type":"scene_camera_update","camera":{"azimuth":$orbitX,"elevation":$orbitY,"distance":$distance,"target":[0,0.5,0]}}""")
            delay(16)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("3D Controller", style = MobileType.h1)
        Spacer(Modifier.height(8.dp))

        // Mode toggle
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { useGyro = false },
                colors = ButtonDefaults.buttonColors(containerColor = if (!useGyro) Cyan else Card)
            ) { Text("Touch", color = if (!useGyro) DarkBg else TextW) }
            Button(
                onClick = { useGyro = true },
                colors = ButtonDefaults.buttonColors(containerColor = if (useGyro) Cyan else Card)
            ) { Text("Gyro", color = if (useGyro) DarkBg else TextW) }
        }

        Spacer(Modifier.height(16.dp))

        if (!useGyro) {
            // Touch orbit control
            Text("Drag to orbit", style = MobileType.body)
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Card)
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            orbitX += dragAmount.x * 0.01f
                            orbitY = (orbitY - dragAmount.y * 0.01f).coerceIn(-1.5f, 1.5f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    // Crosshair
                    drawCircle(Color(0xFF00D2FF), radius = 40f, center = Offset(cx, cy), style = Stroke(2f))
                    drawLine(Color(0xFF00D2FF), Offset(cx - 20, cy), Offset(cx + 20, cy), strokeWidth = 1f)
                    drawLine(Color(0xFF00D2FF), Offset(cx, cy - 20), Offset(cx, cy + 20), strokeWidth = 1f)
                }
            }
        } else {
            Text("Tilt phone to orbit", style = MobileType.body)
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(Card),
                contentAlignment = Alignment.Center
            ) {
                Text("Gyro Active", color = Cyan, fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Zoom control
        Text("Zoom: ${String.format("%.1f", distance)}", style = MobileType.body)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { distance = (distance - 0.5f).coerceAtLeast(1f) },
                colors = ButtonDefaults.buttonColors(containerColor = Card)
            ) { Text("+", color = TextW, fontSize = 20.sp) }
            Button(
                onClick = { distance = (distance + 0.5f).coerceAtMost(30f) },
                colors = ButtonDefaults.buttonColors(containerColor = Card)
            ) { Text("-", color = TextW, fontSize = 20.sp) }
        }

        Spacer(Modifier.height(16.dp))

        // Quick actions
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { repo.changeFrame(5) },
                colors = ButtonDefaults.buttonColors(containerColor = Cyan)
            ) { Text("Load Scene on TV", color = DarkBg) }
            Button(
                onClick = { orbitX = 0f; orbitY = 0.3f; distance = 5f },
                colors = ButtonDefaults.buttonColors(containerColor = Card)
            ) { Text("Reset Camera", color = TextW) }
        }
    }
}
