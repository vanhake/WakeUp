package de.vanhake.wakeup.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-registers all alarms after reboot, app update, clock/timezone or permission changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)
    }
}
