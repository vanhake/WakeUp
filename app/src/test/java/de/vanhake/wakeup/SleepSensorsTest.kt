package de.vanhake.wakeup

import de.vanhake.wakeup.sleep.analysis.Burst
import de.vanhake.wakeup.sleep.analysis.BurstClassifier
import de.vanhake.wakeup.sleep.analysis.ClipType
import de.vanhake.wakeup.sleep.analysis.LabelWindow
import de.vanhake.wakeup.sleep.analysis.LiveDetector
import de.vanhake.wakeup.sleep.analysis.MinuteLevel
import de.vanhake.wakeup.sleep.analysis.NightRecording
import de.vanhake.wakeup.sleep.analysis.RestState
import de.vanhake.wakeup.sleep.analysis.ScreenEvent
import de.vanhake.wakeup.sleep.analysis.SleepAnalyzer
import de.vanhake.wakeup.sleep.analysis.SoundLabel
import de.vanhake.wakeup.sleep.analysis.SoundType
import de.vanhake.wakeup.sleep.analysis.labelFromScores
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sound classifier labels, light sensor and accelerometer in the sleep analysis. */
class SleepSensorsTest {
    private val minute = 60_000L

    private fun scores(vararg pairs: Pair<Int, Float>) = FloatArray(521).also { s -> pairs.forEach { (i, v) -> s[i] = v } }

    private fun snore(atMs: Long) = Burst(atMs, 1_200, -40f, -43f, -60f, 0.8f)

    private fun rustle(atMs: Long) = Burst(atMs, 600, -48f, -50f, -60f, 0.3f)

    private fun label(atMs: Long, label: SoundLabel, score: Float = 0.8f) = LabelWindow(atMs - 100, label, score)

    @Test
    fun yamnetScoresMapToNightLabels() {
        assertEquals(SoundLabel.SNORE to 0.6f, labelFromScores(scores(38 to 0.6f, 0 to 0.1f)))
        assertEquals(SoundLabel.COUGH, labelFromScores(scores(43 to 0.5f, 38 to 0.3f))?.first)
        assertEquals(SoundLabel.SPEECH, labelFromScores(scores(2 to 0.9f))?.first)
        assertEquals(SoundLabel.MUSIC_TV, labelFromScores(scores(518 to 0.7f))?.first)
        // Not confident enough, or a class that does not matter at night.
        assertNull(labelFromScores(scores(0 to 0.2f)))
        assertNull(labelFromScores(scores(494 to 0.99f)))
    }

    @Test
    fun labelsOverrideTheHeuristic() {
        val cough = rustle(1_000)
        val talk = rustle(30_000)
        val lonelySnore = snore(60_000)
        val c = BurstClassifier.classify(
            listOf(cough, talk, lonelySnore),
            listOf(label(1_000, SoundLabel.COUGH), label(30_000, SoundLabel.SPEECH), label(60_000, SoundLabel.SNORE)),
        )
        assertEquals(SoundType.COUGH, c.typeOf(cough))
        assertEquals(SoundType.SPEECH, c.typeOf(talk))
        assertEquals(SoundType.SNORE, c.typeOf(lonelySnore))
    }

    @Test
    fun rhythmicSpeechIsNoBreathingChain() {
        val bursts = (0 until 10).map { snore(it * 4_000L) }
        val c = BurstClassifier.classify(bursts, bursts.take(6).map { label(it.startMs, SoundLabel.SPEECH) })
        assertTrue(c.chains.isEmpty())
        assertEquals(SoundType.SPEECH, c.typeOf(bursts[0]))
    }

    @Test
    fun gaspLabelMarksTheApnea() {
        val before = (0 until 10).map { Burst(it * 4_000L, 1_000, -40f, -43f, -60f, 0.8f) }
        val resume = before.last().endMs + 15_000
        val after = (0 until 6).map { Burst(resume + it * 4_000L, 1_000, -40f, -43f, -60f, 0.8f) }
        val withoutLabel = BurstClassifier.classify(before + after)
        assertEquals(false, withoutLabel.apneas.single().gasp)
        val withLabel = BurstClassifier.classify(before + after, listOf(label(resume, SoundLabel.GASP, 0.4f)))
        assertEquals(true, withLabel.apneas.single().gasp)
    }

    private fun quietNight(): NightRecording {
        val start = 1_793_000_000_000L
        return NightRecording(
            start = start,
            end = start + 8 * 60 * minute,
            bursts = emptyList(),
            minutes = List(8 * 60) { MinuteLevel(-55f, -45f, -60f) },
            screen = listOf(ScreenEvent(start, false)),
        )
    }

    @Test
    fun countsCoughsAndSleepTalking() {
        val n = quietNight()
        val coughs = listOf(rustle(100 * minute), rustle(100 * minute + 5_000), rustle(300 * minute))
        val talk = listOf(rustle(200 * minute), rustle(200 * minute + 10_000), rustle(250 * minute))
        val r = SleepAnalyzer.analyze(
            n.copy(
                bursts = coughs + talk,
                labels = coughs.map { label(it.startMs, SoundLabel.COUGH) } + talk.map { label(it.startMs, SoundLabel.SPEECH) },
                classifierUsed = true,
            ),
        )
        assertEquals(3, r.coughs)
        assertEquals(2, r.sleepTalkEpisodes)
        assertTrue(r.classifierUsed)
        // Two coughs within one minute make it restless.
        assertEquals(RestState.RESTLESS, r.states[100])
    }

    @Test
    fun lightsOffMarksGoingToBed() {
        val n = quietNight()
        val light = List(8 * 60) { m -> if (m < 25) 80f else 0.5f }
        val r = SleepAnalyzer.analyze(n.copy(light = light))
        assertEquals(n.start + 25 * minute, r.lightsOff)
        assertEquals(r.lightsOff, r.bedStart)
        assertNull(SleepAnalyzer.analyze(n.copy(light = List(8 * 60) { null })).lightsOff)
    }

    @Test
    fun mattressMovementsMakeRestless() {
        val n = quietNight()
        val r = SleepAnalyzer.analyze(n.copy(motion = listOf(120 * minute + 1_000, 120 * minute + 20_000)))
        assertEquals(RestState.RESTLESS, r.states[120])
        assertTrue(r.usedMotion)
    }

    @Test
    fun newFieldsSurviveJson() {
        val n = quietNight().copy(
            labels = listOf(LabelWindow(5_000, SoundLabel.SNORE, 0.42f)),
            classifierUsed = true,
            light = listOf(12.5f, null, 0f),
            motion = listOf(1_000L, 90_000L),
        )
        val back = NightRecording.fromJson(JSONObject(n.toJson().toString()))
        assertEquals(n, back)
        // Nights stored by the previous version have none of these fields.
        val old = JSONObject(quietNight().toJson().toString()).apply {
            remove("labels"); remove("classifierUsed"); remove("light"); remove("motion")
        }
        assertEquals(quietNight(), NightRecording.fromJson(old))
    }

    @Test
    fun liveDetectorKeepsCoughAndSpeechSamples() {
        val live = LiveDetector(clipsEnabled = true)
        assertEquals(ClipType.COUGH, live.onLabel(LabelWindow(10_000, SoundLabel.COUGH, 0.7f))?.type)
        assertNull(live.onLabel(LabelWindow(20_000, SoundLabel.COUGH, 0.7f)))
        assertEquals(ClipType.SPEECH, live.onLabel(LabelWindow(30_000, SoundLabel.SPEECH, 0.7f))?.type)
        assertNull(live.onLabel(LabelWindow(40_000, SoundLabel.SNORE, 0.7f)))
    }
}
