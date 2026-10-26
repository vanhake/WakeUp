package de.vanhake.wakeup.sleep

import de.vanhake.wakeup.alarm.nextRegularRing
import de.vanhake.wakeup.alarm.nextTrigger
import de.vanhake.wakeup.data.Alarm
import org.json.JSONObject
import java.time.Duration
import java.time.ZonedDateTime

enum class RecordingMode(val title: String) {
    OFF("Aus"),
    WITH_ALARM("Bei Wecker"),
    ALWAYS("Immer"),
}

data class SleepSettings(
    val mode: RecordingMode = RecordingMode.OFF,
    /** Automatic recording starts at this time. */
    val startHour: Int = 22,
    val startMinute: Int = 30,
    /** Without an alarm, automatic recording ends at this time. */
    val endHour: Int = 9,
    val endMinute: Int = 0,
    /** Store short audio samples of snoring, breathing pauses and noise for 24 hours. */
    val saveClips: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("mode", mode.name)
        put("startHour", startHour)
        put("startMinute", startMinute)
        put("endHour", endHour)
        put("endMinute", endMinute)
        put("saveClips", saveClips)
    }

    companion object {
        fun fromJson(o: JSONObject) = SleepSettings(
            mode = RecordingMode.entries.firstOrNull { it.name == o.optString("mode") } ?: RecordingMode.OFF,
            startHour = o.optInt("startHour", 22),
            startMinute = o.optInt("startMinute", 30),
            endHour = o.optInt("endHour", 9),
            endMinute = o.optInt("endMinute", 0),
            saveClips = o.optBoolean("saveClips", true),
        )
    }
}

/** An alarm ringing within this time after the start counts as "tonight's" alarm. */
val MAX_NIGHT: Duration = Duration.ofHours(16)

/** The next time any enabled alarm rings strictly after [now], or null. */
fun nextAlarmRing(alarms: List<Alarm>, now: ZonedDateTime): Long? = alarms.filter { it.enabled }.minOfOrNull { alarm ->
    val snoozed = alarm.snoozedUntil
    if (snoozed != null && snoozed > now.toInstant().toEpochMilli()) {
        snoozed
    } else {
        nextRegularRing(alarm, now)
    }
}

/** Whether the automatic recording should start at [now]. */
fun shouldRecord(settings: SleepSettings, alarms: List<Alarm>, now: ZonedDateTime): Boolean = when (settings.mode) {
    RecordingMode.OFF -> false
    RecordingMode.ALWAYS -> true
    RecordingMode.WITH_ALARM -> nextAlarmRing(alarms, now)
        ?.let { it - now.toInstant().toEpochMilli() <= MAX_NIGHT.toMillis() } == true
}

/**
 * When a recording started at [now] ends at the latest: some time after tonight's alarm
 * (normally the recording stops when the alarm is dismissed), otherwise at the configured end time.
 */
fun recordingDeadline(settings: SleepSettings, alarms: List<Alarm>, now: ZonedDateTime): Long {
    val nowMillis = now.toInstant().toEpochMilli()
    val alarm = nextAlarmRing(alarms, now)?.takeIf { it - nowMillis <= MAX_NIGHT.toMillis() }
    if (alarm != null) return alarm + Duration.ofHours(3).toMillis()
    val end = nextTrigger(settings.endHour, settings.endMinute, emptySet(), now).toInstant().toEpochMilli()
    // Started shortly before or after the end time (e.g. a nap): give it a few hours, but not a whole day.
    return end.coerceIn(nowMillis + Duration.ofHours(3).toMillis(), nowMillis + MAX_NIGHT.toMillis())
}
