package de.vanhake.wakeup.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.vanhake.wakeup.sleep.SleepScheduler

/** Re-registers all alarms (and the sleep recording start) after reboot, app update, clock/timezone or permission changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)
        SleepScheduler.scheduleNextStart(context)
        SleepScheduler.cleanUpClips(context)
    }
}
