package de.vanhake.wakeup.ui.ringing

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import de.vanhake.wakeup.MainActivity
import de.vanhake.wakeup.alarm.AlarmService
import de.vanhake.wakeup.data.AlarmRepository
import de.vanhake.wakeup.ui.theme.WakeUpTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Full-screen ringing screen, shown above the lock screen. */
class AlarmActivity : ComponentActivity() {

    /** Set once the challenge is solved; the app then opens instead of just closing. */
    private var solved = false

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
            AlarmService.ringingAlarmId.collect { if (it == null && !solved) finish() }
        }

        setContent {
            WakeUpTheme {
                RingingRoute(onSolved = ::openApp)
            }
        }
    }

    /** Shows the night's sleep report or the alarm list, after the phone has been unlocked. */
    private fun openApp(night: Long?) {
        solved = true
        // Wait until the alarm is off, so the app doesn't show the ringing screen once more.
        lifecycleScope.launch {
            AlarmService.ringingAlarmId.first { it == null }
            unlockAndOpenApp(night)
        }
    }

    private fun unlockAndOpenApp(night: Long?) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            startMain(night)
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = startMain(night)
            override fun onDismissCancelled() = finish()
            override fun onDismissError() = finish()
        })
    }

    private fun startMain(night: Long?) {
        try {
            startActivity(MainActivity.afterAlarmIntent(this, night))
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the app after the alarm", e)
        }
        finish()
    }

    private companion object {
        const val TAG = "AlarmActivity"
    }
}
