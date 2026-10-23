package de.vanhake.wakeup.challenge

import de.vanhake.wakeup.data.MathDifficulty
import kotlin.random.Random

data class MathProblem(val text: String, val answer: Long)

fun generateMathProblem(difficulty: MathDifficulty, random: Random = Random.Default): MathProblem =
    when (difficulty) {
        MathDifficulty.EASY -> {
            val a = random.nextInt(10, 100)
            val b = random.nextInt(10, 100)
            if (random.nextBoolean()) {
                MathProblem("$a + $b", (a + b).toLong())
            } else {
                val (big, small) = if (a >= b) a to b else b to a
                MathProblem("$big − $small", (big - small).toLong())
            }
        }
        MathDifficulty.MEDIUM -> {
            val a = random.nextInt(11, 20)
            val b = random.nextInt(3, 10)
            val c = random.nextInt(10, 100)
            MathProblem("$a × $b + $c", (a * b + c).toLong())
        }
        MathDifficulty.HARD -> {
            val a = random.nextInt(11, 30)
            val b = random.nextInt(6, 10)
            val c = random.nextInt(11, 30)
            val d = random.nextInt(3, 10)
            MathProblem("$a × $b + $c × $d", (a * b + c * d).toLong())
        }
    }
