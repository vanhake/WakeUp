package de.vanhake.wakeup.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.vanhake.wakeup.data.AlarmRepository
import de.vanhake.wakeup.data.BarcodeRepository
import de.vanhake.wakeup.data.ChallengeType
import de.vanhake.wakeup.data.SavedBarcode
import de.vanhake.wakeup.ui.challenge.BarcodeScanPane
import de.vanhake.wakeup.ui.components.BigButton
import de.vanhake.wakeup.ui.components.BigOutlinedButton
import de.vanhake.wakeup.ui.components.SectionTitle
import de.vanhake.wakeup.ui.theme.Danger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val barcodes by BarcodeRepository.barcodes.collectAsState()
    val alarms by AlarmRepository.alarms.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SavedBarcode?>(null) }
    var deleting by remember { mutableStateOf<SavedBarcode?>(null) }

    if (adding) {
        AddBarcodeFlow(onDone = { adding = false })
        return
    }

    renaming?.let { barcode ->
        NameDialog(
            title = "Barcode umbenennen",
            initial = barcode.name,
            onConfirm = {
                BarcodeRepository.rename(barcode.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { barcode ->
        val users = alarms.filter { it.challenge == ChallengeType.BARCODE && it.barcodeId == barcode.id }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("„${barcode.name}“ löschen?") },
            text = {
                if (users.isNotEmpty()) {
                    Text(
                        "Wird von ${users.size} Wecker(n) verwendet (" +
                            users.joinToString { "%02d:%02d".format(it.hour, it.minute) } +
                            "). Diese Wecker werden auf Rechnen umgestellt.",
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    users.forEach { AlarmRepository.upsert(it.copy(challenge = ChallengeType.MATH, barcodeId = null)) }
                    BarcodeRepository.delete(barcode.id)
                    deleting = null
                }) { Text("Löschen", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Schließen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SectionTitle("Barcodes")
            Text(
                "Gespeicherte Codes kannst du bei jedem Wecker auswählen. Am besten Gegenstände weit weg " +
                    "vom Bett, z. B. Zahnpasta im Bad oder Kaffee in der Küche.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (barcodes.isEmpty()) {
                Text(
                    "Noch keine Barcodes gespeichert.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                barcodes.forEach { barcode ->
                    BarcodeCard(
                        barcode = barcode,
                        usedBy = alarms.count { it.challenge == ChallengeType.BARCODE && it.barcodeId == barcode.id },
                        onRename = { renaming = barcode },
                        onDelete = { deleting = barcode },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            BigButton(text = "Barcode hinzufügen", icon = Icons.Filled.QrCodeScanner, onClick = { adding = true })
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BarcodeCard(barcode: SavedBarcode, usedBy: Int, onRename: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(barcode.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    barcode.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (usedBy) {
                        0 -> "Von keinem Wecker verwendet"
                        1 -> "Von 1 Wecker verwendet"
                        else -> "Von $usedBy Weckern verwendet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRename, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Umbenennen")
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Löschen", tint = Danger)
            }
        }
    }
}

/** Scans a new code, asks for a name and stores it in the library. [onDone] gets null on cancel. */
@Composable
fun AddBarcodeFlow(onDone: (SavedBarcode?) -> Unit) {
    val context = LocalContext.current
    var scanned by remember { mutableStateOf<String?>(null) }
    BackHandler { onDone(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        BarcodeScanPane(
            title = "Barcode hinzufügen",
            hint = "Scanne einen Barcode oder QR-Code, der weit weg vom Bett ist.",
            onScanned = { code ->
                val existing = BarcodeRepository.findByValue(code)
                if (existing != null) {
                    Toast.makeText(context, "Schon gespeichert als „${existing.name}“", Toast.LENGTH_SHORT).show()
                    onDone(existing)
                } else {
                    scanned = code
                }
                true
            },
            footer = {
                BigOutlinedButton(text = "Abbrechen", icon = Icons.Filled.Close, onClick = { onDone(null) })
            },
        )
    }

    scanned?.let { code ->
        NameDialog(
            title = "Wie heißt der Gegenstand?",
            initial = "",
            placeholder = "z. B. Zahnpasta im Bad",
            onConfirm = { onDone(BarcodeRepository.add(it, code)) },
            onDismiss = { onDone(null) },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String = "",
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                placeholder = { Text(placeholder) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(name.trim()) }),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
