package de.vanhake.wakeup.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        if (id >= 0) AlarmService.fire(context, id)
    }

    companion object {
        const val ACTION_FIRE = "de.vanhake.wakeup.action.FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
