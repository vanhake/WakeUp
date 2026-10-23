package de.vanhake.wakeup.alarm

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Next point in time strictly after [now] at [hour]:[minute] on one of [days]
 * (any day if [days] is empty).
 */
fun nextTrigger(hour: Int, minute: Int, days: Set<DayOfWeek>, now: ZonedDateTime): ZonedDateTime {
    val time = LocalTime.of(hour, minute)
    val today = now.toLocalDate()
    for (offset in 0L..7L) {
        val date = today.plusDays(offset)
        if (days.isNotEmpty() && date.dayOfWeek !in days) continue
        val candidate = ZonedDateTime.of(date, time, now.zone)
        if (candidate.isAfter(now)) return candidate
    }
    error("unreachable: a matching day exists within 8 days")
}

/** "in 7 Std. 5 Min." style countdown. */
fun formatCountdown(from: ZonedDateTime, to: ZonedDateTime): String {
    val seconds = Duration.between(from, to).seconds.coerceAtLeast(0)
    val totalMinutes = (seconds + 59) / 60
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    val parts = buildList {
        if (days > 0) add("$days ${if (days == 1L) "Tag" else "Tagen"}")
        if (hours > 0) add("$hours Std.")
        if (minutes > 0 || isEmpty()) add("$minutes Min.")
    }
    return "in " + parts.joinToString(" ")
}

private val WEEKDAYS = setOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

fun formatDays(days: Set<DayOfWeek>): String = when {
    days.isEmpty() -> "Einmalig"
    days.size == 7 -> "Täglich"
    days == WEEKDAYS -> "Werktags"
    days == WEEKEND -> "Wochenende"
    else -> days.sorted().joinToString(" ") { shortDayName(it) }
}

fun shortDayName(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.SHORT, Locale.GERMAN).removeSuffix(".")

val WEEKDAY_SET: Set<DayOfWeek> get() = WEEKDAYS
val WEEKEND_SET: Set<DayOfWeek> get() = WEEKEND
