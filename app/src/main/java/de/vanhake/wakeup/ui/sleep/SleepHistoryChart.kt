package de.vanhake.wakeup.ui.sleep

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.vanhake.wakeup.sleep.HistoryMetric
import de.vanhake.wakeup.sleep.HistoryPeriod
import de.vanhake.wakeup.sleep.HistoryRange
import de.vanhake.wakeup.sleep.NightSummary
import de.vanhake.wakeup.sleep.dailyNights
import de.vanhake.wakeup.sleep.fullMoonNights
import de.vanhake.wakeup.sleep.historyPeriod
import de.vanhake.wakeup.sleep.moonPhase
import de.vanhake.wakeup.sleep.nightDate
import de.vanhake.wakeup.sleep.rollingAverage
import de.vanhake.wakeup.ui.theme.Danger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val MoonColor = Color(0xFFE6DFB8)
private const val LIST_PAGE = 31

private val DAY_MONTH = DateTimeFormatter.ofPattern("d.M.", Locale.GERMAN)
private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d.M.yyyy", Locale.GERMAN)
private val MONTH_YEAR = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN)
private val SHORT_MONTH = DateTimeFormatter.ofPattern("LLL", Locale.GERMAN)
private val WEEKDAY = DateTimeFormatter.ofPattern("EE", Locale.GERMAN)

private data class ChartPoint(val date: LocalDate, val night: NightSummary, val value: Float)

/**
 * Sleep history: one value per night for a week, month, year or everything, with a moving
 * average for trends and full moons marked. Below it the nights of the shown period.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SleepHistory(nights: List<NightSummary>, onOpenReport: (Long) -> Unit, modifier: Modifier = Modifier) {
    var range by rememberSaveable { mutableStateOf(HistoryRange.WEEK) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var metric by rememberSaveable { mutableStateOf(HistoryMetric.SCORE) }
    var selected by remember(range, offset, metric) { mutableStateOf<LocalDate?>(null) }
    var listLimit by remember(range, offset) { mutableIntStateOf(LIST_PAGE) }

    val today = LocalDate.now()
    val daily = remember(nights) { dailyNights(nights) }
    val first = daily.firstOrNull()?.first
    val period = historyPeriod(range, today, offset, first)
    val points = remember(daily, period, metric) {
        daily.filter { it.first in period }.mapNotNull { (date, night) -> metric.of(night)?.let { ChartPoint(date, night, it) } }
    }
    val average = points.map { it.value }.average().toFloat()
    val previous = if (range == HistoryRange.ALL) {
        null
    } else {
        val prev = historyPeriod(range, today, offset + 1)
        daily.filter { it.first in prev }.mapNotNull { metric.of(it.second) }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    }

    Column(modifier) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            HistoryRange.entries.forEachIndexed { index, r ->
                SegmentedButton(
                    selected = range == r,
                    onClick = {
                        range = r
                        offset = 0
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, HistoryRange.entries.size),
                    modifier = Modifier.height(48.dp),
                ) { Text(r.title) }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val canMove = range != HistoryRange.ALL
            IconButton(
                onClick = { offset++ },
                enabled = canMove && first != null && first.isBefore(period.from),
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Früher") }
            Text(
                periodLabel(range, period),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { offset-- }, enabled = canMove && offset > 0, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Später")
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryMetric.entries.forEach { m ->
                FilterChip(selected = metric == m, onClick = { metric = m }, label = { Text(m.title) })
            }
        }
        Spacer(Modifier.height(8.dp))

        val selectedPoint = selected?.let { d -> points.firstOrNull { it.date == d } }
        Text(
            when {
                selectedPoint != null -> buildString {
                    append(formatNightDate(selectedPoint.night.start, short = true))
                    append(" · ")
                    append(metric.long(selectedPoint.value))
                    if (metric != HistoryMetric.SCORE) append(" · Schlafwert ${selectedPoint.night.score}")
                    moonPhase(selectedPoint.date)?.let { append(" · ${it.title}") }
                }
                points.isEmpty() -> "Keine Nächte in diesem Zeitraum"
                else -> buildString {
                    append("Ø ${metric.long(average)} · ${points.size} ${if (points.size == 1) "Nacht" else "Nächte"}")
                    previous?.let { append(" · ${range.previousLabel} Ø ${metric.short(it)}") }
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (selectedPoint != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(44.dp),
        )

        HistoryChart(period, range, metric, points, selected) { f ->
            val day = period.from.plusDays((f * period.days).toLong().coerceAtMost(period.days - 1L))
            selected = points.minByOrNull { kotlin.math.abs(period.indexOf(it.date) - period.indexOf(day)) }?.date
        }
        AxisLabels(axisLabels(range, period), period.days)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LegendItem(metric.color(), metric.title)
            if (range != HistoryRange.WEEK && points.size >= 3) LegendItem(TrendColor, "Durchschnitt (${trendWindow(range)} Tage)")
            if (period.days <= 800) LegendItem(MoonColor, "Vollmond", round = true)
        }
        if (selectedPoint != null) {
            TextButton(onClick = { onOpenReport(selectedPoint.night.start) }) { Text("Diese Nacht ansehen") }
        }

        val inPeriod = nights.filter { nightDate(it.start) in period }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            inPeriod.take(listLimit).forEach { NightCard(it) { onOpenReport(it.start) } }
        }
        if (inPeriod.size > listLimit) {
            TextButton(onClick = { listLimit += LIST_PAGE }, modifier = Modifier.padding(top = 4.dp)) {
                Text("Weitere ${minOf(LIST_PAGE, inPeriod.size - listLimit)} Nächte anzeigen")
            }
        }
    }
}

private val TrendColor = Color.White.copy(alpha = 0.85f)

private fun trendWindow(range: HistoryRange) = if (range == HistoryRange.MONTH) 7 else 30

@Composable
private fun HistoryMetric.color(): Color = when (this) {
    HistoryMetric.SCORE -> MaterialTheme.colorScheme.primary
    HistoryMetric.DURATION -> CalmColor
    HistoryMetric.SNORING -> SnoreColor
    HistoryMetric.APNEA -> Danger
}

/** Value with unit, e.g. "7 Std. 2 Min. Schlaf". */
private fun HistoryMetric.long(value: Float): String = when (this) {
    HistoryMetric.SCORE -> "Schlafwert ${value.roundToInt()}"
    HistoryMetric.DURATION -> "${formatDuration((value * 60).roundToInt())} Schlaf"
    HistoryMetric.SNORING -> "${value.roundToInt()} Min. Schnarchen"
    HistoryMetric.APNEA -> "%.1f Atemaussetzer/Std.".format(Locale.GERMAN, value)
}

private fun HistoryMetric.short(value: Float): String = when (this) {
    HistoryMetric.SCORE -> "${value.roundToInt()}"
    HistoryMetric.DURATION -> formatDuration((value * 60).roundToInt())
    HistoryMetric.SNORING -> "${value.roundToInt()} Min."
    HistoryMetric.APNEA -> "%.1f/Std.".format(Locale.GERMAN, value)
}

private val HistoryRange.previousLabel: String
    get() = when (this) {
        HistoryRange.WEEK -> "Vorwoche"
        HistoryRange.MONTH -> "Vormonat"
        HistoryRange.YEAR -> "Vorjahr"
        HistoryRange.ALL -> ""
    }

private fun periodLabel(range: HistoryRange, period: HistoryPeriod): String = when (range) {
    HistoryRange.WEEK -> "${DAY_MONTH.format(period.from)} – ${DAY_MONTH_YEAR.format(period.to)}"
    HistoryRange.MONTH -> MONTH_YEAR.format(period.from).replaceFirstChar { it.uppercase() }
    HistoryRange.YEAR -> "${period.from.year}"
    HistoryRange.ALL -> "Seit ${DAY_MONTH_YEAR.format(period.from)}"
}

/** Upper end of the value axis and labelled guide lines. */
private fun axis(metric: HistoryMetric, max: Float?): Pair<Float, List<Pair<Float, String>>> {
    val m = max ?: 0f
    return when (metric) {
        HistoryMetric.SCORE -> 100f to listOf(50f to "50", 100f to "100")
        HistoryMetric.DURATION -> maxOf(10f, m) to listOf(4f to "4 Std.", 8f to "8 Std.")
        HistoryMetric.SNORING -> {
            val top = maxOf(30f, m)
            val step = if (top <= 60f) 15f else if (top <= 180f) 60f else 120f
            top to generateSequence(step) { it + step }.takeWhile { it <= top }.map { it to "${it.roundToInt()} Min." }.toList()
        }
        // Limits of the AHI classification (see apneaLevel).
        HistoryMetric.APNEA -> maxOf(20f, m) to listOf(5f to "5/Std.", 15f to "15/Std.").filter { it.first <= maxOf(20f, m) }
    }
}

@Composable
private fun HistoryChart(
    period: HistoryPeriod,
    range: HistoryRange,
    metric: HistoryMetric,
    points: List<ChartPoint>,
    selected: LocalDate?,
    onSelect: (Float) -> Unit,
) {
    val color = metric.color()
    val outline = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = muted)
    val measurer = rememberTextMeasurer()
    val (maxValue, guides) = axis(metric, points.maxOfOrNull { it.value })
    val days = period.days
    val bars = days <= 62
    val trend = remember(points, range) {
        if (range == HistoryRange.WEEK || points.size < 3) emptyList()
        else rollingAverage(points.map { it.date to it.value }, trendWindow(range))
    }
    val fullMoons = remember(period) { if (days <= 800) fullMoonNights(period.from, period.to) else emptyList() }
    val select by rememberUpdatedState(onSelect)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .inspect { f -> f?.let { select(it) } },
    ) {
        val w = size.width
        val h = size.height
        val top = 18.dp.toPx()
        val slot = w / days
        val xOf = { d: LocalDate -> (period.indexOf(d) + 0.5f) * slot }
        val yOf = { v: Float -> h - (h - top) * (v / maxValue).coerceIn(0f, 1f) }

        guides.forEach { (v, label) ->
            val y = yOf(v)
            drawLine(outline, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            val text = measurer.measure(label, labelStyle)
            drawText(text, topLeft = Offset(w - text.size.width, y - text.size.height - 1.dp.toPx()))
        }
        drawLine(outline, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())

        fullMoons.forEach { d -> drawCircle(MoonColor, radius = 4.dp.toPx(), center = Offset(xOf(d), top / 2)) }

        points.forEach { p ->
            val dim = selected != null && selected != p.date
            val c = if (dim) color.copy(alpha = 0.4f) else color
            val y = yOf(p.value)
            if (bars) {
                val barW = (slot * 0.6f).coerceIn(2.dp.toPx(), 24.dp.toPx())
                if (h - y >= 1f) {
                    drawRoundRect(
                        c,
                        topLeft = Offset(xOf(p.date) - barW / 2, y),
                        size = Size(barW, h - y),
                        cornerRadius = CornerRadius(minOf(barW / 2, 4.dp.toPx())),
                    )
                }
            } else {
                drawCircle(if (dim) c else c.copy(alpha = 0.7f), radius = 3.dp.toPx(), center = Offset(xOf(p.date), y))
            }
        }

        if (trend.size >= 2) {
            val path = Path()
            trend.forEachIndexed { i, (d, v) -> if (i == 0) path.moveTo(xOf(d), yOf(v)) else path.lineTo(xOf(d), yOf(v)) }
            drawPath(path, TrendColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }

        selected?.let { d ->
            val x = xOf(d)
            drawLine(Color.White.copy(alpha = 0.8f), Offset(x, top), Offset(x, h), strokeWidth = 2.dp.toPx())
            if (!bars) points.firstOrNull { it.date == d }?.let { drawCircle(color, radius = 5.dp.toPx(), center = Offset(x, yOf(it.value))) }
        }
    }
}

/** Labels below the chart as (day index, text). */
private fun axisLabels(range: HistoryRange, period: HistoryPeriod): List<Pair<Int, String>> = when (range) {
    HistoryRange.WEEK -> (0 until 7).map { it to WEEKDAY.format(period.from.plusDays(it.toLong())).removeSuffix(".") }
    HistoryRange.MONTH -> listOf(1, 8, 15, 22, 29).filter { it <= period.days }.map { it - 1 to "$it." }
    HistoryRange.YEAR -> (1..12).map { m ->
        period.indexOf(period.from.withMonth(m).withDayOfMonth(15)) to SHORT_MONTH.format(period.from.withMonth(m)).take(1)
    }
    HistoryRange.ALL -> {
        val months = generateSequence(period.from.withDayOfMonth(1)) { it.plusMonths(1) }.takeWhile { !it.isAfter(period.to) }.toList()
        if (months.size <= 14) {
            val every = if (months.size > 7) 2 else 1
            months.filterIndexed { i, _ -> i % every == 0 }
                .map { maxOf(period.indexOf(it.withDayOfMonth(15)), 0) to SHORT_MONTH.format(it).removeSuffix(".") }
        } else {
            (period.from.year..period.to.year).map { y ->
                period.indexOf(LocalDate.of(y, 7, 1)).coerceIn(0, period.days - 1) to "$y"
            }
        }
    }
}

@Composable
private fun AxisLabels(labels: List<Pair<Int, String>>, days: Int) {
    Box(Modifier.fillMaxWidth().height(20.dp)) {
        labels.forEach { (i, text) ->
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(xAlign(-1f + 2f * (i + 0.5f) / days)),
            )
        }
    }
}

@Composable
private fun NightCard(night: NightSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatNightDate(night.start), style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        append("${formatDuration(night.sleepMinutes)} Schlaf")
                        if (night.snoreMinutes > 0) append(" · ${night.snoreMinutes} Min. Schnarchen")
                        if (night.interrupted) append(" · unterbrochen")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${night.score}",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
