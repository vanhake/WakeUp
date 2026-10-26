package de.vanhake.wakeup.sleep

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import de.vanhake.wakeup.data.AlarmRepository
import java.time.ZonedDateTime

class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START -> onStartTime(context)
            ACTION_STOP -> SleepTrackingService.stop()
            ACTION_CLEANUP -> SleepScheduler.cleanUpClips(context)
        }
    }

    private fun onStartTime(context: Context) {
        SleepRepository.init(context)
        AlarmRepository.init(context)
        SleepScheduler.scheduleNextStart(context)
        if (SleepTrackingService.recordingSince.value != null) return
        if (!shouldRecord(SleepRepository.settings.value, AlarmRepository.alarms.value, ZonedDateTime.now())) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            SleepNotifications.showProblem(context, "Das Mikrofon ist nicht erlaubt. Öffne WakeUp → Schlaf, um es zu erlauben.")
            return
        }
        // The microphone may only be started from the foreground, so open a short start screen.
        SleepNotifications.showStart(context)
        try {
            context.startActivity(
                Intent(context, SleepStartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            Log.w("SleepReceiver", "Could not open start screen directly", e)
        }
    }

    companion object {
        const val ACTION_START = "de.vanhake.wakeup.sleep.START"
        const val ACTION_STOP = "de.vanhake.wakeup.sleep.STOP"
        const val ACTION_CLEANUP = "de.vanhake.wakeup.sleep.CLEANUP"
    }
}
