package de.vanhake.wakeup.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import de.vanhake.wakeup.MainActivity
import de.vanhake.wakeup.R
import de.vanhake.wakeup.data.Alarm
import de.vanhake.wakeup.ui.ringing.AlarmActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Notifications {
    const val CHANNEL_RINGING = "ringing"
    const val CHANNEL_SNOOZE = "snooze"
    const val ID_RINGING = 1
    private const val ID_SNOOZE_BASE = 1000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val ringing = NotificationChannel(
            CHANNEL_RINGING,
            context.getString(R.string.channel_ringing),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            // The service plays sound and vibration itself.
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val snooze = NotificationChannel(
            CHANNEL_SNOOZE,
            context.getString(R.string.channel_snooze),
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannels(listOf(ringing, snooze))
    }

    fun ringing(context: Context, alarm: Alarm?): Notification {
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Deliberately no "dismiss" or "snooze" actions: the alarm can only be stopped in the app.
        return NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(alarm?.label?.ifBlank { null } ?: "Wecker")
            .setContentText("Tippen, um die Aufgabe zu lösen")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(intent)
            .setFullScreenIntent(intent, true)
            .build()
    }

    fun showSnoozed(context: Context, alarm: Alarm, until: Long) {
        val time = DateTimeFormatter.ofPattern("HH:mm")
            .format(Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()))
        val left = alarm.snoozesLeft
        val notification = NotificationCompat.Builder(context, CHANNEL_SNOOZE)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Schlummert bis $time")
            .setContentText(
                (alarm.label.ifBlank { "Wecker" }) + " · " +
                    if (left == 0) "kein Schlummern mehr übrig" else "noch $left× Schlummern",
            )
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        try {
            context.getSystemService(NotificationManager::class.java)
                .notify(ID_SNOOZE_BASE + alarm.id, notification)
        } catch (_: SecurityException) {
            // Notification permission not granted; the alarm itself still works.
        }
    }

    fun cancelSnoozed(context: Context, alarmId: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_SNOOZE_BASE + alarmId)
    }
}
