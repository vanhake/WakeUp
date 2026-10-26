package de.vanhake.wakeup.ui.sleep

import androidx.compose.ui.graphics.Color
import de.vanhake.wakeup.sleep.analysis.ClipType
import de.vanhake.wakeup.sleep.analysis.RestState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Validated for colour-vision deficiencies against the dark surface.
val CalmColor = Color(0xFF3B6FE0)
val RestlessColor = Color(0xFF2FA39A)
val AwakeColor = Color(0xFFD9692A)
val SnoreColor = Color(0xFF9C6ADE)

val RestState.color: Color
    get() = when (this) {
        RestState.CALM -> CalmColor
        RestState.RESTLESS -> RestlessColor
        RestState.AWAKE -> AwakeColor
    }

val RestState.label: String
    get() = when (this) {
        RestState.CALM -> "Ruhig"
        RestState.RESTLESS -> "Unruhig"
        RestState.AWAKE -> "Wach"
    }

val ClipType.label: String
    get() = when (this) {
        ClipType.SNORE -> "Schnarchen"
        ClipType.APNEA -> "Atemaussetzer-Verdacht"
        ClipType.NOISE -> "Lautes Geräusch"
    }

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val SHORT_DATE = DateTimeFormatter.ofPattern("EE d.M.", Locale.GERMAN)

fun formatTime(millis: Long): String = TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** Date of the evening the night started (a recording after midnight belongs to the day before). */
fun formatNightDate(start: Long, short: Boolean = false): String {
    val time = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).let { if (it.hour < 12) it.minusDays(1) else it }
    return (if (short) SHORT_DATE else DATE).format(time).replace(".,", ",")
}

fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m Min."
        m == 0 -> "$h Std."
        else -> "$h Std. $m Min."
    }
}
