package de.vanhake.wakeup

import de.vanhake.wakeup.data.Alarm
import de.vanhake.wakeup.data.ChallengeType
import de.vanhake.wakeup.data.MathDifficulty
import de.vanhake.wakeup.data.SavedBarcode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class AlarmJsonTest {
    @Test
    fun roundTrip() {
        val alarms = listOf(
            Alarm(id = 1),
            Alarm(
                id = 2, hour = 6, minute = 15, label = "Arbeit",
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), enabled = false,
                challenge = ChallengeType.BARCODE, mathDifficulty = MathDifficulty.HARD, mathCount = 5,
                shakeCount = 80, barcodeId = 3, maxSnoozes = 0, snoozeMinutes = 9,
                vibrate = false, volume = 0.5f, ringtoneUri = "content://media/internal/audio/media/12",
                snoozesUsed = 1, snoozedUntil = 1_793_000_000_000L,
            ),
        )
        assertEquals(alarms, Alarm.listFromJson(Alarm.listToJson(alarms)))
    }

    @Test
    fun snoozesLeftNeverNegative() {
        assertEquals(0, Alarm(id = 1, maxSnoozes = 2, snoozesUsed = 5).snoozesLeft)
        assertEquals(2, Alarm(id = 1, maxSnoozes = 3, snoozesUsed = 1).snoozesLeft)
    }

    @Test
    fun barcodeRoundTrip() {
        val barcodes = listOf(
            SavedBarcode(1, "Zahnpasta im Bad", "4006381333931"),
            SavedBarcode(2, "Kaffee", "https://example.org/qr?x=\"1\""),
        )
        assertEquals(barcodes, SavedBarcode.listFromJson(SavedBarcode.listToJson(barcodes)))
    }
}
