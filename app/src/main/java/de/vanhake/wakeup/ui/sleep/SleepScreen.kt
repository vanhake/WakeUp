package de.vanhake.wakeup.ui.sleep

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.vanhake.wakeup.sleep.NightSummary
import de.vanhake.wakeup.sleep.RecordingMode
import de.vanhake.wakeup.sleep.SleepRepository
import de.vanhake.wakeup.sleep.SleepScheduler
import de.vanhake.wakeup.sleep.SleepSettings
import de.vanhake.wakeup.sleep.SleepTrackingService
import de.vanhake.wakeup.ui.components.BigButton
import de.vanhake.wakeup.ui.components.BigOutlinedButton
import de.vanhake.wakeup.ui.components.SectionTitle
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(onClose: () -> Unit, onOpenReport: (Long) -> Unit) {
    val context = LocalContext.current
    val settings by SleepRepository.settings.collectAsState()
    val nights by SleepRepository.nights.collectAsState()
    val recordingSince by SleepTrackingService.recordingSince.collectAsState()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var editingTime by remember { mutableStateOf<TimeField?>(null) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        if (granted) pendingAction?.invoke()
        pendingAction = null
    }
    fun withMic(action: () -> Unit) {
        if (hasMic(context)) {
            action()
        } else {
            pendingAction = action
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    fun update(new: SleepSettings) {
        SleepRepository.updateSettings(new)
        SleepScheduler.scheduleNextStart(context)
    }

    val micGranted = remember(lifecycleState == Lifecycle.State.RESUMED, refresh) { hasMic(context) }
    val overlayGranted = remember(lifecycleState == Lifecycle.State.RESUMED) { SystemSettings.canDrawOverlays(context) }

    editingTime?.let { field ->
        TimeDialog(
            title = if (field == TimeField.START) "Aufzeichnung startet um" else "Ende ohne Wecker",
            hour = if (field == TimeField.START) settings.startHour else settings.endHour,
            minute = if (field == TimeField.START) settings.startMinute else settings.endMinute,
            onConfirm = { h, m ->
                update(
                    if (field == TimeField.START) settings.copy(startHour = h, startMinute = m)
                    else settings.copy(endHour = h, endMinute = m),
                )
                editingTime = null
            },
            onDismiss = { editingTime = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Schlafanalyse") },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Schließen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            RecordingCard(
                since = recordingSince,
                onStart = { withMic { SleepTrackingService.start(context) } },
                onStop = { SleepTrackingService.stop() },
            )

            if (settings.mode != RecordingMode.OFF && !micGranted) {
                Spacer(Modifier.height(12.dp))
                Warning("Mikrofon erlauben, sonst kann nicht aufgezeichnet werden.", "Erlauben") {
                    withMic {}
                }
            }
            if (settings.mode != RecordingMode.OFF && !overlayGranted) {
                Spacer(Modifier.height(12.dp))
                Warning("„Über anderen Apps einblenden“ erlauben, damit die Aufzeichnung von selbst startet.", "Öffnen") {
                    context.startActivity(
                        Intent(SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                    )
                }
            }

            SectionTitle("Automatisch aufzeichnen")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                RecordingMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.mode == mode,
                        onClick = {
                            if (mode == RecordingMode.OFF) update(settings.copy(mode = mode))
                            else withMic { update(SleepRepository.settings.value.copy(mode = mode)) }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, RecordingMode.entries.size),
                        modifier = Modifier.height(56.dp),
                    ) { Text(mode.title) }
                }
            }
            Text(
                when (settings.mode) {
                    RecordingMode.OFF -> "Nur manuell über „Jetzt aufzeichnen“."
                    RecordingMode.WITH_ALARM ->
                        "Startet jeden Abend zur Startzeit, wenn für die Nacht ein Wecker gestellt ist, " +
                            "und endet, wenn du den Wecker ausschaltest."
                    RecordingMode.ALWAYS ->
                        "Startet jeden Abend zur Startzeit. Endet mit dem Wecker, ohne Wecker zur Endzeit."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
            if (settings.mode != RecordingMode.OFF) {
                SettingRow("Startzeit", "%02d:%02d".format(settings.startHour, settings.startMinute)) {
                    editingTime = TimeField.START
                }
            }
            if (settings.mode == RecordingMode.ALWAYS) {
                SettingRow("Ende ohne Wecker", "%02d:%02d".format(settings.endHour, settings.endMinute)) {
                    editingTime = TimeField.END
                }
            }
            Text(
                "Zur Startzeit erscheint kurz ein Bildschirm „Schlafaufzeichnung läuft“, weil Android das Mikrofon " +
                    "nur im Vordergrund starten lässt. Dort kannst du die Aufzeichnung für heute auch abbrechen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            SectionTitle("Hörproben")
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { update(settings.copy(saveClips = !settings.saveClips)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Hörproben speichern", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Kurze Ausschnitte von Schnarchen, Atemaussetzern, Husten, Sprechen und lauten Geräuschen. " +
                            "Werden nach 24 Stunden automatisch gelöscht.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = settings.saveClips, onCheckedChange = {
                    update(settings.copy(saveClips = it))
                    if (!it) SleepRepository.deleteAllClips()
                })
            }

            SectionTitle("Letzte Nächte")
            if (nights.isEmpty()) {
                Text(
                    "Noch keine Nacht aufgezeichnet.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                DurationBars(nights)
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    nights.forEach { NightCard(it) { onOpenReport(it.start) } }
                }
            }
            Text(
                "Alles wird nur auf dem Handy ausgewertet und gespeichert. Die Analyse ist keine medizinische " +
                    "Diagnose.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
    }
}

private enum class TimeField { START, END }

private fun hasMic(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Composable
private fun RecordingCard(since: Long?, onStart: () -> Unit, onStop: () -> Unit) {
    if (since == null) {
        BigButton(text = "Jetzt aufzeichnen", icon = Icons.Filled.Bedtime, onClick = onStart)
        return
    }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bedtime, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Aufzeichnung läuft seit ${formatTime(since)}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatDuration(((now - since) / 60_000).toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            BigOutlinedButton(text = "Beenden und auswerten", icon = Icons.Filled.Stop, onClick = onStop)
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

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Warning(text: String, action: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, hour: Int, minute: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                    timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
