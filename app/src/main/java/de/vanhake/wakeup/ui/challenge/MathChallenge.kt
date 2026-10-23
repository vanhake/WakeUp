package de.vanhake.wakeup.ui.challenge

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vanhake.wakeup.challenge.generateMathProblem
import de.vanhake.wakeup.data.MathDifficulty
import de.vanhake.wakeup.ui.theme.Danger
import kotlin.math.roundToInt

@Composable
fun MathChallenge(difficulty: MathDifficulty, count: Int, onSolved: () -> Unit) {
    var solved by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf(generateMathProblem(difficulty)) }
    var input by remember { mutableStateOf("") }
    var wrongAttempts by remember { mutableIntStateOf(0) }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(wrongAttempts) {
        if (wrongAttempts == 0) return@LaunchedEffect
        for (x in listOf(24f, -24f, 16f, -16f, 8f, 0f)) shake.animateTo(x)
    }

    fun submit() {
        if (input.isEmpty()) return
        if (input.toLongOrNull() == problem.answer) {
            solved++
            if (solved >= count) {
                onSolved()
                return
            }
        } else {
            wrongAttempts++
        }
        // A wrong answer also gets a new problem, so guessing does not help.
        problem = generateMathProblem(difficulty)
        input = ""
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Aufgabe ${solved + 1} von $count",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { solved / count.toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.weight(1f))
        Text(
            "${problem.text} =",
            fontSize = 40.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                input.ifEmpty { "?" },
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = if (input.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            if (wrongAttempts > 0) "Falsch – neue Aufgabe" else " ",
            color = Danger,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        Keypad(
            onDigit = { if (input.length < 6) input += it },
            onDelete = { input = input.dropLast(1) },
            onSubmit = ::submit,
        )
    }
}

@Composable
private fun Keypad(onDigit: (String) -> Unit, onDelete: () -> Unit, onSubmit: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { digit -> Key(Modifier.weight(1f), onClick = { onDigit(digit) }) { Text(digit, fontSize = 30.sp) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Key(Modifier.weight(1f), onClick = onDelete) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Löschen")
            }
            Key(Modifier.weight(1f), onClick = { onDigit("0") }) { Text("0", fontSize = 30.sp) }
            Key(
                Modifier.weight(1f),
                onClick = onSubmit,
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Prüfen")
            }
        }
    }
}

@Composable
private fun Key(
    modifier: Modifier,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: Color = MaterialTheme.colorScheme.onSurface,
    label: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) { label() }
}
