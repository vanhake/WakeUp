package de.vanhake.wakeup

import de.vanhake.wakeup.sleep.analysis.Burst
import de.vanhake.wakeup.sleep.analysis.BurstClassifier
import de.vanhake.wakeup.sleep.analysis.BurstDetector
import de.vanhake.wakeup.sleep.analysis.FRAME_SIZE
import de.vanhake.wakeup.sleep.analysis.FrameAnalyzer
import de.vanhake.wakeup.sleep.analysis.SAMPLE_RATE
import de.vanhake.wakeup.sleep.analysis.SoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class SleepAudioTest {
    private val random = Random(42)

    private fun sine(hz: Double, amplitude: Double, seconds: Double) =
        ShortArray((SAMPLE_RATE * seconds).toInt()) { (amplitude * sin(2 * PI * hz * it / SAMPLE_RATE)).toInt().toShort() }

    private fun noise(amplitude: Double, seconds: Double) =
        ShortArray((SAMPLE_RATE * seconds).toInt()) { (amplitude * (random.nextDouble() * 2 - 1)).toInt().toShort() }

    private fun bursts(vararg parts: ShortArray): List<Burst> {
        val audio = parts.reduce { a, b -> a + b }
        val analyzer = FrameAnalyzer()
        val detector = BurstDetector()
        val result = mutableListOf<Burst>()
        for (offset in 0..audio.size - FRAME_SIZE step FRAME_SIZE) {
            detector.add(analyzer.analyze(audio, offset))?.let(result::add)
        }
        detector.flush()?.let(result::add)
        return result
    }

    @Test
    fun lowFrequencySoundHasHighLowRatio() {
        val frame = FrameAnalyzer().analyze(sine(200.0, 8000.0, 0.02))
        assertTrue(frame.lowRatio > 0.9f)
        // Amplitude 8000 → RMS 8000/√2 of 32768 ≈ -15.3 dBFS.
        assertEquals(-15.3f, frame.db, 0.2f)
    }

    @Test
    fun hissHasLowLowRatio() {
        assertTrue(FrameAnalyzer().analyze(noise(8000.0, 0.02)).lowRatio < 0.2f)
    }

    @Test
    fun silenceIsVeryQuiet() {
        assertEquals(-100f, FrameAnalyzer().analyze(ShortArray(FRAME_SIZE)).db, 0.01f)
    }

    @Test
    fun detectsASoundAboveTheBackground() {
        val result = bursts(noise(50.0, 3.0), sine(150.0, 4000.0, 1.0), noise(50.0, 3.0))
        assertEquals(1, result.size)
        val b = result.single()
        assertEquals(3_000.0, b.startMs.toDouble(), 60.0)
        assertEquals(1_000.0, b.durationMs.toDouble(), 100.0)
        assertTrue(b.lowRatio > 0.8f)
        assertTrue(b.peakAboveFloor > 30f)
    }

    @Test
    fun ignoresSteadyBackgroundNoise() {
        assertTrue(bursts(noise(300.0, 20.0)).isEmpty())
    }

    @Test
    fun synthesizedSnoringAndBreathingAreRecognised() {
        val snoring = bursts(*Array(12) { listOf(noise(50.0, 2.8), sine(120.0, 3000.0, 1.2)) }.flatMap { it }.toTypedArray())
        val c1 = BurstClassifier.classify(snoring)
        assertEquals(12, snoring.size)
        assertTrue(snoring.all { c1.typeOf(it) == SoundType.SNORE })
        assertEquals(4_000.0, c1.chains.single().medianIntervalMs.toDouble(), 100.0)

        val breathing = bursts(*Array(12) { listOf(noise(50.0, 3.1), noise(400.0, 0.9)) }.flatMap { it }.toTypedArray())
        val c2 = BurstClassifier.classify(breathing)
        assertTrue(breathing.isNotEmpty() && breathing.all { c2.typeOf(it) == SoundType.BREATH })
    }
}
