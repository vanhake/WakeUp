package de.vanhake.wakeup.ui.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.vanhake.wakeup.sleep.analysis.RestState
import de.vanhake.wakeup.sleep.analysis.ScoreParts
import de.vanhake.wakeup.sleep.analysis.SleepReport
import kotlin.math.roundToInt

/** How the score of this night is made up: points per part out of its maximum. */
@Composable
fun ScoreBreakdown(report: SleepReport) {
    val parts = report.scoreParts ?: return
    val rows = listOf(
        Triple("Schlafdauer", formatDuration(report.sleepMinutes), "volle Punkte bei 7–9 Std."),
        Triple("Effizienz", "${(report.efficiency * 100).roundToInt()} %", "volle Punkte ab 95 %"),
        Triple("Ruhe", "${formatDuration(report.restlessMinutes)} unruhig", "volle Punkte ohne Unruhe"),
        Triple("Wachphasen", "${report.awakenings}", "volle Punkte ohne Wachphase"),
        Triple("Einschlafen", report.latencyMinutes?.let { formatDuration(it) } ?: "–", "volle Punkte bis 20 Min."),
    )
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEachIndexed { i, (label, value, hint) ->
                val max = (ScoreParts.WEIGHTS[i] * 100).roundToInt()
                val points = parts.values[i] * max
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            "${points.roundToInt()} / $max",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Meter(parts.values[i])
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$value · $hint",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Meter(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
            )
        }
    }
}

/** Expandable explanation of how the states, snoring and the score are determined. */
@Composable
fun AnalysisExplanation(report: SleepReport) {
    var open by rememberSaveable { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { open = !open }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("So entstehen die Werte", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (open) "Zuklappen" else "Aufklappen",
            )
        }
        if (open) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Paragraph(
                    "Grundlage",
                    "Die Nacht wird Minute für Minute bewertet. Dazu nutzt die App Geräusche vom Mikrofon" +
                        (if (report.usedMotion) ", Erschütterungen über den Bewegungssensor" else "") +
                        ", ob der Bildschirm an war und wie hell der Raum ist. Geräusche, während der Wecker " +
                        "klingelt, werden ignoriert.",
                )
                StateParagraph(
                    RestState.CALM,
                    "Keine nennenswerte Bewegung in dieser Minute.",
                )
                StateParagraph(
                    RestState.RESTLESS,
                    "Mindestens zwei Bewegungen in einer Minute (Rascheln, Erschütterung, Husten oder Sprechen) " +
                        "oder zusammen mindestens 2 Sekunden Bewegungsgeräusch. Wer in der Minute 10 Sekunden " +
                        "oder mehr schnarcht, gilt trotzdem als schlafend.",
                )
                StateParagraph(
                    RestState.AWAKE,
                    "Der Bildschirm war mindestens 10 Sekunden an, das Licht im Raum brannte noch (nur am " +
                        "Abend vor dem Lichtausschalten) oder du warst 6 Minuten oder länger am Stück unruhig. " +
                        "Außerdem alles vor dem Einschlafen und nach dem Aufwachen.",
                )
                StateParagraph(
                    RestState.SNOOZE,
                    "Zwischen dem ersten Klingeln und dem endgültigen Ausschalten, wenn du geschlummert hast " +
                        "und das Handy nicht benutzt hast. Du kannst dabei wieder eingenickt sein; die Zeit " +
                        "zählt aber nicht als Schlaf.",
                )
                Paragraph(
                    "Einschlafen und Aufwachen",
                    "Eingeschlafen bist du zu Beginn der ersten 15 Minuten ohne Wach-Minute und mit höchstens " +
                        "3 unruhigen Minuten. „Im Bett“ beginnt mit dem letzten Bildschirm- oder Licht-Ausschalten " +
                        "davor. Aufgewacht bist du, als der Wecker zum ersten Mal geklingelt hat – auch bei " +
                        "Smart Wake – oder früher, wenn du direkt davor schon wach warst.",
                )
                Paragraph(
                    "Schnarchen",
                    "Schnarchen sind mindestens 4 gleichmäßige Atemgeräusche hintereinander (je 0,25–3,5 Sekunden " +
                        "lang, alle 1,5–10 Sekunden), die überwiegend tief klingen und mindestens 10 dB lauter als " +
                        "der Raum sind" +
                        (if (report.classifierUsed) ", oder Geräusche, die die KI-Erkennung als Schnarchen einstuft" else "") +
                        ". Gezählt wird nur Schnarchen während des Schlafs; der Anteil bezieht sich auf die Schlafzeit.",
                )
                Paragraph(
                    "Effizienz",
                    "Schlafzeit geteilt durch die Zeit im Bett, also vom Hinlegen bis zum Aufwachen.",
                )
                Paragraph(
                    "Schlafwert",
                    "Der Schlafwert (0–100) setzt sich aus fünf Teilen zusammen:\n" +
                        "• Schlafdauer, 40 Punkte: voll bei 7–9 Std., ab 3 Std. linear steigend, über 9 Std. " +
                        "20 % weniger je Stunde (mindestens 60 %).\n" +
                        "• Effizienz, 20 Punkte: 0 bei 65 %, voll ab 95 %.\n" +
                        "• Ruhe, 15 Punkte: voll ohne Unruhe, 0 wenn 30 % der Schlafzeit unruhig waren.\n" +
                        "• Wachphasen, 15 Punkte: je Wachphase ein Sechstel weniger, ab 6 keine Punkte.\n" +
                        "• Einschlafdauer, 10 Punkte: voll bis 20 Min., bei 60 Min. keine Punkte.",
                )
            }
        }
    }
}

@Composable
private fun StateParagraph(state: RestState, text: String) {
    Paragraph(state.label, text, state.color)
}

@Composable
private fun Paragraph(title: String, text: String, color: Color? = null) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (color != null) {
                Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(8.dp))
            }
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
