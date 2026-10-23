package de.vanhake.wakeup

import de.vanhake.wakeup.alarm.formatCountdown
import de.vanhake.wakeup.alarm.formatDays
import de.vanhake.wakeup.alarm.nextTrigger
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmTimeTest {
    private val zone = ZoneId.of("Europe/Berlin")

    // 2026-10-23 is a Friday.
    private fun at(day: Int, hour: Int, minute: Int, month: Int = 10) =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone)

    @Test
    fun oneTimeAlarmLaterToday() {
        assertEquals(at(23, 7, 30), nextTrigger(7, 30, emptySet(), at(23, 6, 0)))
    }

    @Test
    fun oneTimeAlarmAlreadyPassedRingsTomorrow() {
        assertEquals(at(24, 7, 30), nextTrigger(7, 30, emptySet(), at(23, 8, 0)))
    }

    @Test
    fun exactlyNowRingsTomorrow() {
        assertEquals(at(24, 7, 30), nextTrigger(7, 30, emptySet(), at(23, 7, 30)))
    }

    @Test
    fun weekdayAlarmOnFridayEveningSkipsWeekend() {
        val weekdays = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        assertEquals(at(26, 6, 45), nextTrigger(6, 45, weekdays, at(23, 20, 0)))
    }

    @Test
    fun singleDaySameDayLaterOneWeekAhead() {
        assertEquals(at(30, 9, 0), nextTrigger(9, 0, setOf(DayOfWeek.FRIDAY), at(23, 10, 0)))
    }

    @Test
    fun keepsLocalTimeAcrossDstChange() {
        // DST ends in Germany on 2026-10-25.
        val next = nextTrigger(7, 0, setOf(DayOfWeek.MONDAY), at(23, 12, 0))
        assertEquals(at(26, 7, 0), next)
        assertEquals(7, next.hour)
    }

    @Test
    fun countdownRoundsUpToMinutes() {
        assertEquals("in 1 Min.", formatCountdown(at(23, 6, 0), at(23, 6, 0).plusSeconds(10)))
        assertEquals("in 7 Std. 5 Min.", formatCountdown(at(23, 0, 0), at(23, 7, 5)))
        assertEquals("in 1 Tag 2 Std.", formatCountdown(at(23, 0, 0), at(24, 2, 0)))
    }

    @Test
    fun dayFormatting() {
        assertEquals("Einmalig", formatDays(emptySet()))
        assertEquals("Täglich", formatDays(DayOfWeek.values().toSet()))
        assertEquals("Wochenende", formatDays(setOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)))
        assertEquals("Mo Mi", formatDays(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)))
    }
}
