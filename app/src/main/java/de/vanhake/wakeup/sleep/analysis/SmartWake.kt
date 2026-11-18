package de.vanhake.wakeup.sleep.analysis

import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

enum class SmartWakeTrigger(val label: String) {
    MOVEMENT("Unruhe"),
    SCREEN("Handy benutzt"),
}

/** Why smart wake-up rang early: decided by [SmartWake.decide] during the night. */
data class SmartWakeDecision(
    val trigger: SmartWakeTrigger,
    /** Movements (sounds + felt) in each of the restless minutes, oldest first. */
    val restlessMoves: List<Int>,
    /** Movements per minute that counted as restless this night. */
    val threshold: Int,
    /** Average movements per minute in the hours before the window. */
    val baseline: Float,
)

/** A smart wake-up that happened: when it rang, which regular ring time it replaced and why. */
data class SmartWakeInfo(
    /** Epoch millis. */
    val ringAt: Long,
    /** Regular ring time that was replaced (epoch millis). */
    val target: Long,
    val decision: SmartWakeDecision,
) {
    val minutesEarly: Int get() = ((target - ringAt) / 60_000.0).roundToInt().coerceAtLeast(0)

    fun toJson(): JSONObject = JSONObject()
        .put("ringAt", ringAt)
        .put("target", target)
        .put("trigger", decision.trigger.name)
        .put("moves", decision.restlessMoves.joinToString(","))
        .put("threshold", decision.threshold)
        .put("baseline", (decision.baseline * 100).roundToInt())

    companion object {
        fun fromJson(o: JSONObject): SmartWakeInfo? {
            val trigger = SmartWakeTrigger.entries.firstOrNull { it.name == o.optString("trigger") } ?: return null
            val moves = o.optString("moves").split(",").mapNotNull { it.trim().toIntOrNull() }
            return SmartWakeInfo(
                ringAt = o.getLong("ringAt"),
                target = o.getLong("target"),
                decision = SmartWakeDecision(trigger, moves, o.optInt("threshold"), o.optInt("baseline") / 100f),
            )
        }
    }
}

/**
 * Decides whether an alarm with smart wake-up should ring early.
 *
 * A microphone and the phone's accelerometer cannot see sleep stages; they only notice sounds
 * and shakes – and many of those (breathing, a partner, the heating, traffic) have nothing to do
 * with light sleep. So the decision is deliberately cautious, and in doubt the alarm rings at
 * its regular time:
 *
 * - Restless means clearly more movement than usual this night: at least [MIN_MOVES] per minute
 *   and at least [BASELINE_FACTOR] times the average of the [BASELINE_MINUTES] before the window.
 *   A steadily noisy room raises the bar instead of triggering every morning.
 * - The restlessness has to last: [RESTLESS_RUN] consecutive minutes, all inside the window. So
 *   the alarm never rings at the very start of the window just because of what happened before.
 * - Using the phone for at least [SCREEN_ON_MS] shows being awake.
 */
object SmartWake {
    const val MIN_MOVES = 2
    const val BASELINE_FACTOR = 3f
    const val BASELINE_MINUTES = 120
    const val RESTLESS_RUN = 2
    const val SCREEN_ON_MS = 15_000L
    private const val MINUTE_MS = 60_000L

    /**
     * [soundMoves]: movement sounds per finished minute since the recording start
     * ([LiveDetector.minuteMovements]). [feltMs]: accelerometer movements, ms since the start.
     * [windowStartMs]: start of the smart wake-up window, ms since the recording start.
     * [screenOnForMs]: how long the screen has been on right now, null if it is off.
     */
    fun decide(soundMoves: List<Int>, feltMs: List<Long>, windowStartMs: Long, screenOnForMs: Long?): SmartWakeDecision? {
        val moves = combine(soundMoves, feltMs)
        val firstInWindow = ceil(windowStartMs / MINUTE_MS.toDouble()).toInt().coerceAtLeast(0)
        val baseline = baseline(moves, firstInWindow)
        val threshold = threshold(baseline)
        if (screenOnForMs != null && screenOnForMs >= SCREEN_ON_MS) {
            return SmartWakeDecision(SmartWakeTrigger.SCREEN, emptyList(), threshold, baseline)
        }
        if (moves.size - RESTLESS_RUN < firstInWindow) return null
        val last = moves.takeLast(RESTLESS_RUN)
        if (last.any { it < threshold }) return null
        return SmartWakeDecision(SmartWakeTrigger.MOVEMENT, last, threshold, baseline)
    }

    /** Average movements per minute in the [BASELINE_MINUTES] before minute [until]. */
    fun baseline(moves: List<Int>, until: Int): Float {
        val end = until.coerceAtMost(moves.size)
        val from = (end - BASELINE_MINUTES).coerceAtLeast(0)
        if (end <= from) return 0f
        return moves.subList(from, end).sum().toFloat() / (end - from)
    }

    fun threshold(baseline: Float): Int = max(MIN_MOVES, ceil(baseline * BASELINE_FACTOR).toInt())

    /** Sound and felt movements per minute; felt ones only for minutes with sound data. */
    private fun combine(soundMoves: List<Int>, feltMs: List<Long>): List<Int> {
        val result = soundMoves.toMutableList()
        for (t in feltMs) {
            val m = (t / MINUTE_MS).toInt()
            if (m in result.indices) result[m]++
        }
        return result
    }
}
