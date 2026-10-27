package de.vanhake.wakeup.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.vanhake.wakeup.sleep.analysis.RestState
import de.vanhake.wakeup.sleep.analysis.SleepReport
import de.vanhake.wakeup.ui.theme.Danger
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

private const val MINUTE = 60_000L

/** Selected position for tap/drag inspection, as a fraction 0..1 of the width. */
internal fun Modifier.inspect(onSelect: (Float?) -> Unit): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures(onPress = { offset ->
            onSelect((offset.x / size.width).coerceIn(0f, 1f))
            tryAwaitRelease()
        })
    }
    .pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { onSelect((it.x / size.width).coerceIn(0f, 1f)) },
            onDrag = { change, _ -> onSelect((change.position.x / size.width).coerceIn(0f, 1f)) },
        )
    }

/**
 * Night timeline: rest state per minute, snoring below it, breathing-pause markers and
 * the alarm. Tap or drag to read the values at a point in time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimeline(report: SleepReport, modifier: Modifier = Modifier) {
    val n = report.states.size
    var selected by remember { mutableStateOf<Int?>(null) }
    val outline = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val hourTicks = remember(report) { hourTicks(report.start, n) }

    Column(modifier) {
        Text(
            selected?.let { m ->
                val snore = report.snoreSeconds[m]
                buildString {
                    append(formatTime(report.start + m * MINUTE))
                    append(" · ")
                    append(report.states[m].label)
                    if (snore > 0) append(" · $snore s Schnarchen")
                    if (report.movements[m] > 0) append(" · ${report.movements[m]}× Bewegung")
                }
            } ?: "Tippen oder ziehen, um Details zu sehen",
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected != null) MaterialTheme.colorScheme.onSurface else muted,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .inspect { f -> selected = f?.let { (it * (n - 1)).roundToInt() } },
        ) {
            val w = size.width
            val stateH = 40.dp.toPx()
            val snoreTop = stateH + 12.dp.toPx()
            val snoreH = size.height - snoreTop
            val gap = 2.dp.toPx()
            val xOf = { m: Int -> m * w / n }

            // Rest state as runs of equal minutes, separated by a small gap.
            var runStart = 0
            for (m in 1..n) {
                if (m == n || report.states[m] != report.states[runStart]) {
                    val x0 = xOf(runStart)
                    val x1 = xOf(m) - if (m == n) 0f else gap
                    if (x1 > x0) {
                        drawRoundRect(
                            report.states[runStart].color,
                            topLeft = Offset(x0, 0f),
                            size = Size(x1 - x0, stateH),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                        )
                    }
                    runStart = m
                }
            }

            drawLine(outline, Offset(0f, size.height), Offset(w, size.height), strokeWidth = 1.dp.toPx())
            val barW = (w / n - gap).coerceAtLeast(1f)
            for (m in 0 until n) {
                val s = report.snoreSeconds[m]
                if (s <= 0) continue
                val h = snoreH * (s / 60f).coerceIn(0.08f, 1f)
                drawRoundRect(
                    SnoreColor,
                    topLeft = Offset(xOf(m), size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(minOf(barW / 2, 2.dp.toPx())),
                )
            }
            report.apneas.forEach { a ->
                val x = ((a.timeMs - report.start) / MINUTE.toFloat()) * w / n
                drawCircle(surface, radius = 6.dp.toPx(), center = Offset(x, snoreTop + 6.dp.toPx()))
                drawCircle(Danger, radius = 4.dp.toPx(), center = Offset(x, snoreTop + 6.dp.toPx()))
            }
            hourTicks.forEach { (m, _) ->
                drawLine(outline, Offset(xOf(m), stateH), Offset(xOf(m), stateH + 6.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
            selected?.let { m ->
                val x = xOf(m) + w / n / 2
                drawLine(Color.White.copy(alpha = 0.8f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
        }
        HourLabels(hourTicks, n)
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RestState.entries.forEach { LegendItem(it.color, it.label) }
            LegendItem(SnoreColor, "Schnarchen")
            if (report.apneas.isNotEmpty()) LegendItem(Danger, "Atemaussetzer-Verdacht", round = true)
        }
    }
}

/** Average sound level per minute (dB above the quietest part of the night). */
@Composable
fun NoiseChart(report: SleepReport, modifier: Modifier = Modifier) {
    val n = report.noise.size
    var selected by remember { mutableStateOf<Int?>(null) }
    val smoothed = remember(report) {
        List(n) { m -> (maxOf(0, m - 2)..minOf(n - 1, m + 2)).map { report.noise[it] }.average().toFloat() }
    }
    val maxDb = (smoothed.maxOrNull() ?: 0f).coerceAtLeast(10f)
    val outline = MaterialTheme.colorScheme.outlineVariant
    val line = MaterialTheme.colorScheme.secondary
    val hourTicks = remember(report) { hourTicks(report.start, n) }

    Column(modifier) {
        Text(
            selected?.let { "${formatTime(report.start + it * MINUTE)} · ${smoothed[it].roundToInt()} dB über Ruhepegel" }
                ?: "Bis ${maxDb.roundToInt()} dB über dem Ruhepegel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .inspect { f -> selected = f?.let { (it * (n - 1)).roundToInt() } },
        ) {
            if (n < 2) return@Canvas
            val w = size.width
            val h = size.height
            drawLine(outline, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())
            val path = Path()
            smoothed.forEachIndexed { m, v ->
                val x = m * w / (n - 1)
                val y = h - h * (v / maxDb)
                if (m == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            selected?.let { m ->
                val x = m * w / (n - 1)
                drawLine(Color.White.copy(alpha = 0.8f), Offset(x, 0f), Offset(x, h), strokeWidth = 2.dp.toPx())
                drawCircle(line, radius = 5.dp.toPx(), center = Offset(x, h - h * (smoothed[m] / maxDb)))
            }
        }
        HourLabels(hourTicks, n)
    }
}

@Composable
private fun HourLabels(ticks: List<Pair<Int, Int>>, n: Int) {
    Box(Modifier.fillMaxWidth().height(20.dp)) {
        val every = if (ticks.size > 8) 2 else 1
        ticks.forEachIndexed { i, (m, hour) ->
            if (i % every != 0) return@forEachIndexed
            Text(
                "%02d".format(hour),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(xAlign(-1f + 2f * m / n)),
            )
        }
    }
}

internal fun xAlign(horizontal: Float) = BiasAlignment(horizontal.coerceIn(-1f, 1f), 0f)

@Composable
internal fun LegendItem(color: Color, text: String, round: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .background(color, if (round) RoundedCornerShape(6.dp) else RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Minute index and hour of every full hour within the night. */
private fun hourTicks(start: Long, n: Int): List<Pair<Int, Int>> {
    val first = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).withMinute(0).withSecond(0).withNano(0).plusHours(1)
    return generateSequence(first) { it.plusHours(1) }
        .map { ((it.toInstant().toEpochMilli() - start) / MINUTE).toInt() to it.hour }
        .takeWhile { it.first < n }
        .toList()
}
