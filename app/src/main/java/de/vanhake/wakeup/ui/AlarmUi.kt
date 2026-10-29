package de.vanhake.wakeup.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.graphics.vector.ImageVector
import de.vanhake.wakeup.alarm.AlarmScheduler
import de.vanhake.wakeup.alarm.formatCountdown
import de.vanhake.wakeup.data.Alarm
import de.vanhake.wakeup.data.ChallengeType
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

val ChallengeType.icon: ImageVector
    get() = when (this) {
        ChallengeType.MATH -> Icons.Filled.Calculate
        ChallengeType.SHAKE -> Icons.Filled.Vibration
        ChallengeType.BARCODE -> Icons.Filled.QrCodeScanner
        ChallengeType.NONE -> Icons.Filled.Block
    }

val ChallengeType.title: String
    get() = when (this) {
        ChallengeType.MATH -> "Rechnen"
        ChallengeType.SHAKE -> "Schütteln"
        ChallengeType.BARCODE -> "Barcode"
        ChallengeType.NONE -> "Keine"
    }

/** Next time [alarm] will ring, or null if it is off. */
fun nextRing(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
    if (!alarm.enabled) return null
    val snoozedUntil = alarm.snoozedUntil
    val millis = if (snoozedUntil != null && snoozedUntil > now.toInstant().toEpochMilli()) {
        snoozedUntil
    } else {
        AlarmScheduler.nextRegularTrigger(alarm, now)
    }
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
}

fun toastNextRing(context: Context, alarm: Alarm) {
    val next = nextRing(alarm) ?: return
    Toast.makeText(context, "Wecker klingelt ${formatCountdown(ZonedDateTime.now(), next)}", Toast.LENGTH_SHORT).show()
}

/** Keeps an alarm being edited across configuration changes. */
val AlarmSaver = Saver<Alarm?, String>(
    save = { it?.toJson()?.toString() ?: "" },
    restore = { if (it.isEmpty()) null else Alarm.fromJson(JSONObject(it)) },
)
