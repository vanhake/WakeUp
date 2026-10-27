package de.vanhake.wakeup.sleep

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.floor

enum class HistoryRange(val title: String) {
    WEEK("Woche"),
    MONTH("Monat"),
    YEAR("Jahr"),
    ALL("Alles"),
}

enum class HistoryMetric(val title: String) {
    SCORE("Schlafwert"),
    DURATION("Schlafdauer"),
    SNORING("Schnarchen"),
    APNEA("Atemaussetzer");

    /** Value of a night: score, hours of sleep, minutes of snoring or breathing pauses per hour. */
    fun of(night: NightSummary): Float? = when (this) {
        SCORE -> night.score.toFloat()
        DURATION -> night.sleepMinutes / 60f
        SNORING -> night.snoreMinutes.toFloat()
        APNEA -> night.apneasPerHour
    }
}

/** The evening a night belongs to: a recording started before noon counts for the day before. */
fun nightDate(start: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
    val time = Instant.ofEpochMilli(start).atZone(zone)
    return (if (time.hour < 12) time.minusDays(1) else time).toLocalDate()
}

/** Calendar period shown in the history chart, both ends inclusive. */
data class HistoryPeriod(val from: LocalDate, val to: LocalDate) {
    val days: Int get() = ChronoUnit.DAYS.between(from, to).toInt() + 1

    operator fun contains(date: LocalDate) = !date.isBefore(from) && !date.isAfter(to)

    /** Position of [date] as day index from [from]. */
    fun indexOf(date: LocalDate) = ChronoUnit.DAYS.between(from, date).toInt()
}

/**
 * The week (Monday to Sunday), month or year containing [today], moved back by [offset]
 * periods. [ALL][HistoryRange.ALL] spans from the first recorded night to today.
 */
fun historyPeriod(range: HistoryRange, today: LocalDate, offset: Int = 0, first: LocalDate? = null): HistoryPeriod =
    when (range) {
        HistoryRange.WEEK -> {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(offset.toLong())
            HistoryPeriod(monday, monday.plusDays(6))
        }
        HistoryRange.MONTH -> {
            val month = today.withDayOfMonth(1).minusMonths(offset.toLong())
            HistoryPeriod(month, month.with(TemporalAdjusters.lastDayOfMonth()))
        }
        HistoryRange.YEAR -> {
            val year = today.withDayOfYear(1).minusYears(offset.toLong())
            HistoryPeriod(year, year.with(TemporalAdjusters.lastDayOfYear()))
        }
        HistoryRange.ALL -> {
            val from = first?.takeIf { it.isBefore(today) } ?: today.minusDays(6)
            HistoryPeriod(from, today)
        }
    }

/**
 * One night per date for the chart, oldest first. Several recordings on one evening count
 * as the one with the most sleep; recordings without detected sleep are left out.
 */
fun dailyNights(nights: List<NightSummary>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, NightSummary>> =
    nights.filter { it.sleepMinutes > 0 }
        .groupBy { nightDate(it.start, zone) }
        .map { (date, list) -> date to list.maxBy { it.sleepMinutes } }
        .sortedBy { it.first }

/** Mean of all values within [windowDays] days centred on each point, for showing trends. */
fun rollingAverage(points: List<Pair<LocalDate, Float>>, windowDays: Int): List<Pair<LocalDate, Float>> {
    val half = windowDays / 2L
    return points.map { (date, _) ->
        val from = date.minusDays(half)
        val to = date.plusDays(half)
        date to points.filter { !it.first.isBefore(from) && !it.first.isAfter(to) }.map { it.second }.average().toFloat()
    }
}

// ---- Moon phase ----

private const val SYNODIC_MONTH_DAYS = 29.530588853
private const val DAY_MS = 86_400_000.0

/** A new moon: 6 January 2000, 18:14 UTC. */
private const val NEW_MOON_MS = 947_182_440_000L

/** Days since the last new moon at [millis], 0 until 29.53. */
fun moonAge(millis: Long): Double {
    val age = ((millis - NEW_MOON_MS) / DAY_MS) % SYNODIC_MONTH_DAYS
    return if (age < 0) age + SYNODIC_MONTH_DAYS else age
}

enum class MoonPhase(val title: String) { NEW("Neumond"), FULL("Vollmond") }

/** New or full moon if it is less than a day from the middle of the night after [date]. */
fun moonPhase(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): MoonPhase? {
    val age = moonAge(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    return when {
        abs(age - SYNODIC_MONTH_DAYS / 2) < 1.0 -> MoonPhase.FULL
        age < 1.0 || age > SYNODIC_MONTH_DAYS - 1.0 -> MoonPhase.NEW
        else -> null
    }
}

/** Nights (by [nightDate]) on which the moon is full, between [from] and [to]. */
fun fullMoonNights(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<LocalDate> {
    val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
    val firstFull = NEW_MOON_MS + SYNODIC_MONTH_DAYS / 2 * DAY_MS
    var k = floor((fromMs - firstFull) / (SYNODIC_MONTH_DAYS * DAY_MS)).toLong()
    val result = mutableListOf<LocalDate>()
    while (true) {
        val full = (firstFull + k * SYNODIC_MONTH_DAYS * DAY_MS).toLong()
        val date = nightDate(full, zone)
        if (date.isAfter(to)) break
        if (!date.isBefore(from)) result.add(date)
        k++
    }
    return result
}
