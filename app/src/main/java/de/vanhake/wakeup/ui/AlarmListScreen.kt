package de.vanhake.wakeup.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.vanhake.wakeup.alarm.AlarmScheduler
import de.vanhake.wakeup.alarm.formatCountdown
import de.vanhake.wakeup.alarm.formatDays
import de.vanhake.wakeup.data.Alarm
import de.vanhake.wakeup.data.AlarmRepository
import de.vanhake.wakeup.ui.theme.AccentDim
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun AlarmListScreen(onEdit: (Alarm) -> Unit, onCreate: () -> Unit) {
    val context = LocalContext.current
    val alarms by AlarmRepository.alarms.collectAsState()
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(15_000)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = onCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Neuer Wecker", modifier = Modifier.size(40.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 140.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(alarms, now) }
            item { PermissionWarnings() }
            if (alarms.isEmpty()) {
                item { EmptyState(onCreate) }
            }
            items(alarms, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    now = now,
                    onClick = {
                        if (alarm.isSnoozed()) {
                            Toast.makeText(context, "Der Wecker schlummert – beende ihn über die Aufgabe.", Toast.LENGTH_SHORT).show()
                        } else {
                            onEdit(alarm)
                        }
                    },
                    onToggle = { enabled ->
                        val updated = alarm.copy(enabled = enabled, snoozesUsed = 0, snoozedUntil = null)
                        AlarmRepository.upsert(updated)
                        AlarmScheduler.schedule(context, updated)
                        if (enabled) toastNextRing(context, updated)
                    },
                )
            }
        }
    }
}

@Composable
private fun Header(alarms: List<Alarm>, now: ZonedDateTime) {
    val next = alarms.mapNotNull { nextRing(it, now) }.minOrNull()
    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text("WakeUp", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (next == null) "Kein Wecker aktiv" else "Nächster Wecker ${formatCountdown(now, next)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.AlarmAdd, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Noch keine Wecker", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onCreate) { Text("Ersten Wecker anlegen") }
    }
}

@Composable
private fun AlarmCard(alarm: Alarm, now: ZonedDateTime, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val snoozed = alarm.isSnoozed(now.toInstant().toEpochMilli())
    val active = alarm.enabled
    val faded = if (active) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (snoozed) AccentDim else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 24.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "%02d:%02d".format(alarm.hour, alarm.minute),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Light,
                    color = faded,
                )
                if (alarm.label.isNotBlank()) {
                    Text(alarm.label, style = MaterialTheme.typography.titleMedium, color = faded)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        alarm.challenge.icon, contentDescription = alarm.challenge.title,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        formatDays(alarm.days) + snoozeSummary(alarm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (snoozed) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Snooze, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Schlummert bis " + TIME_FORMAT.format(
                                Instant.ofEpochMilli(alarm.snoozedUntil!!).atZone(ZoneId.systemDefault()),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Switch(
                checked = active,
                onCheckedChange = onToggle,
                enabled = !snoozed,
                modifier = Modifier.scale(1.35f).padding(start = 8.dp),
            )
        }
    }
}

private fun snoozeSummary(alarm: Alarm): String =
    if (alarm.maxSnoozes == 0) " · ohne Schlummern" else " · ${alarm.maxSnoozes}× ${alarm.snoozeMinutes} Min"

private data class Warning(val text: String, val action: String, val onClick: () -> Unit)

@Composable
private fun PermissionWarnings() {
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var notificationsRefresh by remember { mutableStateOf(0) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsRefresh++
    }

    // Re-evaluated whenever the app comes back from the settings screens.
    val warnings = remember(lifecycleState == Lifecycle.State.RESUMED, notificationsRefresh) {
        buildWarnings(context) { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        warnings.forEach { warning ->
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WarningAmber, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(warning.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = warning.onClick) { Text(warning.action) }
                }
            }
        }
    }
}

private fun buildWarnings(context: Context, requestNotifications: () -> Unit): List<Warning> = buildList {
    val packageUri = Uri.parse("package:${context.packageName}")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        add(Warning("Benachrichtigungen erlauben, damit der Wecker angezeigt wird.", "Erlauben", requestNotifications))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    ) {
        add(
            Warning("Exakte Wecker sind nicht erlaubt – der Wecker könnte zu spät klingeln.", "Öffnen") {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))
            },
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    ) {
        add(
            Warning("Vollbild-Anzeige erlauben, damit der Wecker auf dem Sperrbildschirm erscheint.", "Öffnen") {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri))
            },
        )
    }
}
