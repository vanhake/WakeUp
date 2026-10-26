package de.vanhake.wakeup

import de.vanhake.wakeup.sleep.analysis.Burst
import de.vanhake.wakeup.sleep.analysis.BurstClassifier
import de.vanhake.wakeup.sleep.analysis.ClipType
import de.vanhake.wakeup.sleep.analysis.LiveDetector
import de.vanhake.wakeup.sleep.analysis.MinuteLevel
import de.vanhake.wakeup.sleep.analysis.NightRecording
import de.vanhake.wakeup.sleep.analysis.RestState
import de.vanhake.wakeup.sleep.analysis.ScreenEvent
import de.vanhake.wakeup.sleep.analysis.SleepAnalyzer
import de.vanhake.wakeup.sleep.analysis.SleepClip
import de.vanhake.wakeup.sleep.analysis.SoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepAnalysisTest {
    private val minute = 60_000L

    private fun snore(atMs: Long, peak: Float = -40f) =
        Burst(atMs, 1_200, peakDb = peak, meanDb = peak - 3, floorDb = -60f, lowRatio = 0.8f)

    private fun breath(atMs: Long) = Burst(atMs, 900, peakDb = -52f, meanDb = -54f, floorDb = -60f, lowRatio = 0.3f)

    private fun rustle(atMs: Long) = Burst(atMs, 600, peakDb = -48f, meanDb = -50f, floorDb = -60f, lowRatio = 0.3f)

    /** Snoring every 4 s from [fromMs] for [count] breaths. */
    private fun snoring(fromMs: Long, count: Int, peak: Float = -40f) = (0 until count).map { snore(fromMs + it * 4_000L, peak) }

    @Test
    fun regularLowLoudSoundsAreSnoring() {
        val bursts = snoring(0, 30)
        val c = BurstClassifier.classify(bursts)
        assertEquals(1, c.chains.size)
        assertTrue(bursts.all { c.typeOf(it) == SoundType.SNORE })
    }

    @Test
    fun quietBroadbandRhythmIsBreathing() {
        val bursts = (0 until 20).map { breath(it * 4_000L) }
        val c = BurstClassifier.classify(bursts)
        assertTrue(bursts.all { c.typeOf(it) == SoundType.BREATH })
    }

    @Test
    fun irregularSoundsAreMovementAndVeryLoudOnesNoise() {
        val bursts = listOf(rustle(0), rustle(700), rustle(15_000), Burst(40_000, 500, -20f, -22f, -60f, 0.5f))
        val c = BurstClassifier.classify(bursts)
        assertEquals(SoundType.MOVEMENT, c.typeOf(bursts[0]))
        assertEquals(SoundType.MOVEMENT, c.typeOf(bursts[2]))
        assertEquals(SoundType.NOISE, c.typeOf(bursts[3]))
    }

    @Test
    fun pauseInSnoringIsAnApneaCandidate() {
        val before = snoring(0, 10)
        val resume = before.last().endMs + 18_000
        val after = listOf(snore(resume, peak = -32f)) + snoring(resume + 4_000, 8)
        val c = BurstClassifier.classify(before + after)
        assertEquals(1, c.apneas.size)
        val a = c.apneas.single()
        assertEquals(18_000L, a.pauseMs)
        assertTrue(a.gasp)
    }

    @Test
    fun soundDuringThePauseIsNoApnea() {
        val before = snoring(0, 10)
        val gapStart = before.last().endMs
        val after = snoring(gapStart + 20_000, 8)
        val c = BurstClassifier.classify(before + rustle(gapStart + 8_000) + after)
        assertTrue(c.apneas.isEmpty())
    }

    @Test
    fun shortGapIsNoApnea() {
        val before = snoring(0, 10)
        val after = snoring(before.last().endMs + 7_000, 8)
        assertTrue(BurstClassifier.classify(before + after).apneas.isEmpty())
    }

    private fun night(): NightRecording {
        val start = 1_793_000_000_000L
        val hours = 8
        val bursts = mutableListOf<Burst>()
        // Restless for the first half hour, then quiet sleep with snoring from 1:00 to 1:30.
        for (m in 0 until 30) {
            bursts.add(rustle(m * minute + 5_000))
            bursts.add(rustle(m * minute + 25_000))
            bursts.add(rustle(m * minute + 45_000))
        }
        bursts.addAll(snoring(60 * minute, 450))
        val end = start + hours * 60 * minute
        return NightRecording(
            start = start,
            end = end,
            bursts = bursts.sortedBy { it.startMs },
            minutes = List(hours * 60) { MinuteLevel(-55f, -40f, -60f) },
            screen = listOf(ScreenEvent(start, true), ScreenEvent(start + 10 * minute, false)),
            alarmRingAt = end - 2 * minute,
            alarmDismissAt = end,
        )
    }

    @Test
    fun analysesANight() {
        val r = SleepAnalyzer.analyze(night())
        val onset = r.sleepOnset!!
        // Falls asleep when the restlessness stops after 30 minutes.
        assertEquals(30L, (onset - r.start) / minute)
        assertEquals(10L, (r.bedStart - r.start) / minute)
        assertEquals(20, r.latencyMinutes)
        assertEquals(r.end - 2 * minute, r.wakeUp)
        assertEquals(8 * 60 - 30 - 2, r.sleepMinutes)
        assertEquals(RestState.AWAKE, r.states.first())
        assertEquals(RestState.CALM, r.states[120])
        assertEquals(0, r.awakenings)
        // 450 snores × 1.2 s = 9 minutes.
        assertEquals(9, r.snoreMinutes)
        assertEquals(1, r.snoreEpisodes)
        assertTrue(r.apneas.isEmpty())
        assertEquals(15f, r.breathingRate!!, 0.1f)
        assertTrue(r.score in 80..100)
    }

    @Test
    fun screenUseInTheNightIsAnAwakening() {
        val n = night()
        val r = SleepAnalyzer.analyze(
            n.copy(screen = n.screen + ScreenEvent(n.start + 200 * minute, true) + ScreenEvent(n.start + 205 * minute, false)),
        )
        assertEquals(1, r.awakenings)
        assertEquals(RestState.AWAKE, r.states[202])
        assertEquals(8 * 60 - 30 - 2 - 5, r.sleepMinutes)
    }

    @Test
    fun alarmSoundIsIgnored() {
        val n = night()
        val ringStart = n.end - 2 * minute
        val alarm = (0 until 20).map { Burst(ringStart - n.start + it * 5_000L, 2_000, -10f, -12f, -60f, 0.4f) }
        val r = SleepAnalyzer.analyze(n.copy(bursts = n.bursts + alarm, ringing = listOf(ringStart..n.end)))
        assertTrue(r.disturbances.isEmpty())
    }

    @Test
    fun emptyNightHasNoSleep() {
        val r = SleepAnalyzer.analyze(
            NightRecording(0, 5 * minute, emptyList(), emptyList(), listOf(ScreenEvent(0, true))),
        )
        assertNull(r.sleepOnset)
        assertEquals(0, r.sleepMinutes)
        assertEquals(0, r.score)
    }

    @Test
    fun scoreRewardsEnoughSleep() {
        assertEquals(100, SleepAnalyzer.score(8 * 60, 0.95f, 0, 0, 10))
        assertTrue(SleepAnalyzer.score(5 * 60, 0.95f, 0, 0, 10) < SleepAnalyzer.score(7 * 60, 0.95f, 0, 0, 10))
        assertTrue(SleepAnalyzer.score(8 * 60, 0.7f, 60, 4, 50) < 70)
    }

    @Test
    fun nightJsonRoundTrip() {
        val n = night().copy(
            snoozes = 2,
            smartWake = true,
            ringing = listOf(1L..5L),
            clips = listOf(SleepClip(ClipType.APNEA, 1_793_000_100_000L, "a.wav", 24_000)),
            interrupted = true,
        )
        val back = NightRecording.fromJson(org.json.JSONObject(n.toJson().toString()))
        assertEquals(n.copy(bursts = emptyList()), back.copy(bursts = emptyList()))
        assertEquals(n.bursts.size, back.bursts.size)
        n.bursts.zip(back.bursts).forEach { (a, b) ->
            assertEquals(a.startMs, b.startMs)
            assertEquals(a.durationMs, b.durationMs)
            assertEquals(a.peakDb, b.peakDb, 0.05f)
            assertEquals(a.lowRatio, b.lowRatio, 0.001f)
        }
    }

    @Test
    fun liveDetectorRequestsApneaClip() {
        val live = LiveDetector(clipsEnabled = true)
        snoring(0, 6).forEach { live.onBurst(it) }
        val resume = snore(5 * 4_000L + 1_200 + 15_000)
        val request = live.onBurst(resume)
        assertNotNull(request)
        assertEquals(ClipType.APNEA, request!!.type)
        assertTrue(request.fromMs < resume.startMs - 10_000)
        assertTrue(request.toMs > resume.startMs)
    }

    @Test
    fun liveDetectorWithoutClipsRequestsNothing() {
        val live = LiveDetector(clipsEnabled = false)
        assertFalse((snoring(0, 20) + snore(200_000, -5f)).any { live.onBurst(it) != null })
    }

    @Test
    fun liveDetectorCountsMovements() {
        val live = LiveDetector(clipsEnabled = false)
        listOf(rustle(0), rustle(60_000), rustle(61_000), rustle(130_000)).forEach { live.onBurst(it) }
        assertEquals(3, live.movementsSince(30_000))
    }
}
