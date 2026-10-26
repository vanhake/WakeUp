package de.vanhake.wakeup.sleep.analysis

/** What the YAMNet sound classifier heard in a window of about one second. */
enum class SoundLabel { SNORE, BREATH, GASP, COUGH, SPEECH, MUSIC_TV }

/** Classifier result for the window starting at [startMs] (relative to the recording start). */
data class LabelWindow(val startMs: Long, val label: SoundLabel, val score: Float) {
    val endMs: Long get() = startMs + YAMNET_WINDOW_MS
}

/** YAMNet takes 15600 samples (0.975 s at 16 kHz). */
const val YAMNET_WINDOW = 15_600
const val YAMNET_WINDOW_MS = YAMNET_WINDOW * 1000L / SAMPLE_RATE

/** YAMNet (AudioSet) class indices that matter at night. */
private val GROUPS = mapOf(
    SoundLabel.SPEECH to intArrayOf(0, 1, 2, 3),
    SoundLabel.BREATH to intArrayOf(36, 37, 40),
    SoundLabel.SNORE to intArrayOf(38, 41),
    SoundLabel.GASP to intArrayOf(39),
    SoundLabel.COUGH to intArrayOf(42, 43, 44),
    SoundLabel.MUSIC_TV to intArrayOf(132, 518),
)

private val MIN_SCORE = mapOf(SoundLabel.SNORE to 0.2f, SoundLabel.GASP to 0.2f)
private const val DEFAULT_MIN_SCORE = 0.3f

/** Picks the most likely night-relevant label from the 521 YAMNet scores, or null if none is confident. */
fun labelFromScores(scores: FloatArray): Pair<SoundLabel, Float>? = GROUPS
    .map { (label, indices) -> label to indices.maxOf { scores[it] } }
    .filter { (label, score) -> score >= (MIN_SCORE[label] ?: DEFAULT_MIN_SCORE) }
    .maxByOrNull { it.second }
