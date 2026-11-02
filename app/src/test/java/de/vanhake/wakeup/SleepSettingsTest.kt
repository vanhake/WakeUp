package de.vanhake.wakeup

import de.vanhake.wakeup.alarm.nextRegularRing
import de.vanhake.wakeup.data.Alarm
import de.vanhake.wakeup.sleep.RecordingMode
import de.vanhake.wakeup.sleep.SleepSettings
import de.vanhake.wakeup.sleep.expiredNights
import de.vanhake.wakeup.sleep.recordingDeadline
import de.vanhake.wakeup.sleep.shouldRecord
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class SleepSettingsTest {
    private val zone = ZoneId.of("Europe/Berlin")

    // 2026-10-23 is a Friday.
    private fun at(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone)
    private fun millis(day: Int, hour: Int, minute: Int) = at(day, hour, minute).toInstant().toEpochMilli()

    private val workdays = Alarm(id = 1, hour = 6, minute = 30, days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))

    @Test
    fun modes() {
        val thursdayEvening = at(22, 22, 30)
        val fridayEvening = at(23, 22, 30)
        val alarms = listOf(workdays)
        assertFalse(shouldRecord(SleepSettings(mode = RecordingMode.OFF), alarms, thursdayEvening))
        assertTrue(shouldRecord(SleepSettings(mode = RecordingMode.ALWAYS), alarms, fridayEvening))
        assertTrue(shouldRecord(SleepSettings(mode = RecordingMode.WITH_ALARM), alarms, thursdayEvening))
        // No alarm on Saturday morning.
        assertFalse(shouldRecord(SleepSettings(mode = RecordingMode.WITH_ALARM), alarms, fridayEvening))
        assertFalse(shouldRecord(SleepSettings(mode = RecordingMode.WITH_ALARM), listOf(workdays.copy(enabled = false)), thursdayEvening))
    }

    @Test
    fun deadlineFollowsTheAlarmOrTheEndTime() {
        val settings = SleepSettings(mode = RecordingMode.ALWAYS, endHour = 9, endMinute = 0)
        assertEquals(millis(23, 9, 30), recordingDeadline(settings, listOf(workdays), at(22, 22, 30)))
        assertEquals(millis(24, 9, 0), recordingDeadline(settings, listOf(workdays), at(23, 22, 30)))
        // Started after the end time: at most 16 hours.
        assertEquals(millis(24, 2, 0), recordingDeadline(settings, emptyList(), at(23, 10, 0)))
    }

    @Test
    fun smartWakeTargetIsSkipped() {
        val alarm = workdays.copy(smartWakeTarget = millis(23, 6, 30))
        // Woken at 6:15 by smart wake: the 6:30 ring of the same day must not ring again.
        assertEquals(millis(26, 6, 30), nextRegularRing(alarm, at(23, 6, 16)))
        // An old target has no effect.
        assertEquals(millis(26, 6, 30), nextRegularRing(alarm, at(24, 12, 0)))
        assertEquals(millis(23, 6, 30), nextRegularRing(workdays, at(23, 6, 16)))
    }

    @Test
    fun settingsJsonRoundTrip() {
        val s = SleepSettings(RecordingMode.WITH_ALARM, 23, 15, 8, 45, saveClips = false)
        assertEquals(s, SleepSettings.fromJson(JSONObject(s.toJson().toString())))
        assertFalse(SleepSettings.fromJson(JSONObject()).saveClips)
        assertFalse(SleepSettings().saveClips)
    }

    @Test
    fun keepDaysDefaults() {
        // New installations keep one year ...
        assertEquals(365, SleepSettings().keepDays)
        // ... settings saved before the option existed keep everything, so an update deletes nothing.
        assertEquals(0, SleepSettings.fromJson(JSONObject().put("mode", "ALWAYS")).keepDays)
        val s = SleepSettings(keepDays = 90)
        assertEquals(s, SleepSettings.fromJson(JSONObject(s.toJson().toString())))
    }

    @Test
    fun expiredNightsByAge() {
        val day = 24L * 60 * 60 * 1000
        val now = 1_000 * day
        val starts = listOf(now - 10 * day, now - 89 * day, now - 91 * day, now - 400 * day)
        assertEquals(listOf(now - 91 * day, now - 400 * day), expiredNights(starts, 90, now))
        assertEquals(listOf(now - 400 * day), expiredNights(starts, 365, now))
        assertTrue(expiredNights(starts, 0, now).isEmpty())
    }

    @Test
    fun alarmSmartWakeJsonRoundTrip() {
        val a = Alarm(id = 3, smartWakeMinutes = 20, smartWakeTarget = 1_793_000_000_000L)
        assertEquals(a, Alarm.fromJson(a.toJson()))
        assertEquals(0, Alarm.fromJson(JSONObject(Alarm(id = 1).toJson().apply { remove("smartWakeMinutes") }.toString())).smartWakeMinutes)
    }
}
