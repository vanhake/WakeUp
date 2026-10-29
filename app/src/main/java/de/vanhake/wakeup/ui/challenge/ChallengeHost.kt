package de.vanhake.wakeup.ui.challenge

import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import de.vanhake.wakeup.data.Alarm
import de.vanhake.wakeup.data.BarcodeRepository
import de.vanhake.wakeup.data.ChallengeType
import de.vanhake.wakeup.data.MathDifficulty

/** Number of hard math problems that replace a barcode that can't be scanned. */
private const val EMERGENCY_MATH_COUNT = 6

/** Shows the challenge configured for [alarm] and calls [onSolved] once it is completed. */
@Composable
fun ChallengeHost(alarm: Alarm, onSolved: () -> Unit, onInteraction: () -> Unit) {
    val context = LocalContext.current
    var emergency by remember { mutableStateOf(false) }
    val canShake = remember {
        hasAccelerometer(context.getSystemService(SensorManager::class.java))
    }
    val barcode = remember(alarm.barcodeId) { BarcodeRepository.get(alarm.barcodeId) }

    when {
        alarm.challenge == ChallengeType.NONE ->
            LaunchedEffect(Unit) { onSolved() }
        emergency ->
            MathChallenge(MathDifficulty.HARD, EMERGENCY_MATH_COUNT, onSolved)
        alarm.challenge == ChallengeType.MATH ->
            MathChallenge(alarm.mathDifficulty, alarm.mathCount, onSolved)
        alarm.challenge == ChallengeType.SHAKE && canShake ->
            ShakeChallenge(alarm.shakeCount, onSolved, onShake = onInteraction)
        alarm.challenge == ChallengeType.BARCODE && barcode != null ->
            BarcodeChallenge(barcode, onSolved, onEmergency = { emergency = true }, onScan = onInteraction)
        // No accelerometer, or the barcode was removed from the library.
        else ->
            MathChallenge(MathDifficulty.HARD, EMERGENCY_MATH_COUNT, onSolved)
    }
}
