package de.vanhake.wakeup.sleep

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.vanhake.wakeup.alarm.AlarmScheduler
import de.vanhake.wakeup.data.AlarmRepository
import de.vanhake.wakeup.sleep.analysis.Burst
import de.vanhake.wakeup.sleep.analysis.BurstDetector
import de.vanhake.wakeup.sleep.analysis.ClipRequest
import de.vanhake.wakeup.sleep.analysis.FRAME_MS
import de.vanhake.wakeup.sleep.analysis.FRAME_SIZE
import de.vanhake.wakeup.sleep.analysis.FrameAnalyzer
import de.vanhake.wakeup.sleep.analysis.LabelWindow
import de.vanhake.wakeup.sleep.analysis.LiveDetector
import de.vanhake.wakeup.sleep.analysis.MinuteLevel
import de.vanhake.wakeup.sleep.analysis.NightRecording
import de.vanhake.wakeup.sleep.analysis.SAMPLE_RATE
import de.vanhake.wakeup.sleep.analysis.ScreenEvent
import de.vanhake.wakeup.sleep.analysis.SleepClip
import de.vanhake.wakeup.sleep.analysis.YAMNET_WINDOW
import de.vanhake.wakeup.sleep.analysis.labelFromScores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.ZonedDateTime
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Records the night with the microphone. Audio is analysed on the fly and thrown away;
 * only the detected sounds, minute levels and (if enabled) a few short samples are kept.
 */
class SleepTrackingService : Service() {

    private val lock = Any()
    private val screen = mutableListOf<ScreenEvent>()
    private val ringing = mutableListOf<LongRange>()
    private var ringingSince: Long? = null
    private var alarmRingAt: Long? = null
    private var alarmDismissAt: Long? = null
    private var snoozes = 0
    private var smartWake = false
    private val motion = mutableListOf<Long>()
    @Volatile private var lux: Float? = null
    private var gravity: FloatArray? = null
    private var lastMotion = 0L

    @Volatile private var stopRequested = false
    @Volatile private var discard = false
    private var thread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var start = 0L
    private var deadline = 0L
    private val main = Handler(Looper.getMainLooper())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val on = intent.action == Intent.ACTION_SCREEN_ON
            synchronized(lock) { screen.add(ScreenEvent(System.currentTimeMillis(), on)) }
        }
    }

    /**
     * Light: brightness for "lights off". Accelerometer: if the phone lies on the mattress,
     * turning over shakes it; on the nightstand it feels nothing.
     */
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_LIGHT -> lux = event.values[0]
                Sensor.TYPE_ACCELEROMETER -> onAcceleration(event.values)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private fun onAcceleration(values: FloatArray) {
        val g = gravity ?: values.copyOf().also { gravity = it }
        var dynamic = 0f
        for (i in 0..2) {
            g[i] = 0.9f * g[i] + 0.1f * values[i]
            val d = values[i] - g[i]
            dynamic += d * d
        }
        val now = System.currentTimeMillis()
        if (sqrt(dynamic) > MOTION_THRESHOLD && now - lastMotion > 2_000) {
            lastMotion = now
            synchronized(lock) { motion.add(now - start) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (thread != null) return START_NOT_STICKY
        SleepRepository.init(this)
        AlarmRepository.init(this)
        start = System.currentTimeMillis()
        try {
            ServiceCompat.startForeground(
                this, SleepNotifications.ID_RECORDING, SleepNotifications.recording(this, start),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not start microphone service", e)
            SleepNotifications.showProblem(this, "Android hat den Zugriff auf das Mikrofon im Hintergrund verweigert.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            SleepNotifications.showProblem(this, "Das Mikrofon ist nicht erlaubt.")
            shutDown()
            return START_NOT_STICKY
        }

        deadline = recordingDeadline(SleepRepository.settings.value, AlarmRepository.alarms.value, ZonedDateTime.now())
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        screen.add(ScreenEvent(start, interactive))
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        getSystemService(SensorManager::class.java)?.let { sm ->
            listOf(Sensor.TYPE_LIGHT, Sensor.TYPE_ACCELEROMETER).forEach { type ->
                sm.getDefaultSensor(type)?.let { sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
            }
        }
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeUp:sleep")
            .apply { acquire(MAX_NIGHT.toMillis() + 60 * 60 * 1000L) }

        instance = this
        _recordingSince.value = start
        thread = Thread(::record, "SleepRecorder").apply { start() }
        return START_NOT_STICKY
    }

    // ---- Called on the main thread by the alarm and the UI ----

    private fun onAlarmRinging() {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            if (alarmRingAt == null) alarmRingAt = now
            if (ringingSince == null) ringingSince = now
        }
    }

    private fun onAlarmSilenced(dismissed: Boolean) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            ringingSince?.let { ringing.add(it..now) }
            ringingSince = null
            if (!dismissed) {
                snoozes++
                return
            }
            alarmDismissAt = now
        }
        // An alarm shortly after going to bed (e.g. a reminder) does not end the night.
        if (System.currentTimeMillis() - start >= MIN_NIGHT_FOR_ALARM_MS) requestStop(discard = false)
    }

    private fun requestStop(discard: Boolean) {
        this.discard = discard
        stopRequested = true
        if (thread == null) shutDown()
    }

    // ---- Recording thread ----

    @SuppressLint("MissingPermission")
    private fun record() {
        val settings = SleepRepository.settings.value
        val analyzer = FrameAnalyzer()
        val detector = BurstDetector()
        val live = LiveDetector(settings.saveClips)
        val bursts = ArrayList<Burst>(8192)
        val minutes = ArrayList<MinuteLevel>(600)
        val clips = mutableListOf<SleepClip>()
        val labels = ArrayList<LabelWindow>(4096)
        val light = ArrayList<Float?>(600)
        val classifier = YamnetClassifier.create(this)
        val window = FloatArray(YAMNET_WINDOW)
        var windowFill = 0
        var windowStart = 0L
        var windowActive = 0
        val pendingClips = mutableListOf<ClipRequest>()
        val ring = ShortArray(SAMPLE_RATE * RING_SECONDS)
        var totalSamples = 0L
        var frameCount = 0L
        var minutePower = 0.0
        var minuteMax = -100f
        var minuteFrames = 0
        var lastCheckpoint = System.currentTimeMillis()
        val smartWoken = mutableSetOf<Long>()

        val record = openAudioRecord()
        if (record == null) {
            main.post {
                SleepNotifications.showProblem(this, "Das Mikrofon konnte nicht geöffnet werden.")
                shutDown()
            }
            return
        }
        val buffer = ShortArray(FRAME_SIZE * 5)
        try {
            record.startRecording()
            while (!stopRequested) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) {
                    if (read < 0) {
                        Log.e(TAG, "AudioRecord.read failed: $read")
                        break
                    }
                    continue
                }
                for (i in 0 until read) ring[((totalSamples + i) % ring.size).toInt()] = buffer[i]
                totalSamples += read
                val isRinging = synchronized(lock) { ringingSince != null }

                for (offset in 0..read - FRAME_SIZE step FRAME_SIZE) {
                    val frame = analyzer.analyze(buffer, offset)
                    frameCount++
                    minutePower += Math.pow(10.0, frame.db / 10.0)
                    minuteMax = max(minuteMax, frame.db)
                    minuteFrames++
                    if (minuteFrames == FRAMES_PER_MINUTE) {
                        minutes.add(MinuteLevel((10 * log10(minutePower / minuteFrames)).toFloat(), minuteMax, detector.floorDb))
                        light.add(lux)
                        minutePower = 0.0
                        minuteMax = -100f
                        minuteFrames = 0
                    }
                    val burst = detector.add(frame)
                    if (detector.inBurst || burst != null) windowActive++
                    if (burst != null) {
                        bursts.add(burst)
                        if (!isRinging) live.onBurst(burst)?.let(pendingClips::add)
                    }
                }

                // Sound classifier on ~1-s windows, only when something could be heard.
                if (classifier != null) {
                    var i = 0
                    while (i < read) {
                        if (windowFill == 0) windowStart = (totalSamples - read + i) * 1000 / SAMPLE_RATE
                        val n = minOf(read - i, YAMNET_WINDOW - windowFill)
                        for (k in 0 until n) window[windowFill + k] = buffer[i + k] / 32768f
                        windowFill += n
                        i += n
                        if (windowFill == YAMNET_WINDOW) {
                            if (windowActive >= 3 && !isRinging) {
                                labelFromScores(classifier.classify(window))?.let { (label, score) ->
                                    val labelled = LabelWindow(windowStart, label, score)
                                    labels.add(labelled)
                                    live.onLabel(labelled)?.let(pendingClips::add)
                                }
                            }
                            windowFill = 0
                            windowActive = 0
                        }
                    }
                }

                // Cut samples once the audio after the event has arrived.
                val nowMs = totalSamples * 1000 / SAMPLE_RATE
                pendingClips.removeAll { req ->
                    if (req.toMs > nowMs) return@removeAll false
                    writeClip(req, ring, totalSamples, clips.size)?.let(clips::add)
                    true
                }

                if (frameCount % FRAMES_PER_10S < 5) {
                    val wall = System.currentTimeMillis()
                    if (!isRinging) checkSmartWake(live, nowMs, wall, smartWoken)
                    if (wall >= deadline) stopRequested = true
                    if (wall - lastCheckpoint >= CHECKPOINT_MS) {
                        lastCheckpoint = wall
                        SleepRepository.writeCheckpoint(snapshot(wall, bursts, minutes, clips, labels, light, classifier != null))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed", e)
        } finally {
            try {
                record.stop()
            } catch (_: IllegalStateException) {
            }
            record.release()
            classifier?.close()
        }

        detector.flush()?.let(bursts::add)
        val end = System.currentTimeMillis()
        if (discard) {
            clips.forEach { SleepRepository.clipFile(it.file).delete() }
        } else {
            val summary = SleepRepository.saveNight(
                snapshot(end, bursts, minutes, clips, labels, light, classifier != null).copy(interrupted = false),
            )
            if (summary != null) SleepNotifications.showReport(this, summary)
        }
        SleepRepository.clearCheckpoint()
        SleepScheduler.cleanUpClips(this)
        main.post { shutDown() }
    }

    @SuppressLint("MissingPermission")
    private fun openAudioRecord(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        for (source in listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            try {
                val rec = AudioRecord(
                    source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    max(minBuffer, SAMPLE_RATE * 4),
                )
                if (rec.state == AudioRecord.STATE_INITIALIZED) return rec
                rec.release()
            } catch (e: Exception) {
                Log.w(TAG, "Audio source $source not available", e)
            }
        }
        return null
    }

    /**
     * Rings an alarm with smart wake-up early when, inside its window, the sleeper moves
     * (light sleep) or uses the phone. It is re-scheduled to ring right away through the
     * normal alarm path, and the regular ring time is remembered so it is skipped.
     */
    private fun checkSmartWake(live: LiveDetector, nowMs: Long, wall: Long, done: MutableSet<Long>) {
        if (wall - start < 2 * 60 * 60 * 1000L) return
        val now = ZonedDateTime.now()
        for (alarm in AlarmRepository.alarms.value) {
            if (!alarm.enabled || alarm.smartWakeMinutes <= 0 || alarm.isSnoozed(wall)) continue
            val target = AlarmScheduler.nextRegularTrigger(alarm, now)
            val before = target - wall
            if (before < 60_000 || before > alarm.smartWakeMinutes * 60_000L || target in done) continue
            val screenOn = synchronized(lock) { screen.lastOrNull()?.on == true }
            val felt = synchronized(lock) { motion.count { it >= wall - start - 3 * 60_000L } }
            if (!screenOn && live.movementsSince(nowMs - 3 * 60_000L) + felt < 2) continue
            done.add(target)
            synchronized(lock) { smartWake = true }
            Log.i(TAG, "Smart wake-up for alarm ${alarm.id}, ${before / 60_000} min early")
            AlarmRepository.upsert(alarm.copy(smartWakeTarget = target))
            AlarmScheduler.scheduleAt(this, alarm.id, wall + 1_000)
        }
    }

    /** Writes the requested part of the ring buffer as an 8-kHz WAV file. */
    private fun writeClip(req: ClipRequest, ring: ShortArray, total: Long, index: Int): SleepClip? {
        val totalMs = total * 1000 / SAMPLE_RATE
        val oldestMs = max(0L, (total - ring.size) * 1000 / SAMPLE_RATE)
        val fromMs = max(req.fromMs, oldestMs)
        val toMs = minOf(req.toMs, totalMs)
        if (toMs - fromMs < 2_000) return null
        val from = fromMs * SAMPLE_RATE / 1000
        val count = ((toMs - fromMs) * SAMPLE_RATE / 1000).toInt() / 2
        val pcm = ShortArray(count) { i ->
            val a = ring[((from + 2 * i) % ring.size).toInt()]
            val b = ring[((from + 2 * i + 1) % ring.size).toInt()]
            ((a + b) / 2).toShort()
        }
        val name = "$start-$index.wav"
        return try {
            writeWav(SleepRepository.clipFile(name), pcm, SAMPLE_RATE / 2)
            SleepClip(req.type, start + fromMs, name, (toMs - fromMs).toInt())
        } catch (e: Exception) {
            Log.w(TAG, "Could not write clip", e)
            null
        }
    }

    private fun snapshot(
        end: Long,
        bursts: List<Burst>,
        minutes: List<MinuteLevel>,
        clips: List<SleepClip>,
        labels: List<LabelWindow>,
        light: List<Float?>,
        classifierUsed: Boolean,
    ): NightRecording =
        synchronized(lock) {
            NightRecording(
                start = start,
                end = end,
                bursts = bursts.toList(),
                minutes = minutes.toList(),
                screen = screen.toList(),
                alarmRingAt = alarmRingAt,
                alarmDismissAt = alarmDismissAt,
                snoozes = snoozes,
                smartWake = smartWake,
                ringing = ringing.toList() + listOfNotNull(ringingSince?.let { it..end }),
                clips = clips.toList(),
                interrupted = true,
                labels = labels.toList(),
                classifierUsed = classifierUsed,
                light = if (light.all { it == null }) emptyList() else light.toList(),
                motion = motion.toList(),
            )
        }

    private fun shutDown() {
        if (instance === this) instance = null
        _recordingSince.value = null
        getSystemService(SensorManager::class.java)?.unregisterListener(sensorListener)
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: IllegalArgumentException) {
        }
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        thread = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Killed without finishing: the thread stops; the checkpoint is recovered on the next start.
        stopRequested = true
        if (instance === this) instance = null
        _recordingSince.value = null
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SleepTracking"
        private const val RING_SECONDS = 32
        private const val FRAMES_PER_MINUTE = 60_000 / FRAME_MS
        private const val FRAMES_PER_10S = 10_000 / FRAME_MS
        private const val CHECKPOINT_MS = 10 * 60 * 1000L

        /** Dismissing an alarm ends the recording only once it has run this long. */
        private const val MIN_NIGHT_FOR_ALARM_MS = 2 * 60 * 60 * 1000L

        /** m/s² of acceleration beyond gravity that counts as a movement of the mattress. */
        private const val MOTION_THRESHOLD = 0.15f

        private var instance: SleepTrackingService? = null
        private val _recordingSince = MutableStateFlow<Long?>(null)

        /** Start of the running recording, or null. */
        val recordingSince: StateFlow<Long?> = _recordingSince.asStateFlow()

        /** Must be called while the app is in the foreground (Android rule for the microphone). */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SleepTrackingService::class.java))
        }

        /** Ends the recording; with [discard] the night is thrown away. */
        fun stop(discard: Boolean = false) {
            instance?.requestStop(discard)
        }

        fun alarmRinging() {
            instance?.onAlarmRinging()
        }

        fun alarmSnoozed() {
            instance?.onAlarmSilenced(dismissed = false)
        }

        /** Start of the recording that dismissing the ringing alarm now would end, or null. */
        fun nightEndingWithAlarm(): Long? =
            _recordingSince.value?.takeIf { System.currentTimeMillis() - it >= MIN_NIGHT_FOR_ALARM_MS }

        fun alarmDismissed() {
            instance?.onAlarmSilenced(dismissed = true)
        }
    }
}

private fun writeWav(file: File, pcm: ShortArray, sampleRate: Int) {
    val dataSize = pcm.size * 2
    val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray())
        putInt(36 + dataSize)
        put("WAVE".toByteArray())
        put("fmt ".toByteArray())
        putInt(16)
        putShort(1)
        putShort(1)
        putInt(sampleRate)
        putInt(sampleRate * 2)
        putShort(2)
        putShort(16)
        put("data".toByteArray())
        putInt(dataSize)
    }
    val data = java.nio.ByteBuffer.allocate(dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    pcm.forEach { data.putShort(it) }
    file.outputStream().use {
        it.write(header.array())
        it.write(data.array())
    }
}
