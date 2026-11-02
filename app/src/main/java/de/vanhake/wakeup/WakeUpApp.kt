package de.vanhake.wakeup

import android.app.Application
import de.vanhake.wakeup.alarm.Notifications
import de.vanhake.wakeup.data.AlarmRepository
import de.vanhake.wakeup.sleep.SleepNotifications
import de.vanhake.wakeup.sleep.SleepRepository
import de.vanhake.wakeup.sleep.SleepScheduler

class WakeUpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmRepository.init(this)
        Notifications.createChannels(this)
        SleepNotifications.createChannels(this)
        // Also saves a night whose recording was killed (once the phone is unlocked).
        SleepRepository.init(this)
        SleepScheduler.scheduleNextStart(this)
        Thread(SleepRepository::refreshOutdatedSummaries, "SleepSummaries").start()
    }
}
