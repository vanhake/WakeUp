package de.vanhake.wakeup.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import de.vanhake.wakeup.alarm.AlarmScheduler
import de.vanhake.wakeup.alarm.WEEKDAY_SET
import de.vanhake.wakeup.alarm.WEEKEND_SET
import de.vanhake.wakeup.alarm.shortDayName
import de.vanhake.wakeup.data.Alarm
import de.vanhake.wakeup.data.AlarmRepository
import de.vanhake.wakeup.data.BarcodeRepository
import de.vanhake.wakeup.data.ChallengeType
import de.vanhake.wakeup.data.MathDifficulty
import de.vanhake.wakeup.ui.components.BigButton
import de.vanhake.wakeup.ui.components.BigOutlinedButton
import de.vanhake.wakeup.ui.components.SectionTitle
import de.vanhake.wakeup.ui.theme.Danger
import java.time.DayOfWeek
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAlarmScreen(
    initial: Alarm,
    onChange: (Alarm) -> Unit,
    onClose: () -> Unit,
    onTest: (Alarm) -> Unit,
) {
    val context = LocalContext.current
    val alarm = initial
    val currentAlarm by rememberUpdatedState(alarm)
    val isNew = AlarmRepository.get(alarm.id) == null
    var scanning by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val timeState = rememberTimePickerState(initialHour = alarm.hour, initialMinute = alarm.minute, is24Hour = true)
    LaunchedEffect(timeState) {
        snapshotFlow { timeState.hour to timeState.minute }.collect { (h, m) ->
            if (h != currentAlarm.hour || m != currentAlarm.minute) onChange(currentAlarm.copy(hour = h, minute = m))
        }
    }

    if (scanning) {
        AddBarcodeFlow(onDone = { barcode ->
            scanning = false
            if (barcode != null) onChange(currentAlarm.copy(barcodeId = barcode.id))
        })
        return
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Wecker löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    AlarmScheduler.cancel(context, alarm.id)
                    AlarmRepository.delete(alarm.id)
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
                title = { Text(if (isNew) "Neuer Wecker" else "Wecker bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Schließen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                BigButton(text = "Speichern", onClick = { save(context, alarm, onClose) })
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(
                    state = timeState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.surface,
                        timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }

            OutlinedTextField(
                value = alarm.label,
                onValueChange = { onChange(alarm.copy(label = it.take(40))) },
                label = { Text("Name / Label") },
                placeholder = { Text("z. B. Arbeit, Training …") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Tage")
            DaySelector(alarm.days) { onChange(alarm.copy(days = it)) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetChip("Werktags", alarm.days == WEEKDAY_SET) { onChange(alarm.copy(days = WEEKDAY_SET)) }
                PresetChip("Wochenende", alarm.days == WEEKEND_SET) { onChange(alarm.copy(days = WEEKEND_SET)) }
                PresetChip("Täglich", alarm.days.size == 7) { onChange(alarm.copy(days = DayOfWeek.values().toSet())) }
            }
            Text(
                if (alarm.days.isEmpty()) "Keine Tage gewählt: Der Wecker klingelt einmalig." else " ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            SectionTitle("Aufgabe zum Ausschalten")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChallengeType.entries.forEach { type ->
                    ChallengeOption(type, selected = alarm.challenge == type, modifier = Modifier.weight(1f)) {
                        val barcodeId = alarm.barcodeId?.takeIf { BarcodeRepository.get(it) != null }
                            ?: BarcodeRepository.barcodes.value.firstOrNull()?.id
                        onChange(alarm.copy(challenge = type, barcodeId = barcodeId))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            when (alarm.challenge) {
                ChallengeType.MATH -> MathSettings(alarm, onChange)
                ChallengeType.SHAKE -> LabeledSlider(
                    label = "Anzahl Schüttler: ${alarm.shakeCount}",
                    value = alarm.shakeCount.toFloat(),
                    range = 10f..150f,
                    steps = 13,
                    onValue = { onChange(alarm.copy(shakeCount = (it / 10).roundToInt() * 10)) },
                )
                ChallengeType.BARCODE -> BarcodeSettings(
                    alarm,
                    onSelect = { onChange(alarm.copy(barcodeId = it)) },
                    onAdd = { scanning = true },
                )
            }
            Spacer(Modifier.height(12.dp))
            BigOutlinedButton(
                text = "Aufgabe testen",
                icon = Icons.Filled.PlayArrow,
                onClick = {
                    if (missingBarcode(alarm)) {
                        Toast.makeText(context, "Bitte zuerst einen Barcode auswählen", Toast.LENGTH_SHORT).show()
                    } else {
                        onTest(alarm)
                    }
                },
            )

            SectionTitle("Schlummern")
            LabeledSlider(
                label = if (alarm.maxSnoozes == 0) "Schlummern deaktiviert" else "Maximal ${alarm.maxSnoozes}× schlummern",
                value = alarm.maxSnoozes.toFloat(),
                range = 0f..10f,
                steps = 9,
                onValue = { onChange(alarm.copy(maxSnoozes = it.roundToInt())) },
            )
            if (alarm.maxSnoozes > 0) {
                LabeledSlider(
                    label = "Dauer: ${alarm.snoozeMinutes} Min",
                    value = alarm.snoozeMinutes.toFloat(),
                    range = 1f..30f,
                    steps = 28,
                    onValue = { onChange(alarm.copy(snoozeMinutes = it.roundToInt())) },
                )
            }

            SectionTitle("Ton")
            RingtoneRow(alarm, onChange)
            LabeledSlider(
                label = "Lautstärke: ${(alarm.volume * 100).roundToInt()} %",
                value = alarm.volume,
                range = 0.1f..1f,
                steps = 8,
                onValue = { onChange(alarm.copy(volume = (it * 10).roundToInt() / 10f)) },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onChange(alarm.copy(vibrate = !alarm.vibrate)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Vibration", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = alarm.vibrate, onCheckedChange = { onChange(alarm.copy(vibrate = it)) })
            }

            if (!isNew) {
                Spacer(Modifier.height(24.dp))
                BigOutlinedButton(
                    text = "Wecker löschen",
                    icon = Icons.Filled.Delete,
                    onClick = { confirmDelete = true },
                    contentColor = Danger,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun save(context: Context, alarm: Alarm, onClose: () -> Unit) {
    if (missingBarcode(alarm)) {
        Toast.makeText(context, "Bitte zuerst einen Barcode auswählen", Toast.LENGTH_SHORT).show()
        return
    }
    val saved = alarm.copy(enabled = true, snoozesUsed = 0, snoozedUntil = null)
    AlarmRepository.upsert(saved)
    AlarmScheduler.schedule(context, saved)
    toastNextRing(context, saved)
    onClose()
}

@Composable
private fun DaySelector(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.values().forEach { day ->
            val selected = day in days
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onChange(if (selected) days - day else days + day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    shortDayName(day),
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PresetChip(text: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    ) {
        Text(text, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ChallengeOption(type: ChallengeType, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                type.icon, contentDescription = null, modifier = Modifier.size(36.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(type.title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MathSettings(alarm: Alarm, onChange: (Alarm) -> Unit) {
    val labels = mapOf(
        MathDifficulty.EASY to "Leicht",
        MathDifficulty.MEDIUM to "Mittel",
        MathDifficulty.HARD to "Schwer",
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        MathDifficulty.entries.forEachIndexed { index, difficulty ->
            SegmentedButton(
                selected = alarm.mathDifficulty == difficulty,
                onClick = { onChange(alarm.copy(mathDifficulty = difficulty)) },
                shape = SegmentedButtonDefaults.itemShape(index, MathDifficulty.entries.size),
                modifier = Modifier.height(56.dp),
            ) { Text(labels.getValue(difficulty)) }
        }
    }
    Text(
        when (alarm.mathDifficulty) {
            MathDifficulty.EASY -> "z. B. 47 + 38"
            MathDifficulty.MEDIUM -> "z. B. 14 × 7 + 36"
            MathDifficulty.HARD -> "z. B. 23 × 8 + 17 × 6"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
    )
    LabeledSlider(
        label = "Anzahl Aufgaben: ${alarm.mathCount}",
        value = alarm.mathCount.toFloat(),
        range = 1f..10f,
        steps = 8,
        onValue = { onChange(alarm.copy(mathCount = it.roundToInt())) },
    )
}

private fun missingBarcode(alarm: Alarm): Boolean =
    alarm.challenge == ChallengeType.BARCODE && BarcodeRepository.get(alarm.barcodeId) == null

@Composable
private fun BarcodeSettings(alarm: Alarm, onSelect: (Int) -> Unit, onAdd: () -> Unit) {
    val barcodes by BarcodeRepository.barcodes.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (barcodes.isEmpty()) {
            Text(
                "Noch keine Barcodes gespeichert – scanne jetzt einen.",
                style = MaterialTheme.typography.bodyLarge,
                color = Danger,
            )
        }
        barcodes.forEach { barcode ->
            val selected = alarm.barcodeId == barcode.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    )
                    .clickable { onSelect(barcode.id) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = { onSelect(barcode.id) })
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(barcode.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        barcode.value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        BigOutlinedButton(
            text = "Neuen Barcode scannen",
            icon = Icons.Filled.QrCodeScanner,
            onClick = onAdd,
            height = 60.dp,
        )
        Text(
            "Alle Barcodes verwaltest du in den Einstellungen (Zahnrad in der Weckerliste).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValue: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Slider(value = value, onValueChange = onValue, valueRange = range, steps = steps)
    }
}

@Composable
private fun RingtoneRow(alarm: Alarm, onChange: (Alarm) -> Unit) {
    val context = LocalContext.current
    val currentAlarm by rememberUpdatedState(alarm)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            val isDefault = uri == null || RingtoneManager.isDefault(uri)
            onChange(currentAlarm.copy(ringtoneUri = if (isDefault) null else uri.toString()))
        }
    }
    val title = remember(alarm.ringtoneUri) { ringtoneTitle(context, alarm.ringtoneUri) }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                val existing = alarm.ringtoneUri?.let(Uri::parse)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                picker.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Weckton"),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Weckton", style = MaterialTheme.typography.bodyLarge)
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun ringtoneTitle(context: Context, uri: String?): String {
    if (uri == null) return "Standard-Weckton"
    return try {
        RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) ?: "Unbekannt"
    } catch (_: Exception) {
        "Unbekannt"
    }
}
