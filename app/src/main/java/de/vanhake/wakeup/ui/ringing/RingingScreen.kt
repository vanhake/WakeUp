package de.vanhake.wakeup.ui.ringing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vanhake.wakeup.alarm.AlarmService
import de.vanhake.wakeup.data.Alarm
import de.vanhake.wakeup.data.AlarmRepository
import de.vanhake.wakeup.data.ChallengeType
import de.vanhake.wakeup.ui.challenge.ChallengeHost
import de.vanhake.wakeup.ui.components.BigButton
import de.vanhake.wakeup.ui.theme.SurfaceHigh
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Ringing screen for the alarm that currently rings; shown by both activities. */
@Composable
fun RingingRoute() {
    val context = LocalContext.current
    val ringingId by AlarmService.ringingAlarmId.collectAsState()
    val alarms by AlarmRepository.alarms.collectAsState()
    val alarm = alarms.firstOrNull { it.id == ringingId } ?: return
    RingingScreen(
        alarm = alarm,
        onSnooze = { AlarmService.snooze(context) },
        onSolved = { AlarmService.dismiss(context) },
        onChallengeActive = { AlarmService.setQuiet(context, it) },
    )
}

/** Without a touch for this long the challenge is abandoned and the alarm is loud again. */
private const val INACTIVITY_TIMEOUT_MS = 30_000L

@Composable
fun RingingScreen(
    alarm: Alarm,
    onSnooze: () -> Unit,
    onSolved: () -> Unit,
    onChallengeActive: (Boolean) -> Unit,
) {
    var inChallenge by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val currentOnChallengeActive by rememberUpdatedState(onChallengeActive)
    // Walking to the barcode takes a while without touching the screen, and it should stay loud.
    val quietDuringChallenge = alarm.challenge != ChallengeType.BARCODE

    LaunchedEffect(inChallenge) {
        if (quietDuringChallenge) currentOnChallengeActive(inChallenge)
    }
    LaunchedEffect(inChallenge, lastInteraction) {
        if (inChallenge && quietDuringChallenge) {
            delay(INACTIVITY_TIMEOUT_MS)
            inChallenge = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastInteraction = System.currentTimeMillis()
                    }
                }
            }
            .safeDrawingPadding(),
    ) {
        if (inChallenge) {
            ChallengeHost(
                alarm = alarm,
                onSolved = onSolved,
                onInteraction = { lastInteraction = System.currentTimeMillis() },
            )
        } else {
            RingingContent(alarm, onSnooze = onSnooze, onStop = {
                lastInteraction = System.currentTimeMillis()
                inChallenge = true
            })
        }
    }
}

@Composable
private fun RingingContent(alarm: Alarm, onSnooze: () -> Unit, onStop: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            now.format(DateTimeFormatter.ofPattern("HH:mm")),
            fontSize = 88.sp,
            fontWeight = FontWeight.Light,
        )
        Text(
            alarm.label.ifBlank { "Guten Morgen!" },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1.5f))

        if (alarm.maxSnoozes > 0) {
            val left = alarm.snoozesLeft
            BigButton(
                text = if (left > 0) {
                    "Schlummern · ${alarm.snoozeMinutes} Min ($left übrig)"
                } else {
                    "Kein Schlummern mehr"
                },
                icon = Icons.Filled.Snooze,
                onClick = onSnooze,
                enabled = left > 0,
                height = 80.dp,
                containerColor = SurfaceHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
            // Generous gap so a sleepy thumb doesn't hit the wrong button.
            Spacer(Modifier.height(40.dp))
        }
        BigButton(
            text = when (alarm.challenge) {
                ChallengeType.MATH -> "Ausschalten · Rechnen"
                ChallengeType.SHAKE -> "Ausschalten · Schütteln"
                ChallengeType.BARCODE -> "Ausschalten · Scannen"
            },
            icon = Icons.Filled.AlarmOff,
            onClick = onStop,
            height = 112.dp,
        )
    }
}
