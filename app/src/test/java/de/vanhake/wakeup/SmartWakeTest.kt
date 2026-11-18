package de.vanhake.wakeup

import de.vanhake.wakeup.sleep.analysis.SmartWake
import de.vanhake.wakeup.sleep.analysis.SmartWakeTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmartWakeTest {
    private val minute = 60_000L

    /** Window from minute 150 on (e.g. 30 minutes before a ring time at minute 180). */
    private val windowStart = 150 * minute

    /** A calm night: one movement every 5 minutes, then [tail] for the minutes from 150 on. */
    private fun night(vararg tail: Int, before: (Int) -> Int = { if (it % 5 == 0) 1 else 0 }): List<Int> =
        List(150) { before(it) } + tail.toList()

    @Test
    fun ordinaryMovementAtTheStartOfTheWindowDoesNotRing() {
        // The old rule (2 movements in 3 minutes) rang right away here.
        assertNull(SmartWake.decide(night(1), emptyList(), windowStart, null))
        assertNull(SmartWake.decide(night(1, 1), emptyList(), windowStart, null))
    }

    @Test
    fun restlessnessBeforeTheWindowDoesNotRingAtItsStart() {
        val moves = List(148) { 0 } + listOf(5, 5) + listOf(4)
        assertNull(SmartWake.decide(moves, emptyList(), windowStart, null))
    }

    @Test
    fun turningOverOnceDoesNotRing() {
        assertNull(SmartWake.decide(night(0, 4, 0), emptyList(), windowStart, null))
        assertNull(SmartWake.decide(night(0, 4, 0, 1), emptyList(), windowStart, null))
    }

    @Test
    fun lastingRestlessnessInTheWindowRings() {
        assertNull(SmartWake.decide(night(0, 0, 3), emptyList(), windowStart, null))
        val d = SmartWake.decide(night(0, 0, 3, 2), emptyList(), windowStart, null)
        assertNotNull(d)
        assertEquals(SmartWakeTrigger.MOVEMENT, d!!.trigger)
        assertEquals(listOf(3, 2), d.restlessMoves)
        assertEquals(2, d.threshold)
        assertEquals(0.2f, d.baseline, 0.001f)
    }

    @Test
    fun noisyRoomRaisesTheBar() {
        // Heating, traffic or a partner: about 2 "movements" every minute all night.
        val noisy = { _: Int -> 2 }
        assertEquals(6, SmartWake.threshold(2f))
        assertNull(SmartWake.decide(night(3, 3, 3, 3, before = noisy), emptyList(), windowStart, null))
        assertNotNull(SmartWake.decide(night(2, 6, 7, before = noisy), emptyList(), windowStart, null))
    }

    @Test
    fun feltMovementsCount() {
        val felt = listOf(150, 151).flatMap { m -> listOf(m * minute + 5_000, m * minute + 40_000) }
        assertNotNull(SmartWake.decide(night(0, 0), felt, windowStart, null))
        assertNull(SmartWake.decide(night(0, 0), felt.take(3), windowStart, null))
    }

    @Test
    fun usingThePhoneRings() {
        assertNull(SmartWake.decide(night(), emptyList(), windowStart, 5_000))
        assertEquals(SmartWakeTrigger.SCREEN, SmartWake.decide(night(), emptyList(), windowStart, 20_000)?.trigger)
    }
}
