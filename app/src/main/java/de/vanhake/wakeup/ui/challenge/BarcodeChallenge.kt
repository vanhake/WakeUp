package de.vanhake.wakeup.ui.challenge

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.vanhake.wakeup.ui.components.BigButton
import de.vanhake.wakeup.ui.components.BigOutlinedButton
import de.vanhake.wakeup.ui.theme.Danger
import kotlinx.coroutines.delay

@Composable
fun rememberCameraPermission(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    return granted to { launcher.launch(Manifest.permission.CAMERA) }
}

/**
 * Scan screen used both for registering a code and for the wake-up challenge.
 * [onScanned] returns true if the code was accepted.
 */
@Composable
fun BarcodeScanPane(
    title: String,
    hint: String,
    onScanned: (String) -> Boolean,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    val (hasCamera, requestCamera) = rememberCameraPermission()
    var torch by remember { mutableStateOf(false) }
    var lastWrong by remember { mutableLongStateOf(0L) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (!hasCamera) requestCamera() }
    LaunchedEffect(lastWrong) {
        if (lastWrong != 0L) {
            delay(1500)
            lastWrong = 0L
        }
    }

    Column(
        modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .clip(RoundedCornerShape(28.dp))
                .border(
                    BorderStroke(3.dp, if (lastWrong != 0L) Danger else MaterialTheme.colorScheme.primary),
                    RoundedCornerShape(28.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (hasCamera) {
                BarcodeScannerView(
                    onBarcode = { code ->
                        if (!done) {
                            if (onScanned(code)) done = true else if (lastWrong == 0L) lastWrong = System.currentTimeMillis()
                        }
                    },
                    torch = torch,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    "Kamera-Zugriff wird benötigt",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Text(
            if (lastWrong != 0L) "Falscher Code" else " ",
            color = Danger,
            style = MaterialTheme.typography.titleMedium,
        )

        if (hasCamera) {
            BigOutlinedButton(
                text = if (torch) "Licht aus" else "Licht an",
                icon = if (torch) Icons.Filled.FlashlightOff else Icons.Filled.FlashlightOn,
                onClick = { torch = !torch },
            )
        } else {
            BigButton(text = "Kamera erlauben", icon = Icons.Filled.CameraAlt, onClick = requestCamera)
        }
        Spacer(Modifier.height(4.dp))
        footer()
    }
}

@Composable
fun BarcodeChallenge(expected: String?, onSolved: () -> Unit, onEmergency: () -> Unit) {
    BarcodeScanPane(
        title = "Scanne deinen Barcode",
        hint = "Geh zu dem Gegenstand, dessen Code du hinterlegt hast.",
        onScanned = { code ->
            val ok = expected == null || code == expected
            if (ok) onSolved()
            ok
        },
        footer = {
            TextButton(onClick = onEmergency) {
                Text("Code nicht greifbar? Notfall-Aufgaben lösen")
            }
        },
    )
}
