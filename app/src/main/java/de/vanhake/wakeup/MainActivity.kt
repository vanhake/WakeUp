package de.vanhake.wakeup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.vanhake.wakeup.alarm.AlarmService
import de.vanhake.wakeup.data.Alarm
import de.vanhake.wakeup.data.AlarmRepository
import de.vanhake.wakeup.ui.AlarmListScreen
import de.vanhake.wakeup.ui.EditAlarmScreen
import de.vanhake.wakeup.ui.SettingsScreen
import de.vanhake.wakeup.ui.challenge.ChallengeHost
import de.vanhake.wakeup.ui.sleep.SleepReportScreen
import de.vanhake.wakeup.ui.sleep.SleepScreen
import de.vanhake.wakeup.ui.components.BigOutlinedButton
import de.vanhake.wakeup.ui.ringing.RingingRoute
import de.vanhake.wakeup.ui.theme.WakeUpTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AlarmRepository.init(this)
        handleIntent(intent)
        setContent {
            WakeUpTheme {
                App()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val report = intent?.getLongExtra(EXTRA_SLEEP_REPORT, -1L) ?: -1L
        if (report > 0) openReport.value = report
        intent?.removeExtra(EXTRA_SLEEP_REPORT)
    }

    companion object {
        const val EXTRA_SLEEP_REPORT = "sleep_report"

        /** Sleep report to open, set when the app is opened from the report notification. */
        val openReport = MutableStateFlow<Long?>(null)
    }
}

private val AlarmSaver = Saver<Alarm?, String>(
    save = { it?.toJson()?.toString() ?: "" },
    restore = { if (it.isEmpty()) null else Alarm.fromJson(JSONObject(it)) },
)

@Composable
private fun App() {
    var editing by rememberSaveable(stateSaver = AlarmSaver) { mutableStateOf<Alarm?>(null) }
    var preview by rememberSaveable(stateSaver = AlarmSaver) { mutableStateOf<Alarm?>(null) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var sleep by rememberSaveable { mutableStateOf(false) }
    var report by rememberSaveable { mutableStateOf<Long?>(null) }
    val ringingId by AlarmService.ringingAlarmId.collectAsState()
    val requestedReport by MainActivity.openReport.collectAsState()
    LaunchedEffect(requestedReport) {
        requestedReport?.let {
            report = it
            MainActivity.openReport.value = null
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val previewAlarm = preview
        val editAlarm = editing
        when {
            // While an alarm rings, the app shows nothing but the ringing screen.
            ringingId != null -> {
                BackHandler { }
                RingingRoute()
            }
            report != null -> {
                BackHandler { report = null }
                SleepReportScreen(start = report!!, onClose = { report = null })
            }
            sleep -> {
                BackHandler { sleep = false }
                SleepScreen(onClose = { sleep = false }, onOpenReport = { report = it })
            }
            settings -> {
                BackHandler { settings = false }
                SettingsScreen(onClose = { settings = false })
            }
            previewAlarm != null -> {
                BackHandler { preview = null }
                ChallengePreview(previewAlarm, onClose = { preview = null })
            }
            editAlarm != null -> {
                BackHandler { editing = null }
                EditAlarmScreen(
                    initial = editAlarm,
                    onChange = { editing = it },
                    onClose = { editing = null },
                    onTest = { preview = it },
                )
            }
            else -> AlarmListScreen(
                onEdit = { editing = it },
                onCreate = { editing = Alarm(id = AlarmRepository.nextId()) },
                onSettings = { settings = true },
                onSleep = { sleep = true },
                onOpenReport = { report = it },
            )
        }
    }
}

@Composable
private fun ChallengePreview(alarm: Alarm, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Text(
            "Testlauf – so beendest du den Wecker",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp),
        )
        Box(Modifier.weight(1f)) {
            ChallengeHost(alarm = alarm, onSolved = onClose, onInteraction = {})
        }
        BigOutlinedButton(
            text = "Test abbrechen",
            icon = Icons.Filled.Close,
            onClick = onClose,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
