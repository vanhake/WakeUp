package de.vanhake.wakeup.sleep

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import de.vanhake.wakeup.MainActivity
import de.vanhake.wakeup.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object SleepNotifications {
    private const val CHANNEL_RECORDING = "sleep_recording"
    private const val CHANNEL_START = "sleep_start"
    private const val CHANNEL_REPORT = "sleep_report"
    const val ID_RECORDING = 2
    const val ID_START = 3
    private const val ID_REPORT = 4

    fun createChannels(context: Context) {
        val recording = NotificationChannel(CHANNEL_RECORDING, "Schlafaufzeichnung", NotificationManager.IMPORTANCE_LOW)
        val start = NotificationChannel(CHANNEL_START, "Start der Schlafaufzeichnung", NotificationManager.IMPORTANCE_HIGH)
            .apply {
                setSound(null, null)
                enableVibration(false)
            }
        val report = NotificationChannel(CHANNEL_REPORT, "Schlafbericht", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { setSound(null, null) }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(recording, start, report))
    }

    fun recording(context: Context, since: Long): Notification {
        val stop = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, SleepReceiver::class.java).setAction(SleepReceiver.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Schlafaufzeichnung läuft seit ${formatTime(since)}")
            .setContentText("Handy am besten ans Ladegerät, Mikrofon Richtung Kopf.")
            .setOngoing(true)
            .setContentIntent(openApp(context, 0, null))
            .addAction(0, "Beenden", stop)
            .build()
    }

    /**
     * Opens the start screen at bedtime. Starting the microphone needs the app in the
     * foreground; the full-screen intent brings it there even when the phone is locked.
     */
    fun showStart(context: Context) {
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, SleepStartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notify(
            context, ID_START,
            NotificationCompat.Builder(context, CHANNEL_START)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle("Schlafaufzeichnung starten")
                .setContentText("Tippen, um die Aufzeichnung für heute Nacht zu starten.")
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(4 * 60 * 60 * 1000L)
                .setContentIntent(intent)
                .setFullScreenIntent(intent, true)
                .build(),
        )
    }

    fun cancelStart(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(ID_START)

    fun showProblem(context: Context, text: String) = notify(
        context, ID_START,
        NotificationCompat.Builder(context, CHANNEL_REPORT)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Schlafaufzeichnung nicht gestartet")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, 1, null))
            .build(),
    )

    fun showReport(context: Context, summary: NightSummary) {
        val h = summary.sleepMinutes / 60
        val m = summary.sleepMinutes % 60
        val text = buildString {
            append("$h Std. $m Min. Schlaf · Schlafwert ${summary.score}")
            if (summary.snoreMinutes > 0) append(" · ${summary.snoreMinutes} Min. Schnarchen")
        }
        notify(
            context, ID_REPORT,
            NotificationCompat.Builder(context, CHANNEL_REPORT)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle("Dein Schlafbericht ist da")
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(openApp(context, 2, summary.start))
                .build(),
        )
    }

    private fun openApp(context: Context, requestCode: Int, report: Long?): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java).apply {
                if (report != null) putExtra(MainActivity.EXTRA_SLEEP_REPORT, report)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notify(context: Context, id: Int, notification: Notification) {
        try {
            context.getSystemService(NotificationManager::class.java).notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission not granted.
        }
    }

    private fun formatTime(millis: Long): String =
        DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}
