package de.vanhake.wakeup.sleep.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

const val SAMPLE_RATE = 16_000

/** 20 ms of audio. */
const val FRAME_SIZE = 320
const val FRAME_MS = 20

private const val FFT_SIZE = 512
private const val HZ_PER_BIN = SAMPLE_RATE.toDouble() / FFT_SIZE
private const val SILENCE_DB = -100f

/** Loudness (dBFS) and the share of energy between 60 and 1000 Hz of one frame. */
data class FrameFeatures(val db: Float, val lowRatio: Float)

/** Computes [FrameFeatures] for consecutive 20-ms frames of 16-kHz mono audio. */
class FrameAnalyzer {
    private val re = DoubleArray(FFT_SIZE)
    private val im = DoubleArray(FFT_SIZE)
    private val window = DoubleArray(FRAME_SIZE) { 0.5 - 0.5 * cos(2 * PI * it / (FRAME_SIZE - 1)) }
    private val lowBins = (60 / HZ_PER_BIN).toInt() + 1..(1000 / HZ_PER_BIN).toInt()
    private val allBins = lowBins.first until FFT_SIZE / 2

    fun analyze(samples: ShortArray, offset: Int = 0): FrameFeatures {
        var sum = 0.0
        for (i in 0 until FRAME_SIZE) {
            val s = samples[offset + i].toDouble()
            sum += s * s
            re[i] = s * window[i]
            im[i] = 0.0
        }
        re.fill(0.0, FRAME_SIZE, FFT_SIZE)
        im.fill(0.0, FRAME_SIZE, FFT_SIZE)
        val rms = sqrt(sum / FRAME_SIZE) / 32768.0
        val db = if (rms > 0) max(SILENCE_DB, (20 * log10(rms)).toFloat()) else SILENCE_DB

        fft(re, im)
        var low = 0.0
        var total = 0.0
        for (k in allBins) {
            val p = re[k] * re[k] + im[k] * im[k]
            total += p
            if (k in lowBins) low += p
        }
        return FrameFeatures(db, if (total > 0) (low / total).toFloat() else 0f)
    }
}

/** In-place iterative radix-2 FFT; the size must be a power of two. */
internal fun fft(re: DoubleArray, im: DoubleArray) {
    val n = re.size
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) {
            j = j xor bit
            bit = bit shr 1
        }
        j = j xor bit
        if (i < j) {
            var t = re[i]; re[i] = re[j]; re[j] = t
            t = im[i]; im[i] = im[j]; im[j] = t
        }
    }
    var len = 2
    while (len <= n) {
        val angle = -2 * PI / len
        val wRe = cos(angle)
        val wIm = sin(angle)
        for (start in 0 until n step len) {
            var curRe = 1.0
            var curIm = 0.0
            for (k in 0 until len / 2) {
                val a = start + k
                val b = a + len / 2
                val tRe = re[b] * curRe - im[b] * curIm
                val tIm = re[b] * curIm + im[b] * curRe
                re[b] = re[a] - tRe
                im[b] = im[a] - tIm
                re[a] += tRe
                im[a] += tIm
                val nextRe = curRe * wRe - curIm * wIm
                curIm = curRe * wIm + curIm * wRe
                curRe = nextRe
            }
        }
        len = len shl 1
    }
}

/**
 * A contiguous sound clearly above the background noise.
 * [startMs] is relative to the start of the recording.
 */
data class Burst(
    val startMs: Long,
    val durationMs: Int,
    val peakDb: Float,
    val meanDb: Float,
    /** Background noise level when the sound started. */
    val floorDb: Float,
    val lowRatio: Float,
) {
    val endMs: Long get() = startMs + durationMs
    val peakAboveFloor: Float get() = peakDb - floorDb
}

/**
 * Tracks the background noise level and groups louder frames into [Burst]s.
 * Feed frames in order with [add]; finished bursts are returned as soon as they end.
 */
class BurstDetector {
    var floorDb: Float = Float.NaN
        private set

    private var frameIndex = 0L
    private var inBurst = false
    private var burstStart = 0L
    private var lastActive = 0L
    private var peak = SILENCE_DB
    private var powerSum = 0.0
    private var lowWeighted = 0.0
    private var activeFrames = 0
    private var startFloor = 0f

    /** Processes one frame and returns a burst if one just ended. */
    fun add(frame: FrameFeatures): Burst? {
        val index = frameIndex++
        val db = frame.db
        if (floorDb.isNaN()) floorDb = db
        // Follow drops in the background level quickly, rises only slowly (0.5 dB/s).
        floorDb = if (db < floorDb) floorDb + (db - floorDb) * 0.05f else floorDb + 0.01f

        var finished: Burst? = null
        val above = db - floorDb
        if (inBurst) {
            if (above >= CONTINUE_DB) {
                accumulate(index, frame)
                if ((index - burstStart + 1) * FRAME_MS >= MAX_BURST_MS) finished = finish()
            } else if (index - lastActive > MAX_GAP_FRAMES) {
                finished = finish()
            }
        } else if (above >= START_DB) {
            inBurst = true
            burstStart = index
            peak = SILENCE_DB
            powerSum = 0.0
            lowWeighted = 0.0
            activeFrames = 0
            startFloor = floorDb
            accumulate(index, frame)
        }
        return finished
    }

    /** Ends a burst that is still open, e.g. when recording stops. */
    fun flush(): Burst? = if (inBurst) finish() else null

    private fun accumulate(index: Long, frame: FrameFeatures) {
        lastActive = index
        val power = Math.pow(10.0, frame.db / 10.0)
        powerSum += power
        lowWeighted += power * frame.lowRatio
        activeFrames++
        if (frame.db > peak) peak = frame.db
    }

    private fun finish(): Burst? {
        inBurst = false
        val frames = (lastActive - burstStart + 1).toInt()
        if (frames < MIN_FRAMES) return null
        return Burst(
            startMs = burstStart * FRAME_MS,
            durationMs = frames * FRAME_MS,
            peakDb = peak,
            meanDb = (10 * log10(powerSum / activeFrames)).toFloat(),
            floorDb = startFloor,
            lowRatio = if (powerSum > 0) (lowWeighted / powerSum).toFloat() else 0f,
        )
    }

    private companion object {
        const val START_DB = 6f
        const val CONTINUE_DB = 3f
        const val MAX_GAP_FRAMES = 5
        const val MIN_FRAMES = 5
        const val MAX_BURST_MS = 10_000
    }
}
