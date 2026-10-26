package de.vanhake.wakeup.sleep.analysis

import kotlin.math.ceil
import kotlin.math.roundToInt

enum class RestState { CALM, RESTLESS, AWAKE }

/** Loud sound during sleep; [wokeUp] if restlessness or waking followed within 2 minutes. */
data class Disturbance(val timeMs: Long, val aboveFloorDb: Float, val wokeUp: Boolean)

data class ApneaEvent(val timeMs: Long, val pauseMs: Long, val gasp: Boolean)

data class SleepReport(
    val start: Long,
    val end: Long,
    /** One entry per minute from [start]. */
    val states: List<RestState>,
    val snoreSeconds: List<Int>,
    val movements: List<Int>,
    /** Average sound level per minute in dB above the quietest part of the night. */
    val noise: List<Float>,
    /** When going to bed: last screen-off or lights-off before falling asleep (or the start). */
    val bedStart: Long,
    val sleepOnset: Long?,
    val wakeUp: Long?,
    val timeInBedMinutes: Int,
    val sleepMinutes: Int,
    val latencyMinutes: Int?,
    val awakenings: Int,
    val restlessMinutes: Int,
    /** Sleep time ÷ time in bed, 0..1. */
    val efficiency: Float,
    val snoreMinutes: Int,
    val snoreEpisodes: Int,
    /** Typical loudness of snoring in dB above the background. */
    val snoreLoudness: Float?,
    val apneas: List<ApneaEvent>,
    val apneasPerHour: Float?,
    /** Breaths per minute where breathing or snoring was audible. */
    val breathingRate: Float?,
    val disturbances: List<Disturbance>,
    val score: Int,
    /** Coughs during the sleep period (needs the sound classifier). */
    val coughs: Int = 0,
    /** Separate stretches of talking during sleep (needs the sound classifier). */
    val sleepTalkEpisodes: Int = 0,
    /** Room light switched off (from the light sensor). */
    val lightsOff: Long? = null,
    val classifierUsed: Boolean = false,
    /** The accelerometer felt movements, i.e. the phone lay on the mattress. */
    val usedMotion: Boolean = false,
) {
    val snorePercent: Int get() = if (sleepMinutes > 0) (snoreMinutes * 100f / sleepMinutes).roundToInt() else 0
}

enum class ApneaLevel(val label: String) {
    NORMAL("unauffällig"),
    MILD("leicht erhöht"),
    MODERATE("deutlich erhöht"),
    SEVERE("stark erhöht"),
}

/** Classification analogous to the apnea-hypopnea index (events per hour of sleep). */
fun apneaLevel(perHour: Float): ApneaLevel = when {
    perHour < 5 -> ApneaLevel.NORMAL
    perHour < 15 -> ApneaLevel.MILD
    perHour < 30 -> ApneaLevel.MODERATE
    else -> ApneaLevel.SEVERE
}

object SleepAnalyzer {
    private const val MINUTE = 60_000L
    private const val ONSET_WINDOW = 15
    private const val RESTLESS_RUN_AWAKE = 6
    private const val SNORING_MINUTE_SEC = 10
    private const val LIT = 5f
    private const val DARK = 2f

    fun analyze(night: NightRecording): SleepReport {
        val start = night.start
        val n = ceil((night.end - start) / MINUTE.toDouble()).toInt().coerceAtLeast(1)
        fun ringing(relMs: Long) = night.ringing.any { (start + relMs) in it }
        val bursts = night.bursts.filterNot { ringing(it.startMs) }
        val classification = BurstClassifier.classify(bursts, night.labels.filterNot { ringing(it.startMs) })

        val snoreMs = LongArray(n)
        val moveCount = IntArray(n)
        val moveMs = LongArray(n)
        for (b in bursts) {
            val m = (b.startMs / MINUTE).toInt()
            if (m !in 0 until n) continue
            when (classification.typeOf(b)) {
                SoundType.SNORE -> snoreMs[m] += b.durationMs.toLong()
                // Coughing and talking are restless too.
                SoundType.MOVEMENT, SoundType.COUGH, SoundType.SPEECH -> {
                    moveCount[m]++
                    moveMs[m] += b.durationMs.toLong()
                }
                else -> Unit
            }
        }
        for (t in night.motion) {
            val m = (t / MINUTE).toInt()
            if (m in 0 until n && !ringing(t)) moveCount[m]++
        }
        val snoreSec = IntArray(n) { (snoreMs[it] / 1000).toInt().coerceAtMost(60) }

        // Lights still on in the evening = awake (morning daylight in the second half does not count).
        val lightsOff = lightsOff(night, n / 2)
        val lightsOffMinute = lightsOff?.let { ((it - start) / MINUTE).toInt() } ?: 0

        // Minute-level state: screen or light on = awake, lots of movement = restless.
        val states = Array(n) { m ->
            val snoring = snoreSec[m] >= SNORING_MINUTE_SEC
            when {
                screenOnDuring(night, start + m * MINUTE, start + (m + 1) * MINUTE) -> RestState.AWAKE
                m < lightsOffMinute && (night.light.getOrNull(m) ?: 0f) >= LIT -> RestState.AWAKE
                !snoring && (moveCount[m] >= 2 || moveMs[m] >= 2_000) -> RestState.RESTLESS
                else -> RestState.CALM
            }
        }
        // A long stretch of restlessness most likely means being awake.
        var runStart = -1
        for (m in 0..n) {
            if (m < n && states[m] == RestState.RESTLESS) {
                if (runStart < 0) runStart = m
            } else {
                if (runStart >= 0 && m - runStart >= RESTLESS_RUN_AWAKE) {
                    for (k in runStart until m) states[k] = RestState.AWAKE
                }
                runStart = -1
            }
        }

        val onsetMinute = (0..n - ONSET_WINDOW).firstOrNull { m ->
            val window = m until m + ONSET_WINDOW
            window.none { states[it] == RestState.AWAKE } && window.count { states[it] == RestState.RESTLESS } <= 3
        } ?: (0 until n).firstOrNull { snoreSec[it] >= SNORING_MINUTE_SEC && states[it] != RestState.AWAKE }

        // Wake-up: first alarm, or earlier if the screen/restlessness shows being awake before.
        var wakeMinute = night.alarmRingAt?.let { ((it - start) / MINUTE).toInt().coerceIn(0, n) } ?: n
        while (onsetMinute != null && wakeMinute > onsetMinute + 1 && states[wakeMinute - 1] == RestState.AWAKE) {
            wakeMinute--
        }
        if (onsetMinute != null) {
            for (m in 0 until onsetMinute) states[m] = RestState.AWAKE
        }
        for (m in wakeMinute until n) states[m] = RestState.AWAKE

        val sleepRange = if (onsetMinute != null && wakeMinute > onsetMinute) onsetMinute until wakeMinute else IntRange.EMPTY
        val sleepMinutes = sleepRange.count { states[it] != RestState.AWAKE }
        val restlessMinutes = sleepRange.count { states[it] == RestState.RESTLESS }
        var awakenings = 0
        for (m in sleepRange) {
            if (states[m] == RestState.AWAKE && (m == sleepRange.first || states[m - 1] != RestState.AWAKE)) awakenings++
        }

        val onsetTime = onsetMinute?.let { start + it * MINUTE }
        val wakeTime = if (onsetMinute != null) start + wakeMinute * MINUTE else null
        val bedStart = onsetTime?.let { onset ->
            listOfNotNull(
                night.screen.filter { !it.on && it.timeMs <= onset }.maxOfOrNull { it.timeMs },
                lightsOff?.takeIf { it <= onset },
            ).maxOrNull()
        }?.coerceAtLeast(start) ?: start
        val timeInBed = (((wakeTime ?: night.end) - bedStart) / MINUTE).toInt().coerceAtLeast(0)
        val latency = onsetTime?.let { ((it - bedStart) / MINUTE).toInt().coerceAtLeast(0) }
        val efficiency = if (timeInBed > 0) (sleepMinutes.toFloat() / timeInBed).coerceIn(0f, 1f) else 0f

        fun inSleep(relMs: Long): Boolean = (relMs / MINUTE).toInt().let { it in sleepRange && states[it] != RestState.AWAKE }

        val snoreChains = classification.chains.filter { it.type == SoundType.SNORE && inSleep(it.bursts.first().startMs) }
        val snoreMinutes = (sleepRange.sumOf { snoreSec[it] } / 60f).roundToInt()
        val snoreLoudness = snoreChains.flatMap { c -> c.bursts.map { it.peakAboveFloor } }
            .takeIf { it.isNotEmpty() }?.let { median(it) }

        val apneas = classification.apneas.filter { inSleep(it.startMs) }
            .map { ApneaEvent(start + it.startMs, it.pauseMs, it.gasp) }
        val apneasPerHour = if (sleepMinutes >= 60) apneas.size / (sleepMinutes / 60f) else null

        val audibleChains = classification.chains.filter { inSleep(it.bursts.first().startMs) }
        val breathingRate = if (audibleChains.sumOf { it.bursts.size } >= 20) {
            val weighted = audibleChains.sumOf { c -> c.bursts.size * 60_000.0 / c.medianIntervalMs }
            (weighted / audibleChains.sumOf { it.bursts.size }).toFloat()
        } else {
            null
        }

        val disturbances = bursts
            .filter { classification.typeOf(it) == SoundType.NOISE && it.peakAboveFloor >= 20f && inSleep(it.startMs) }
            .map { b ->
                val m = (b.startMs / MINUTE).toInt()
                val wokeUp = (m + 1..minOf(m + 2, n - 1)).any { states[it] != RestState.CALM } &&
                    states[m] == RestState.CALM
                Disturbance(start + b.startMs, b.peakAboveFloor, wokeUp)
            }

        val quiet = night.minutes.map { it.floorDb }.sorted().let { if (it.isEmpty()) 0f else it[it.size / 10] }
        val noise = List(n) { m -> night.minutes.getOrNull(m)?.let { (it.avgDb - quiet).coerceAtLeast(0f) } ?: 0f }

        val score = score(sleepMinutes, efficiency, restlessMinutes, awakenings, latency)

        fun inSleepRange(relMs: Long) = (relMs / MINUTE).toInt() in sleepRange
        val coughs = bursts.count { classification.typeOf(it) == SoundType.COUGH && inSleepRange(it.startMs) }
        var talkEpisodes = 0
        var lastTalk: Long? = null
        for (b in bursts.sortedBy { it.startMs }) {
            if (classification.typeOf(b) != SoundType.SPEECH || !inSleepRange(b.startMs)) continue
            if (lastTalk == null || b.startMs - lastTalk > 60_000) talkEpisodes++
            lastTalk = b.endMs
        }

        return SleepReport(
            start = start,
            end = night.end,
            states = states.toList(),
            snoreSeconds = snoreSec.toList(),
            movements = moveCount.toList(),
            noise = noise,
            bedStart = bedStart,
            sleepOnset = onsetTime,
            wakeUp = wakeTime,
            timeInBedMinutes = timeInBed,
            sleepMinutes = sleepMinutes,
            latencyMinutes = latency,
            awakenings = awakenings,
            restlessMinutes = restlessMinutes,
            efficiency = efficiency,
            snoreMinutes = snoreMinutes,
            snoreEpisodes = snoreChains.size,
            snoreLoudness = snoreLoudness,
            apneas = apneas,
            apneasPerHour = apneasPerHour,
            breathingRate = breathingRate,
            disturbances = disturbances,
            score = score,
            coughs = coughs,
            sleepTalkEpisodes = talkEpisodes,
            lightsOff = lightsOff,
            classifierUsed = night.classifierUsed,
            usedMotion = night.motion.isNotEmpty(),
        )
    }

    /** 0–100 from duration, efficiency, restlessness, awakenings and time to fall asleep. */
    fun score(sleepMinutes: Int, efficiency: Float, restlessMinutes: Int, awakenings: Int, latency: Int?): Int {
        if (sleepMinutes <= 0) return 0
        val hours = sleepMinutes / 60f
        val duration = when {
            hours < 7f -> ((hours - 3f) / 4f).coerceIn(0f, 1f)
            hours <= 9f -> 1f
            else -> (1f - (hours - 9f) * 0.2f).coerceAtLeast(0.6f)
        }
        val eff = ((efficiency - 0.65f) / 0.30f).coerceIn(0f, 1f)
        val rest = (1f - restlessMinutes.toFloat() / sleepMinutes / 0.3f).coerceIn(0f, 1f)
        val wake = (1f - awakenings / 6f).coerceIn(0f, 1f)
        val lat = latency?.let { if (it <= 20) 1f else (1f - (it - 20) / 40f).coerceIn(0f, 1f) } ?: 1f
        return (100 * (0.40f * duration + 0.20f * eff + 0.15f * rest + 0.15f * wake + 0.10f * lat)).roundToInt()
    }

    /** Last switch from a lit room to darkness before minute [before]. */
    private fun lightsOff(night: NightRecording, before: Int): Long? {
        var result: Long? = null
        for (m in 1 until minOf(before + 1, night.light.size)) {
            val prev = night.light[m - 1] ?: continue
            val cur = night.light[m] ?: continue
            if (prev >= LIT && cur < DARK) result = night.start + m * MINUTE
        }
        return result
    }

    private fun screenOnDuring(night: NightRecording, from: Long, to: Long): Boolean {
        var on = false
        var onSince = 0L
        for (e in night.screen) {
            if (e.timeMs >= to) break
            if (e.on && !on) {
                on = true
                onSince = e.timeMs
            } else if (!e.on && on) {
                on = false
                if (e.timeMs > from && e.timeMs - onSince >= 10_000) return true
            }
        }
        return on && onSince < to
    }
}
