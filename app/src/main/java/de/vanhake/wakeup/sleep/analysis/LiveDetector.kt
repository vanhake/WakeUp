package de.vanhake.wakeup.sleep.analysis

/** Audio to cut from the running recording; times relative to the recording start. */
data class ClipRequest(val type: ClipType, val fromMs: Long, val toMs: Long)

/**
 * Looks at bursts while the night is being recorded: decides which moments are worth an
 * audio sample and counts movement sounds per minute (for smart wake-up).
 */
class LiveDetector(private val clipsEnabled: Boolean) {
    private val recent = ArrayDeque<Burst>()
    private val recentLabels = ArrayDeque<LabelWindow>()
    private val lastClip = HashMap<ClipType, Long>()
    private var clips = 0
    private val minuteMoves = ArrayList<Int>()

    fun onBurst(b: Burst): ClipRequest? {
        recent.addLast(b)
        while (recent.first().startMs < b.startMs - KEEP_MS) recent.removeFirst()
        if (!clipsEnabled || clips >= MAX_CLIPS) return null
        val request = apneaClip(b) ?: snoreClip(b) ?: noiseClip(b) ?: return null
        clips++
        lastClip[request.type] = b.startMs
        return request
    }

    /** Keeps one sample of coughing and of talking in the sleep every 20 minutes at most. */
    fun onLabel(window: LabelWindow): ClipRequest? {
        recentLabels.addLast(window)
        while (recentLabels.first().startMs < window.startMs - KEEP_MS) recentLabels.removeFirst()
        val type = when (window.label) {
            SoundLabel.COUGH -> ClipType.COUGH
            SoundLabel.SPEECH -> ClipType.SPEECH
            else -> return null
        }
        if (!clipsEnabled || clips >= MAX_CLIPS) return null
        if (lastClip[type]?.let { window.startMs - it < 20 * 60_000L } == true) return null
        clips++
        lastClip[type] = window.startMs
        return ClipRequest(type, window.startMs - 3_000, window.endMs + 3_000)
    }

    /**
     * Movement sounds per minute of the recording (index = minute since the start), for every
     * minute that ended at least [SETTLE_MS] before [nowMs] (relative to the recording start).
     * The delay lets a breathing rhythm become recognisable before its first sounds are judged.
     * Breathing, snoring, coughing, speech and music/TV (if the classifier heard them) do not
     * count. Call it regularly (every few seconds): minutes older than a few minutes can no
     * longer be classified and count as quiet.
     */
    fun minuteMovements(nowMs: Long): List<Int> {
        val complete = ((nowMs - SETTLE_MS) / MINUTE_MS).toInt()
        if (complete > minuteMoves.size) {
            val window = recent.toList()
            val classification = BurstClassifier.classify(window, recentLabels.toList())
            val counts = HashMap<Int, Int>()
            window.forEach { b ->
                if (classification.typeOf(b) == SoundType.MOVEMENT) {
                    val m = (b.startMs / MINUTE_MS).toInt()
                    counts[m] = (counts[m] ?: 0) + 1
                }
            }
            while (minuteMoves.size < complete) minuteMoves.add(counts[minuteMoves.size] ?: 0)
        }
        return minuteMoves
    }

    /** Breathing resumed after ≥ 10 s of silence that followed regular breathing. */
    private fun apneaClip(b: Burst): ClipRequest? {
        if (recent.size < 6 || !cooledDown(ClipType.APNEA, b, 5 * 60_000L)) return null
        val before = recent.toList().dropLast(1).takeLast(5)
        val last = before.last()
        if (b.startMs - last.endMs < BurstClassifier.APNEA_MIN_PAUSE_MS || !isRegular(before)) return null
        val from = maxOf(last.endMs - 5_000, b.startMs - 20_000)
        return ClipRequest(ClipType.APNEA, from, b.startMs + 6_000)
    }

    private fun snoreClip(b: Burst): ClipRequest? {
        if (recent.size < 6 || !cooledDown(ClipType.SNORE, b, 20 * 60_000L)) return null
        val last = recent.toList().takeLast(6)
        if (!isRegular(last)) return null
        if (median(last.map { it.lowRatio }) < 0.55f || median(last.map { it.peakAboveFloor }) < 10f) return null
        return ClipRequest(ClipType.SNORE, b.endMs - 12_000, b.endMs + 3_000)
    }

    private fun noiseClip(b: Burst): ClipRequest? {
        if (b.peakAboveFloor < 25f || !cooledDown(ClipType.NOISE, b, 20 * 60_000L)) return null
        return ClipRequest(ClipType.NOISE, b.startMs - 3_000, minOf(b.endMs + 3_000, b.startMs + 15_000))
    }

    private fun cooledDown(type: ClipType, b: Burst, gapMs: Long): Boolean =
        lastClip[type]?.let { b.startMs - it >= gapMs } ?: true

    private fun isRegular(bursts: List<Burst>): Boolean {
        if (!bursts.all(BurstClassifier::isRhythmicCandidate)) return false
        val intervals = bursts.zipWithNext { a, c -> c.startMs - a.startMs }
        if (intervals.any { it !in 1_500L..10_000L }) return false
        return intervals.max() <= intervals.min() * 2
    }

    private companion object {
        const val KEEP_MS = 6 * 60_000L
        const val MINUTE_MS = 60_000L
        const val SETTLE_MS = 30_000L
        const val MAX_CLIPS = 30
    }
}
