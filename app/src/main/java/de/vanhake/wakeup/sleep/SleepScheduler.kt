package de.vanhake.wakeup.sleep

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.vanhake.wakeup.alarm.nextTrigger
import java.time.ZonedDateTime

/** Schedules the daily automatic start of the sleep recording and the deletion of old audio samples. */
object SleepScheduler {

    fun scheduleNextStart(context: Context) {
        SleepRepository.init(context)
        val settings = SleepRepository.settings.value
        val am = context.getSystemService(AlarmManager::class.java)
        val operation = intent(context, SleepReceiver.ACTION_START)
        if (settings.mode == RecordingMode.OFF) {
            am.cancel(operation)
            return
        }
        val at = nextTrigger(settings.startHour, settings.startMinute, emptySet(), ZonedDateTime.now())
            .toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    /** Deletes expired audio samples now and schedules the next deletion. */
    fun cleanUpClips(context: Context) {
        SleepRepository.init(context)
        val next = SleepRepository.deleteExpiredClips()
        val am = context.getSystemService(AlarmManager::class.java)
        val operation = intent(context, SleepReceiver.ACTION_CLEANUP)
        if (next == null) {
            am.cancel(operation)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC, next + 1_000, operation)
        }
    }

    private fun intent(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, SleepReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
