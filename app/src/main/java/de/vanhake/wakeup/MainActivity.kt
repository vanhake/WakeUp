package de.vanhake.wakeup

import android.content.Context
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.vanhake.wakeup.alarm.AlarmService
import de.vanhake.wakeup.data.Alarm
import de.vanhake.wakeup.data.AlarmRepository
import de.vanhake.wakeup.sleep.SleepRepository
import de.vanhake.wakeup.sleep.SleepTrackingService
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
        if (intent?.getBooleanExtra(EXTRA_AFTER_ALARM, false) == true) {
            val night = intent.getLongExtra(EXTRA_NIGHT, -1L)
            afterAlarm.value = AfterAlarm(night.takeIf { it > 0 })
        }
        intent?.removeExtra(EXTRA_AFTER_ALARM)
        intent?.removeExtra(EXTRA_NIGHT)
    }

    /** The app opened after a solved alarm; [night] is the sleep recording that ended with it. */
    data class AfterAlarm(val night: Long?)

    companion object {
        const val EXTRA_SLEEP_REPORT = "sleep_report"
        private const val EXTRA_AFTER_ALARM = "after_alarm"
        private const val EXTRA_NIGHT = "night"

        /** Sleep report to open, set when the app is opened from the report notification. */
        val openReport = MutableStateFlow<Long?>(null)

        /** Set when the app is opened by the ringing screen after the challenge was solved. */
        val afterAlarm = MutableStateFlow<AfterAlarm?>(null)

        fun afterAlarmIntent(context: Context, night: Long?): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_AFTER_ALARM, true)
                .apply { if (night != null) putExtra(EXTRA_NIGHT, night) }
    }
}

/** How long to wait for the report of the night that ended with the alarm. */
private const val REPORT_WAIT_MS = 60_000L

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
    var awaitingReport by rememberSaveable { mutableStateOf<Long?>(null) }
    val ringingId by AlarmService.ringingAlarmId.collectAsState()
    val requestedReport by MainActivity.openReport.collectAsState()
    LaunchedEffect(requestedReport) {
        requestedReport?.let {
            report = it
            MainActivity.openReport.value = null
        }
    }
    // After a solved alarm: the night's sleep report if one was recorded, else the alarm list.
    val afterAlarm by MainActivity.afterAlarm.collectAsState()
    LaunchedEffect(afterAlarm) {
        afterAlarm?.let {
            editing = null
            preview = null
            settings = false
            sleep = false
            report = null
            awaitingReport = it.night
            MainActivity.afterAlarm.value = null
        }
    }
    LaunchedEffect(awaitingReport) {
        val night = awaitingReport ?: return@LaunchedEffect
        // The recording stops with the alarm; its report is ready once the night is saved.
        val saved = withTimeoutOrNull(REPORT_WAIT_MS) {
            combine(SleepRepository.nights, SleepTrackingService.recordingSince) { nights, recording ->
                when {
                    nights.any { it.start == night } -> true
                    recording != night -> false
                    else -> null
                }
            }.filterNotNull().first()
        }
        if (saved == true) report = night
        awaitingReport = null
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val previewAlarm = preview
        val editAlarm = editing
        when {
            // While an alarm rings, the app shows nothing but the ringing screen.
            ringingId != null -> {
                BackHandler { }
                RingingRoute(onSolved = { MainActivity.afterAlarm.value = MainActivity.AfterAlarm(it) })
            }
            awaitingReport != null -> {
                BackHandler { awaitingReport = null }
                AwaitingReport(onSkip = { awaitingReport = null })
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
private fun AwaitingReport(onSkip: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    "Schlafbericht wird erstellt …",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        BigOutlinedButton(text = "Zur Weckerliste", icon = Icons.Filled.Alarm, onClick = onSkip)
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
