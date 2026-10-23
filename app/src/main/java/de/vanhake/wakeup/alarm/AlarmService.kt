package de.vanhake.wakeup.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.vanhake.wakeup.data.Alarm
import de.vanhake.wakeup.data.AlarmRepository
import de.vanhake.wakeup.ui.ringing.AlarmActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZonedDateTime
import kotlin.math.ceil

/** Foreground service that rings until the alarm is snoozed or its challenge is solved. */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var originalVolume: Int? = null
    private var current: Alarm? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AlarmRepository.init(this)
        when (intent?.action) {
            ACTION_FIRE -> onFire(intent.getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1))
            ACTION_SNOOZE -> onSnooze()
            ACTION_DISMISS -> onDismiss()
            ACTION_QUIET -> setQuiet(intent.getBooleanExtra(EXTRA_QUIET, false))
            else -> if (current == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun onFire(alarmId: Int) {
        val alarm = AlarmRepository.get(alarmId)
        // startForegroundService() obliges us to call startForeground() in every case.
        goForeground(alarm ?: current)

        val ringing = current
        if (ringing != null) {
            if (ringing.id != alarmId && alarm != null) {
                // Another alarm is already ringing: try this one again in a minute.
                AlarmScheduler.scheduleAt(this, alarmId, System.currentTimeMillis() + 60_000)
            }
            return
        }
        if (alarm == null || !alarm.enabled) {
            stopEverything()
            return
        }

        Notifications.cancelSnoozed(this, alarm.id)
        val updated = alarm.copy(snoozedUntil = null)
        AlarmRepository.upsert(updated)
        if (updated.isRepeating) {
            // Make sure tomorrow's alarm is registered even if this one is never dismissed.
            AlarmScheduler.scheduleAt(
                this, updated.id,
                AlarmScheduler.nextRegularTrigger(updated, ZonedDateTime.now().plusMinutes(1)),
            )
        }
        current = updated
        _ringingAlarmId.value = updated.id
        startRinging(updated)

        try {
            startActivity(
                Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            // Background activity starts may be blocked; the full-screen notification covers it.
            Log.w(TAG, "Could not start alarm activity directly", e)
        }
    }

    private fun onSnooze() {
        val alarm = current?.let { AlarmRepository.get(it.id) ?: it } ?: return stopEverything()
        if (alarm.snoozesLeft <= 0) return
        val until = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
        val updated = alarm.copy(snoozesUsed = alarm.snoozesUsed + 1, snoozedUntil = until)
        AlarmRepository.upsert(updated)
        AlarmScheduler.scheduleAt(this, updated.id, until)
        Notifications.showSnoozed(this, updated, until)
        stopEverything()
    }

    private fun onDismiss() {
        val alarm = current?.let { AlarmRepository.get(it.id) ?: it } ?: return stopEverything()
        val updated = alarm.copy(
            snoozesUsed = 0,
            snoozedUntil = null,
            enabled = alarm.enabled && alarm.isRepeating,
        )
        AlarmRepository.upsert(updated)
        AlarmScheduler.schedule(this, updated)
        Notifications.cancelSnoozed(this, updated.id)
        stopEverything()
    }

    private fun goForeground(alarm: Alarm?) {
        val notification = Notifications.ringing(this, alarm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                ServiceCompat.startForeground(
                    this, Notifications.ID_RINGING, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
                )
                return
            } catch (e: Exception) {
                Log.w(TAG, "systemExempted foreground service refused, using mediaPlayback", e)
            }
        }
        ServiceCompat.startForeground(
            this, Notifications.ID_RINGING, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )
    }

    private fun startRinging(alarm: Alarm) {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeUp:ringing")
            .apply { acquire(60 * 60 * 1000L) }

        val audio = getSystemService(AudioManager::class.java)
        try {
            originalVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val target = ceil(max * alarm.volume.coerceIn(0.1f, 1f)).toInt().coerceIn(1, max)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not set alarm volume", e)
        }

        player = createPlayer(alarm)
        if (alarm.vibrate) startVibration()
    }

    private fun createPlayer(alarm: Alarm): MediaPlayer? {
        val candidates = listOfNotNull(
            alarm.ringtoneUri?.let(Uri::parse),
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.prepare()
                mp.start()
                return mp
            } catch (e: Exception) {
                Log.w(TAG, "Could not play $uri", e)
                mp.release()
            }
        }
        return null
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator = v
        @Suppress("DEPRECATION")
        v.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
        )
    }

    /** Lowers the sound while the user is working on the challenge. */
    private fun setQuiet(quiet: Boolean) {
        val alarm = current ?: return
        val level = if (quiet) 0.15f else 1f
        player?.setVolume(level, level)
        if (alarm.vibrate) {
            if (quiet) vibrator?.cancel() else startVibration()
        }
    }

    private fun stopEverything() {
        player?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        originalVolume?.let {
            try {
                getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, it, 0)
            } catch (_: SecurityException) {
            }
        }
        originalVolume = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        current = null
        _ringingAlarmId.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (current != null) stopEverything()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        private const val ACTION_FIRE = "de.vanhake.wakeup.service.FIRE"
        private const val ACTION_SNOOZE = "de.vanhake.wakeup.service.SNOOZE"
        private const val ACTION_DISMISS = "de.vanhake.wakeup.service.DISMISS"
        private const val ACTION_QUIET = "de.vanhake.wakeup.service.QUIET"
        private const val EXTRA_QUIET = "quiet"

        private val _ringingAlarmId = MutableStateFlow<Int?>(null)

        /** Id of the alarm that is currently ringing, null if none. */
        val ringingAlarmId: StateFlow<Int?> = _ringingAlarmId.asStateFlow()

        fun fire(context: Context, alarmId: Int) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java)
                    .setAction(ACTION_FIRE)
                    .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
            )
        }

        fun snooze(context: Context) = send(context, Intent(ACTION_SNOOZE))

        fun dismiss(context: Context) = send(context, Intent(ACTION_DISMISS))

        fun setQuiet(context: Context, quiet: Boolean) =
            send(context, Intent(ACTION_QUIET).putExtra(EXTRA_QUIET, quiet))

        private fun send(context: Context, intent: Intent) {
            if (_ringingAlarmId.value == null) return
            context.startService(intent.setClass(context, AlarmService::class.java))
        }
    }
}
