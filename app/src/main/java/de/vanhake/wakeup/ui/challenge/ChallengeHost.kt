package de.vanhake.wakeup.ui.challenge

import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import de.vanhake.wakeup.data.Alarm
import de.vanhake.wakeup.data.AlarmRepository
import de.vanhake.wakeup.data.BarcodeRepository
import de.vanhake.wakeup.data.ChallengeType
import de.vanhake.wakeup.data.FallbackChallenge
import de.vanhake.wakeup.data.MathDifficulty

/** Shows the challenge configured for [alarm] and calls [onSolved] once it is completed. */
@Composable
fun ChallengeHost(alarm: Alarm, onSolved: () -> Unit, onInteraction: () -> Unit) {
    val context = LocalContext.current
    var emergency by remember { mutableStateOf(false) }
    val canShake = remember {
        hasAccelerometer(context.getSystemService(SensorManager::class.java))
    }
    val barcode = remember(alarm.barcodeId) { BarcodeRepository.get(alarm.barcodeId) }
    val fallback by AlarmRepository.fallback.collectAsState()

    when {
        alarm.challenge == ChallengeType.NONE ->
            LaunchedEffect(Unit) { onSolved() }
        emergency ->
            FallbackTask(fallback, canShake, onSolved, onInteraction)
        alarm.challenge == ChallengeType.MATH ->
            MathChallenge(alarm.mathDifficulty, alarm.mathCount, onSolved)
        alarm.challenge == ChallengeType.SHAKE && canShake ->
            ShakeChallenge(alarm.shakeCount, onSolved, onShake = onInteraction)
        alarm.challenge == ChallengeType.BARCODE && barcode != null ->
            BarcodeChallenge(barcode, onSolved, onEmergency = { emergency = true }, onScan = onInteraction)
        // No accelerometer, or the barcode was removed from the library.
        else ->
            FallbackTask(fallback, canShake, onSolved, onInteraction)
    }
}

/** The fallback task from the settings; shaking needs an accelerometer, else hard math. */
@Composable
private fun FallbackTask(fallback: FallbackChallenge, canShake: Boolean, onSolved: () -> Unit, onInteraction: () -> Unit) {
    val difficulty = fallback.mathDifficulty
    when {
        difficulty != null -> MathChallenge(difficulty, FallbackChallenge.MATH_COUNT, onSolved)
        canShake -> ShakeChallenge(FallbackChallenge.SHAKE_COUNT, onSolved, onShake = onInteraction)
        else -> MathChallenge(MathDifficulty.HARD, FallbackChallenge.MATH_COUNT, onSolved)
    }
}
