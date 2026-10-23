package de.vanhake.wakeup.ui.challenge

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

private const val SHAKE_THRESHOLD_G = 2.0f
private const val SHAKE_DEBOUNCE_MS = 250L

/** Returns false if the device has no accelerometer. */
fun hasAccelerometer(sensorManager: SensorManager): Boolean =
    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

@Composable
fun ShakeChallenge(target: Int, onSolved: () -> Unit, onShake: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }
    val currentOnSolved by rememberUpdatedState(onSolved)
    val currentOnShake by rememberUpdatedState(onShake)

    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                val now = System.currentTimeMillis()
                if (g > SHAKE_THRESHOLD_G && now - last > SHAKE_DEBOUNCE_MS && count < target) {
                    last = now
                    count++
                    currentOnShake()
                    if (count >= target) currentOnSolved()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Filled.Vibration, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Schüttle dein Handy!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { count / target.toFloat() },
                modifier = Modifier.size(240.dp),
                strokeWidth = 18.dp,
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${target - count}", fontSize = 72.sp, fontWeight = FontWeight.Bold)
                Text("noch", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.weight(1.5f))
    }
}
