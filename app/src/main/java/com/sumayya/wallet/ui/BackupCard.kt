package com.sumayya.wallet.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** Save everything to a file, restore it later, or export the entries as a CSV. */
@Composable
fun BackupCard(vm: WalletViewModel) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    val stamp = remember { LocalDate.now().toString() }

    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) vm.exportBackup(context.contentResolver, uri) { status = it }
    }
    val saveCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) vm.exportCsv(context.contentResolver, uri) { status = it }
    }
    val pickBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingRestore = uri
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Backup & restore", style = MaterialTheme.typography.titleMedium)
            Text(
                "Your data lives only on this phone. Save a backup file now and then, and keep a copy " +
                    "somewhere safe (Google Drive, WhatsApp to yourself, or your computer).",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { saveBackup.launch("wallet-backup-$stamp.json") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save backup file") }
            OutlinedButton(
                onClick = { pickBackup.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restore from backup file") }
            OutlinedButton(
                onClick = { saveCsv.launch("wallet-entries-$stamp.csv") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Export entries as CSV (for Excel / Sheets)") }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Replace all current data?") },
            text = {
                Text(
                    "Restoring replaces everything in the app with the contents of the backup file. " +
                        "If you want to keep what you have now, save a backup first."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.restoreBackup(context.contentResolver, uri) { status = it }
                    pendingRestore = null
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } }
        )
    }
}
