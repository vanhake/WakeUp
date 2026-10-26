package de.vanhake.wakeup.sleep

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import de.vanhake.wakeup.ui.components.BigOutlinedButton
import de.vanhake.wakeup.ui.theme.WakeUpTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shown briefly at bedtime: Android only lets an app start using the microphone while it is
 * in the foreground, so this screen starts the recording and closes itself again.
 */
class SleepStartActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        SleepNotifications.cancelStart(this)

        val allowed = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed && SleepTrackingService.recordingSince.value == null) {
            SleepTrackingService.start(this)
        }

        lifecycleScope.launch {
            delay(if (allowed) 6_000 else 10_000)
            finish()
        }

        setContent {
            WakeUpTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .safeDrawingPadding()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Filled.Bedtime, contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(72.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        if (allowed) "Schlafaufzeichnung läuft" else "Mikrofon nicht erlaubt",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (allowed) "Gute Nacht! Leg das Handy in Reichweite, am besten am Ladegerät." else
                            "Erlaube das Mikrofon in WakeUp unter „Schlaf“, damit die Nacht aufgezeichnet werden kann.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.weight(1f))
                    if (allowed) {
                        BigOutlinedButton(
                            text = "Heute nicht aufzeichnen",
                            icon = Icons.Filled.Close,
                            onClick = {
                                SleepTrackingService.stop(discard = true)
                                finish()
                            },
                        )
                    }
                }
            }
        }
    }
}
