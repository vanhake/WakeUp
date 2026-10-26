package de.vanhake.wakeup.sleep.analysis

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Sound level of one minute of the recording (dBFS). */
data class MinuteLevel(val avgDb: Float, val maxDb: Float, val floorDb: Float)

/** Screen switched on/off at [timeMs] (epoch millis). */
data class ScreenEvent(val timeMs: Long, val on: Boolean)

enum class ClipType { SNORE, APNEA, NOISE, COUGH, SPEECH }

/** A short audio sample stored next to the night; deleted after 24 hours. */
data class SleepClip(val type: ClipType, val timeMs: Long, val file: String, val durationMs: Int)

/** Everything recorded during one night. The report is computed from this by [SleepAnalyzer]. */
data class NightRecording(
    /** Epoch millis. */
    val start: Long,
    val end: Long,
    val bursts: List<Burst>,
    val minutes: List<MinuteLevel>,
    val screen: List<ScreenEvent>,
    /** First time an alarm rang during the recording. */
    val alarmRingAt: Long? = null,
    val alarmDismissAt: Long? = null,
    val snoozes: Int = 0,
    /** The alarm rang early because a light sleep phase was detected. */
    val smartWake: Boolean = false,
    /** Periods (epoch millis, start to end) in which an alarm rang and audio was ignored. */
    val ringing: List<LongRange> = emptyList(),
    val clips: List<SleepClip> = emptyList(),
    /** The app was stopped during the night; data ends at [end]. */
    val interrupted: Boolean = false,
    /** YAMNet results for windows with sound; empty if the classifier was not available. */
    val labels: List<LabelWindow> = emptyList(),
    val classifierUsed: Boolean = false,
    /** Brightness per minute in lux, null where the phone has no light sensor. */
    val light: List<Float?> = emptyList(),
    /** Movements felt by the accelerometer (phone on the mattress), ms relative to [start]. */
    val motion: List<Long> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("start", start)
        put("end", end)
        put("bursts", JSONArray().also { arr ->
            bursts.forEach { b ->
                arr.put(b.startMs)
                arr.put(b.durationMs)
                arr.put((b.peakDb * 10).roundToInt())
                arr.put((b.meanDb * 10).roundToInt())
                arr.put((b.floorDb * 10).roundToInt())
                arr.put((b.lowRatio * 1000).roundToInt())
            }
        })
        put("minutes", JSONArray().also { arr ->
            minutes.forEach { m ->
                arr.put((m.avgDb * 10).roundToInt())
                arr.put((m.maxDb * 10).roundToInt())
                arr.put((m.floorDb * 10).roundToInt())
            }
        })
        put("screen", JSONArray(screen.map { JSONArray(listOf(it.timeMs, if (it.on) 1 else 0)) }))
        alarmRingAt?.let { put("alarmRingAt", it) }
        alarmDismissAt?.let { put("alarmDismissAt", it) }
        put("snoozes", snoozes)
        put("smartWake", smartWake)
        put("ringing", JSONArray(ringing.map { JSONArray(listOf(it.first, it.last)) }))
        put("clips", JSONArray(clips.map {
            JSONObject()
                .put("type", it.type.name)
                .put("time", it.timeMs)
                .put("file", it.file)
                .put("duration", it.durationMs)
        }))
        put("interrupted", interrupted)
        put("labels", JSONArray().also { arr ->
            labels.forEach { l ->
                arr.put(l.startMs)
                arr.put(l.label.ordinal)
                arr.put((l.score * 100).roundToInt())
            }
        })
        put("classifierUsed", classifierUsed)
        put("light", JSONArray(light.map { it?.let { lux -> (lux * 10).roundToInt() } ?: -1 }))
        put("motion", JSONArray(motion))
    }

    companion object {
        fun fromJson(o: JSONObject): NightRecording {
            val b = o.getJSONArray("bursts")
            val bursts = (0 until b.length() / 6).map { i ->
                val k = i * 6
                Burst(
                    startMs = b.getLong(k),
                    durationMs = b.getInt(k + 1),
                    peakDb = b.getInt(k + 2) / 10f,
                    meanDb = b.getInt(k + 3) / 10f,
                    floorDb = b.getInt(k + 4) / 10f,
                    lowRatio = b.getInt(k + 5) / 1000f,
                )
            }
            val m = o.getJSONArray("minutes")
            val minutes = (0 until m.length() / 3).map { i ->
                MinuteLevel(m.getInt(i * 3) / 10f, m.getInt(i * 3 + 1) / 10f, m.getInt(i * 3 + 2) / 10f)
            }
            val s = o.optJSONArray("screen") ?: JSONArray()
            val screen = (0 until s.length()).map {
                val e = s.getJSONArray(it)
                ScreenEvent(e.getLong(0), e.getInt(1) == 1)
            }
            val r = o.optJSONArray("ringing") ?: JSONArray()
            val ringing = (0 until r.length()).map {
                val e = r.getJSONArray(it)
                e.getLong(0)..e.getLong(1)
            }
            val c = o.optJSONArray("clips") ?: JSONArray()
            val clips = (0 until c.length()).mapNotNull {
                val e = c.getJSONObject(it)
                val type = ClipType.entries.firstOrNull { t -> t.name == e.optString("type") }
                    ?: return@mapNotNull null
                SleepClip(type, e.getLong("time"), e.getString("file"), e.getInt("duration"))
            }
            val l = o.optJSONArray("labels") ?: JSONArray()
            val labels = (0 until l.length() / 3).mapNotNull { i ->
                val label = SoundLabel.entries.getOrNull(l.getInt(i * 3 + 1)) ?: return@mapNotNull null
                LabelWindow(l.getLong(i * 3), label, l.getInt(i * 3 + 2) / 100f)
            }
            val li = o.optJSONArray("light") ?: JSONArray()
            val light = (0 until li.length()).map { li.getInt(it).takeIf { v -> v >= 0 }?.let { v -> v / 10f } }
            val mo = o.optJSONArray("motion") ?: JSONArray()
            val motion = (0 until mo.length()).map { mo.getLong(it) }
            return NightRecording(
                start = o.getLong("start"),
                end = o.getLong("end"),
                bursts = bursts,
                minutes = minutes,
                screen = screen,
                alarmRingAt = if (o.has("alarmRingAt")) o.getLong("alarmRingAt") else null,
                alarmDismissAt = if (o.has("alarmDismissAt")) o.getLong("alarmDismissAt") else null,
                snoozes = o.optInt("snoozes", 0),
                smartWake = o.optBoolean("smartWake", false),
                ringing = ringing,
                clips = clips,
                interrupted = o.optBoolean("interrupted", false),
                labels = labels,
                classifierUsed = o.optBoolean("classifierUsed", false),
                light = light,
                motion = motion,
            )
        }
    }
}
