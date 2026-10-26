package de.vanhake.wakeup.ui.sleep

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vanhake.wakeup.sleep.SleepRepository
import de.vanhake.wakeup.sleep.analysis.ApneaLevel
import de.vanhake.wakeup.sleep.analysis.NightRecording
import de.vanhake.wakeup.sleep.analysis.SleepAnalyzer
import de.vanhake.wakeup.sleep.analysis.SleepClip
import de.vanhake.wakeup.sleep.analysis.SleepReport
import de.vanhake.wakeup.sleep.analysis.apneaLevel
import de.vanhake.wakeup.ui.components.SectionTitle
import de.vanhake.wakeup.ui.theme.Danger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepReportScreen(start: Long, onClose: () -> Unit) {
    var data by remember { mutableStateOf<Pair<NightRecording, SleepReport>?>(null) }
    var missing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(start) {
        val result = withContext(Dispatchers.Default) {
            SleepRepository.loadNight(start)?.let { it to SleepAnalyzer.analyze(it) }
        }
        data = result
        missing = result == null
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Diese Nacht löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    SleepRepository.deleteNight(start)
                    onClose()
                }) { Text("Löschen", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(formatNightDate(start)) },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Schließen")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Nacht löschen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val loaded = data
        when {
            loaded != null -> ReportContent(loaded.first, loaded.second, Modifier.padding(padding))
            missing -> Text("Diese Nacht ist nicht mehr gespeichert.", modifier = Modifier.padding(padding).padding(20.dp))
            else -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun ReportContent(night: NightRecording, report: SleepReport, modifier: Modifier) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        if (night.interrupted) {
            Notice("Die Aufzeichnung wurde vorzeitig beendet (App wurde vom System gestoppt). Ausgewertet bis ${formatTime(night.end)}.")
            Spacer(Modifier.height(12.dp))
        }

        // Headline: the score with the most important facts next to it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${report.score}", fontSize = 64.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.primary)
                Text("Schlafwert", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(24.dp))
            Column {
                Text(formatDuration(report.sleepMinutes), style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(
                        report.sleepOnset?.let { "eingeschlafen ${formatTime(it)}" },
                        report.wakeUp?.let { "aufgewacht ${formatTime(it)}" },
                    ).joinToString("\n").ifEmpty { "Kein Schlaf erkannt" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionTitle("Verlauf")
        SleepTimeline(report)

        SectionTitle("Schlaf")
        StatGrid(
            listOf(
                "Im Bett" to formatDuration(report.timeInBedMinutes) +
                    (report.lightsOff?.let { " · Licht aus ${formatTime(it)}" } ?: ""),
                "Einschlafdauer" to (report.latencyMinutes?.let { formatDuration(it) } ?: "–"),
                "Effizienz" to "${(report.efficiency * 100).roundToInt()} %",
                "Wachphasen" to "${report.awakenings}",
                "Unruhig" to formatDuration(report.restlessMinutes),
                "Aufwachen" to when {
                    night.alarmRingAt == null -> "ohne Wecker"
                    night.snoozes == 0 -> if (night.smartWake) "Smart Wake" else "sofort"
                    else -> "${night.snoozes}× geschlummert"
                },
            ),
        )

        SectionTitle("Atmung & Schnarchen")
        StatGrid(
            listOf(
                "Schnarchen" to if (report.snoreMinutes == 0) "kaum" else "${formatDuration(report.snoreMinutes)} (${report.snorePercent} %)",
                "Lautstärke" to (report.snoreLoudness?.let { "+${it.roundToInt()} dB" } ?: "–"),
                "Atemaussetzer-Verdacht" to "${report.apneas.size}" + (report.apneasPerHour?.let { " (%.1f/Std.)".format(it) } ?: ""),
                "Atemfrequenz" to (report.breathingRate?.let { "${it.roundToInt()} /Min." } ?: "nicht hörbar"),
            ),
        )
        Spacer(Modifier.height(12.dp))
        ApneaText(report)

        if (report.classifierUsed) {
            Spacer(Modifier.height(12.dp))
            StatGrid(
                listOf(
                    "Husten" to if (report.coughs == 0) "keins" else "${report.coughs}×",
                    "Sprechen im Schlaf" to if (report.sleepTalkEpisodes == 0) "nein" else "${report.sleepTalkEpisodes}×",
                ),
            )
        }

        SectionTitle("Geräusche")
        NoiseChart(report)
        Spacer(Modifier.height(8.dp))
        val woke = report.disturbances.count { it.wokeUp }
        Text(
            when {
                report.disturbances.isEmpty() -> "Keine lauten Störgeräusche während des Schlafs."
                woke == 0 -> "${report.disturbances.size} laute Geräusche, danach bliebst du ruhig."
                else -> "${report.disturbances.size} laute Geräusche, nach $woke davon wurdest du unruhig oder wach."
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        val clips = night.clips.filter { SleepRepository.clipFile(it.file).exists() }
        if (clips.isNotEmpty()) {
            SectionTitle("Hörproben")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ClipList(clips)
            }
        }

        Text(
            "Hinweis: Die Schlafanalyse arbeitet mit Mikrofon" +
                (if (report.classifierUsed) ", KI-Geräuscherkennung (YAMNet, offline)" else "") +
                (if (report.usedMotion) " und Bewegungssensor" else "") +
                " und ist keine medizinische Diagnose. " +
                "Schnarcht oder spricht jemand neben dir, kann das Handy die Geräusche nicht sicher zuordnen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp),
        )
    }
}

@Composable
private fun StatGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(value, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ApneaText(report: SleepReport) {
    val perHour = report.apneasPerHour
    val text = when {
        perHour == null -> "Für eine Einschätzung von Atemaussetzern war die Schlafzeit zu kurz."
        report.breathingRate == null && report.apneas.isEmpty() ->
            "Deine Atmung war meist nicht hörbar, daher lassen sich Atemaussetzer nicht beurteilen."
        else -> when (apneaLevel(perHour)) {
            ApneaLevel.NORMAL -> "Atemaussetzer-Verdacht: unauffällig (unter 5 pro Stunde)."
            ApneaLevel.MILD -> "Atemaussetzer-Verdacht: leicht erhöht (5–15 pro Stunde). Beobachte das über mehrere Nächte."
            ApneaLevel.MODERATE, ApneaLevel.SEVERE ->
                "Atemaussetzer-Verdacht: ${apneaLevel(perHour).label} (%.0f pro Stunde). ".format(perHour) +
                    "Wenn das in mehreren Nächten vorkommt, lass es ärztlich abklären (z. B. Schlaflabor)."
        }
    }
    val serious = perHour != null && apneaLevel(perHour) >= ApneaLevel.MODERATE
    if (serious) Notice(text) else Text(text, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "Gezählt werden Pausen von mindestens 10 Sekunden in hörbarem Atmen oder Schnarchen. Flache Atmung " +
            "und Aussetzer ohne Geräusch erkennt das Mikrofon nicht.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ClipList(clips: List<SleepClip>) {
    var playing by remember { mutableStateOf<String?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    fun stop() {
        player[0]?.release()
        player[0] = null
        playing = null
    }
    DisposableEffect(Unit) { onDispose { stop() } }

    clips.sortedBy { it.timeMs }.forEach { clip ->
        val isPlaying = playing == clip.file
        val hoursLeft = ((SleepRepository.clipFile(clip.file).lastModified() + SleepRepository.CLIP_LIFETIME_MS -
            System.currentTimeMillis()) / 3_600_000).coerceAtLeast(0)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${formatTime(clip.timeMs)} · ${clip.type.label}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${clip.durationMs / 1000} s · noch ${if (hoursLeft < 1) "unter 1" else "$hoursLeft"} Std. verfügbar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        val wasPlaying = isPlaying
                        stop()
                        if (!wasPlaying) {
                            try {
                                player[0] = MediaPlayer().apply {
                                    setDataSource(SleepRepository.clipFile(clip.file).absolutePath)
                                    setOnCompletionListener { stop() }
                                    prepare()
                                    start()
                                }
                                playing = clip.file
                            } catch (_: Exception) {
                                stop()
                            }
                        }
                    },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Stopp" else "Abspielen",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
