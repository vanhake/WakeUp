package de.vanhake.wakeup

import android.app.Application
import de.vanhake.wakeup.alarm.Notifications
import de.vanhake.wakeup.data.AlarmRepository

class WakeUpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmRepository.init(this)
        Notifications.createChannels(this)
    }
}
