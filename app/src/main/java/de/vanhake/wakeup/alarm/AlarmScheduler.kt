package de.vanhake.wakeup.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.vanhake.wakeup.MainActivity
import de.vanhake.wakeup.data.Alarm
import de.vanhake.wakeup.data.AlarmRepository
import java.time.ZonedDateTime

object AlarmScheduler {

    /** Schedules the next ring of [alarm] (pending snooze first), or cancels it if disabled. */
    fun schedule(context: Context, alarm: Alarm) {
        if (!alarm.enabled) {
            cancel(context, alarm.id)
            return
        }
        val snoozedUntil = alarm.snoozedUntil
        val time = if (snoozedUntil != null && snoozedUntil > System.currentTimeMillis()) {
            snoozedUntil
        } else {
            nextRegularTrigger(alarm)
        }
        scheduleAt(context, alarm.id, time)
    }

    fun nextRegularTrigger(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()): Long =
        nextTrigger(alarm.hour, alarm.minute, alarm.days, now).toInstant().toEpochMilli()

    fun scheduleAt(context: Context, alarmId: Int, triggerAtMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val operation = fireIntent(context, alarmId)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            val showIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), operation)
        } else {
            // Without the exact-alarm permission this is the best we can do.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    fun cancel(context: Context, alarmId: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(fireIntent(context, alarmId))
    }

    fun rescheduleAll(context: Context) {
        AlarmRepository.init(context)
        AlarmRepository.alarms.value.forEach { schedule(context, it) }
    }

    private fun fireIntent(context: Context, alarmId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alarmId,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_FIRE)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
