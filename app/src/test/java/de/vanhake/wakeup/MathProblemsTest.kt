package de.vanhake.wakeup

import de.vanhake.wakeup.challenge.generateMathProblem
import de.vanhake.wakeup.data.MathDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MathProblemsTest {
    /** Evaluates the generated text independently of the generator. */
    private fun evaluate(text: String): Long =
        text.split(" + ").sumOf { term ->
            when {
                "×" in term -> term.split(" × ").map { it.trim().toLong() }.reduce(Long::times)
                "−" in term -> term.split(" − ").map { it.trim().toLong() }.reduce(Long::minus)
                else -> term.trim().toLong()
            }
        }

    @Test
    fun answersMatchTextAndArePositive() {
        val random = Random(42)
        for (difficulty in MathDifficulty.entries) {
            repeat(500) {
                val p = generateMathProblem(difficulty, random)
                assertEquals(p.text, evaluate(p.text), p.answer)
                assertTrue(p.text, p.answer >= 0)
            }
        }
    }
}
