package de.vanhake.wakeup.ui.ringing

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import de.vanhake.wakeup.alarm.AlarmService
import de.vanhake.wakeup.data.AlarmRepository
import de.vanhake.wakeup.ui.theme.WakeUpTheme
import kotlinx.coroutines.launch

/** Full-screen ringing screen, shown above the lock screen. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // The back button must not get you out of a ringing alarm.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        AlarmRepository.init(this)

        lifecycleScope.launch {
            AlarmService.ringingAlarmId.collect { if (it == null) finish() }
        }

        setContent {
            WakeUpTheme {
                RingingRoute()
            }
        }
    }
}
