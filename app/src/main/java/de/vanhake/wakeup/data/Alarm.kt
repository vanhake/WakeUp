package de.vanhake.wakeup.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

enum class ChallengeType { MATH, SHAKE, BARCODE }

enum class MathDifficulty { EASY, MEDIUM, HARD }

data class Alarm(
    val id: Int,
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    /** Empty means a one-time alarm. */
    val days: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
    val challenge: ChallengeType = ChallengeType.MATH,
    val mathDifficulty: MathDifficulty = MathDifficulty.MEDIUM,
    val mathCount: Int = 3,
    val shakeCount: Int = 30,
    /** Id of a [SavedBarcode] from the barcode library. */
    val barcodeId: Int? = null,
    /** 0 disables snoozing. */
    val maxSnoozes: Int = 2,
    val snoozeMinutes: Int = 5,
    val vibrate: Boolean = true,
    /** Fraction of the maximum alarm stream volume, 0.1..1. */
    val volume: Float = 1f,
    /** null = system default alarm sound. */
    val ringtoneUri: String? = null,
    // Runtime state of the current wake-up, reset on dismiss.
    val snoozesUsed: Int = 0,
    val snoozedUntil: Long? = null,
) {
    val isRepeating: Boolean get() = days.isNotEmpty()
    val snoozesLeft: Int get() = (maxSnoozes - snoozesUsed).coerceAtLeast(0)

    fun isSnoozed(now: Long = System.currentTimeMillis()): Boolean =
        snoozedUntil != null && snoozedUntil > now

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("label", label)
        put("days", JSONArray(days.sorted().map { it.value }))
        put("enabled", enabled)
        put("challenge", challenge.name)
        put("mathDifficulty", mathDifficulty.name)
        put("mathCount", mathCount)
        put("shakeCount", shakeCount)
        barcodeId?.let { put("barcodeId", it) }
        put("maxSnoozes", maxSnoozes)
        put("snoozeMinutes", snoozeMinutes)
        put("vibrate", vibrate)
        put("volume", volume.toDouble())
        ringtoneUri?.let { put("ringtoneUri", it) }
        put("snoozesUsed", snoozesUsed)
        snoozedUntil?.let { put("snoozedUntil", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val daysJson = o.optJSONArray("days") ?: JSONArray()
            val days = (0 until daysJson.length()).map { DayOfWeek.of(daysJson.getInt(it)) }.toSet()
            return Alarm(
                id = o.getInt("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                label = o.optString("label", ""),
                days = days,
                enabled = o.optBoolean("enabled", true),
                challenge = enumOr(o.optString("challenge"), ChallengeType.MATH),
                mathDifficulty = enumOr(o.optString("mathDifficulty"), MathDifficulty.MEDIUM),
                mathCount = o.optInt("mathCount", 3),
                shakeCount = o.optInt("shakeCount", 30),
                barcodeId = if (o.has("barcodeId")) o.getInt("barcodeId") else null,
                maxSnoozes = o.optInt("maxSnoozes", 2),
                snoozeMinutes = o.optInt("snoozeMinutes", 5),
                vibrate = o.optBoolean("vibrate", true),
                volume = o.optDouble("volume", 1.0).toFloat(),
                ringtoneUri = if (o.has("ringtoneUri")) o.getString("ringtoneUri") else null,
                snoozesUsed = o.optInt("snoozesUsed", 0),
                snoozedUntil = if (o.has("snoozedUntil")) o.getLong("snoozedUntil") else null,
            )
        }

        fun listToJson(alarms: List<Alarm>): String =
            JSONArray(alarms.map { it.toJson() }).toString()

        fun listFromJson(json: String): List<Alarm> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }

        private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
