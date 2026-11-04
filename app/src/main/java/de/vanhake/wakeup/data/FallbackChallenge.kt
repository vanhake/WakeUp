package de.vanhake.wakeup.data

/**
 * Task that replaces a barcode which can't be scanned (not at hand, or deleted from the
 * library), and a shake task on a phone without accelerometer.
 */
enum class FallbackChallenge(val label: String) {
    MATH_EASY("Rechnen leicht"),
    MATH_MEDIUM("Rechnen mittel"),
    MATH_HARD("Rechnen schwer"),
    SHAKE("Schütteln"),
    ;

    val mathDifficulty: MathDifficulty?
        get() = when (this) {
            MATH_EASY -> MathDifficulty.EASY
            MATH_MEDIUM -> MathDifficulty.MEDIUM
            MATH_HARD -> MathDifficulty.HARD
            SHAKE -> null
        }

    companion object {
        val DEFAULT = MATH_HARD

        /** Number of math problems for the fallback. */
        const val MATH_COUNT = 6

        /** Number of shakes for the fallback. */
        const val SHAKE_COUNT = 50

        fun fromName(name: String?): FallbackChallenge = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
