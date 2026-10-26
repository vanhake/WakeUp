package de.vanhake.wakeup.sleep.analysis

import kotlin.math.abs

enum class SoundType { SNORE, BREATH, MOVEMENT, NOISE, COUGH, SPEECH }

/** A regular sequence of breathing or snoring sounds. */
data class BreathChain(val bursts: List<Burst>, val type: SoundType) {
    val medianIntervalMs: Long get() = median(bursts.zipWithNext { a, b -> b.startMs - a.startMs })
}

/** A pause of at least 10 s inside regular breathing/snoring. */
data class ApneaCandidate(
    /** Relative to the recording start, like [Burst.startMs]. */
    val startMs: Long,
    val pauseMs: Long,
    /** Breathing resumed with a sound clearly louder than before (gasp / loud snore). */
    val gasp: Boolean,
)

data class Classification(
    val types: Map<Burst, SoundType>,
    val chains: List<BreathChain>,
    val apneas: List<ApneaCandidate>,
) {
    fun typeOf(burst: Burst): SoundType = types[burst] ?: SoundType.MOVEMENT
}

/**
 * Sorts bursts into snoring, audible breathing, movement and loud noise.
 *
 * Breathing and snoring are recognised by their rhythm: sounds of 0.25–3.5 s that repeat
 * every 1.5–10 s. Snoring is the loud, low-frequency variant. Everything else is movement
 * (rustling, bed creaking) or – if very loud or long – noise.
 *
 * If YAMNet [LabelWindow]s are available they take precedence: they recognise coughing and
 * speech, confirm snoring without a clear rhythm and stop speech or music from being taken
 * for rhythmic breathing.
 */
object BurstClassifier {
    private const val MIN_RHYTHMIC_MS = 250
    private const val MAX_RHYTHMIC_MS = 3_500
    private const val MIN_INTERVAL_MS = 1_500L
    private const val MAX_INTERVAL_MS = 10_000L
    private const val MIN_CHAIN = 4
    private const val SNORE_LOW_RATIO = 0.55f
    private const val SNORE_MIN_ABOVE_DB = 10f
    private const val NOISE_ABOVE_DB = 20f
    private const val NOISE_MIN_MS = 4_000

    const val APNEA_MIN_PAUSE_MS = 10_000L
    private const val APNEA_MAX_PAUSE_MS = 90_000L

    private val NOT_BREATHING = setOf(SoundLabel.SPEECH, SoundLabel.MUSIC_TV, SoundLabel.COUGH)

    fun isRhythmicCandidate(b: Burst): Boolean =
        b.durationMs in MIN_RHYTHMIC_MS..MAX_RHYTHMIC_MS && b.peakAboveFloor < 40f

    fun classify(bursts: List<Burst>, labels: List<LabelWindow> = emptyList()): Classification {
        val sorted = bursts.sortedBy { it.startMs }
        val labelOf = labelLookup(sorted, labels)
        val rawChains = buildChains(sorted.filter(::isRhythmicCandidate))
        val chains = rawChains.filter { it.size >= MIN_CHAIN }.mapNotNull { members ->
            val heard = members.mapNotNull { labelOf[it] }
            val majority = heard.groupingBy { it }.eachCount().maxByOrNull { it.value }
                ?.takeIf { it.value * 2 >= heard.size && heard.isNotEmpty() }?.key
            val type = when {
                majority != null && majority in NOT_BREATHING -> return@mapNotNull null
                majority == SoundLabel.SNORE -> SoundType.SNORE
                majority == SoundLabel.BREATH -> SoundType.BREATH
                median(members.map { it.lowRatio }) >= SNORE_LOW_RATIO &&
                    median(members.map { it.peakAboveFloor }) >= SNORE_MIN_ABOVE_DB -> SoundType.SNORE
                else -> SoundType.BREATH
            }
            BreathChain(members, type)
        }

        val types = HashMap<Burst, SoundType>(sorted.size * 2)
        chains.forEach { chain -> chain.bursts.forEach { types[it] = chain.type } }
        sorted.forEach { b ->
            val heuristic = types[b] ?: if (b.peakAboveFloor >= NOISE_ABOVE_DB || b.durationMs >= NOISE_MIN_MS) {
                SoundType.NOISE
            } else {
                SoundType.MOVEMENT
            }
            types[b] = when (labelOf[b]) {
                SoundLabel.COUGH -> SoundType.COUGH
                SoundLabel.SPEECH -> SoundType.SPEECH
                SoundLabel.MUSIC_TV -> SoundType.NOISE
                SoundLabel.SNORE -> SoundType.SNORE
                else -> heuristic
            }
        }
        return Classification(types, chains, findApneas(chains, sorted) { labelOf[it] == SoundLabel.GASP })
    }

    /** The most confident label of a window overlapping each burst. */
    private fun labelLookup(sorted: List<Burst>, labels: List<LabelWindow>): Map<Burst, SoundLabel> {
        if (labels.isEmpty()) return emptyMap()
        val windows = labels.sortedBy { it.startMs }
        val result = HashMap<Burst, SoundLabel>()
        var first = 0
        for (b in sorted) {
            while (first < windows.size && windows[first].endMs <= b.startMs) first++
            var best: LabelWindow? = null
            var i = first
            while (i < windows.size && windows[i].startMs < b.endMs) {
                if (best == null || windows[i].score > best.score) best = windows[i]
                i++
            }
            best?.let { result[b] = it.label }
        }
        return result
    }

    /**
     * Greedily links rhythmic sounds whose onsets are 1.5–10 s apart and whose spacing stays
     * consistent. Sounds that come too early are skipped without breaking the chain.
     */
    private fun buildChains(candidates: List<Burst>): List<List<Burst>> {
        val chains = mutableListOf<MutableList<Burst>>()
        var current = mutableListOf<Burst>()
        for (b in candidates) {
            val last = current.lastOrNull()
            if (last == null) {
                current.add(b)
                continue
            }
            val interval = b.startMs - last.startMs
            when {
                interval < MIN_INTERVAL_MS -> Unit
                interval <= MAX_INTERVAL_MS && fitsRhythm(current, interval) -> current.add(b)
                else -> {
                    chains.add(current)
                    current = mutableListOf(b)
                }
            }
        }
        if (current.isNotEmpty()) chains.add(current)
        return chains
    }

    private fun fitsRhythm(chain: List<Burst>, interval: Long): Boolean {
        if (chain.size < 3) return true
        val med = median(chain.takeLast(6).zipWithNext { a, b -> b.startMs - a.startMs })
        return abs(interval - med) <= med * 0.6
    }

    /**
     * Two regular chains separated by 10–90 s of silence: breathing stopped and resumed.
     * Any sound in between (except one shortly before breathing resumes, e.g. turning over
     * when waking up) means it was not a breathing pause.
     */
    private fun findApneas(
        chains: List<BreathChain>,
        all: List<Burst>,
        heardGasp: (Burst) -> Boolean,
    ): List<ApneaCandidate> {
        val result = mutableListOf<ApneaCandidate>()
        for ((a, b) in chains.zipWithNext()) {
            if (a.bursts.size < MIN_CHAIN || a.medianIntervalMs > 6_500) continue
            val lastA = a.bursts.last()
            val firstB = b.bursts.first()
            val firstAfter = all.firstOrNull { it.startMs > lastA.startMs } ?: continue
            val pause = firstAfter.startMs - lastA.endMs
            if (pause < APNEA_MIN_PAUSE_MS || pause > APNEA_MAX_PAUSE_MS) continue
            if (pause < a.medianIntervalMs * 2) continue
            if (firstB.startMs - firstAfter.startMs > 5_000) continue
            val gasp = firstB.peakDb >= median(a.bursts.map { it.peakDb }) + 3f || heardGasp(firstAfter) || heardGasp(firstB)
            result.add(ApneaCandidate(lastA.endMs, pause, gasp))
        }
        return result
    }
}

internal fun median(values: List<Long>): Long {
    if (values.isEmpty()) return 0
    val s = values.sorted()
    return s[s.size / 2]
}

@JvmName("medianFloat")
internal fun median(values: List<Float>): Float {
    if (values.isEmpty()) return 0f
    val s = values.sorted()
    return s[s.size / 2]
}
