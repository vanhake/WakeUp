package de.vanhake.wakeup

import de.vanhake.wakeup.sleep.HistoryPeriod
import de.vanhake.wakeup.sleep.HistoryRange
import de.vanhake.wakeup.sleep.MoonPhase
import de.vanhake.wakeup.sleep.NightSummary
import de.vanhake.wakeup.sleep.dailyNights
import de.vanhake.wakeup.sleep.fullMoonNights
import de.vanhake.wakeup.sleep.historyPeriod
import de.vanhake.wakeup.sleep.moonPhase
import de.vanhake.wakeup.sleep.nightDate
import de.vanhake.wakeup.sleep.rollingAverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SleepHistoryTest {
    private val zone = ZoneId.of("Europe/Berlin")

    // 2026-10-27 is a Tuesday.
    private val today = LocalDate.of(2026, 10, 27)

    private fun millis(day: Int, hour: Int) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun night(start: Long, sleepMinutes: Int, score: Int) =
        NightSummary(start, start + 8 * 3_600_000L, sleepMinutes, 0, null, score, false)

    @Test
    fun periods() {
        assertEquals(HistoryPeriod(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)), historyPeriod(HistoryRange.WEEK, today))
        assertEquals(HistoryPeriod(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)), historyPeriod(HistoryRange.WEEK, today, 1))
        assertEquals(HistoryPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), historyPeriod(HistoryRange.MONTH, today, 1))
        assertEquals(HistoryPeriod(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)), historyPeriod(HistoryRange.YEAR, today, 1))
        assertEquals(
            HistoryPeriod(LocalDate.of(2026, 3, 2), today),
            historyPeriod(HistoryRange.ALL, today, first = LocalDate.of(2026, 3, 2)),
        )
        assertEquals(7, historyPeriod(HistoryRange.ALL, today).days)
    }

    @Test
    fun nightBelongsToTheEvening() {
        assertEquals(LocalDate.of(2026, 10, 26), nightDate(millis(26, 22), zone))
        assertEquals(LocalDate.of(2026, 10, 26), nightDate(millis(27, 1), zone))
        assertEquals(LocalDate.of(2026, 10, 27), nightDate(millis(27, 14), zone))
    }

    @Test
    fun oneNightPerDateWithoutEmptyRecordings() {
        val main = night(millis(26, 22), 412, 79)
        val nap = night(millis(26, 14), 30, 10)
        val empty = night(millis(25, 22), 0, 0)
        assertEquals(listOf(LocalDate.of(2026, 10, 26) to main), dailyNights(listOf(nap, main, empty), zone))
    }

    @Test
    fun fullMoons() {
        // Full moon on 26 October 2026 at 05:12 in Berlin: the night from the 25th.
        assertEquals(listOf(LocalDate.of(2026, 10, 25)), fullMoonNights(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), zone))
        assertEquals(13, fullMoonNights(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), zone).size)
        assertEquals(MoonPhase.FULL, moonPhase(LocalDate.of(2026, 10, 25), zone))
        // New moon on 10 October 2026 at 17:50 in Berlin.
        assertEquals(MoonPhase.NEW, moonPhase(LocalDate.of(2026, 10, 10), zone))
        assertNull(moonPhase(LocalDate.of(2026, 10, 18), zone))
    }

    @Test
    fun rollingAverageUsesNeighbouringDays() {
        val points = listOf(
            LocalDate.of(2026, 1, 1) to 1f,
            LocalDate.of(2026, 1, 2) to 3f,
            LocalDate.of(2026, 1, 20) to 10f,
        )
        assertEquals(listOf(2f, 2f, 10f), rollingAverage(points, 7).map { it.second })
    }
}
