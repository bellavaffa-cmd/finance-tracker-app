package com.financetracker.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.financetracker.app.domain.Money
import com.financetracker.app.ui.FinanceViewModel
import com.financetracker.app.ui.LedgerState
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: LedgerState, vm: FinanceViewModel, onAccounts: () -> Unit, onCategories: () -> Unit) {
    var editingCurrency by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val importResult by vm.importResult.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportCsv)
    }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importCsv)
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::backup)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::restore)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Header("Ledger")
            Item("Accounts", "${state.activeAccounts.size} active", onAccounts)
            Item("Categories", "${state.categories.size} categories", onCategories)
            Item("Currency", state.currency) { editingCurrency = true }

            Header("Import & export")
            Item("Export CSV", "The whole ledger, for a spreadsheet") { exportCsv.launch("ledger-$today.csv") }
            Item("Import CSV", "From this app's export or a bank statement") {
                importCsv.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
            }

            Header("Backup")
            Item("Back up", "Everything, as one JSON file") { backup.launch("finance-backup-$today.json") }
            Item("Restore", "Replace this ledger with a backup") { confirmRestore = true }

            Header("About")
            Text(
                "Finance Tracker 2. Your data stays on this device. The app has no internet permission " +
                    "and no account; back up to a file you keep somewhere safe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (editingCurrency) {
        var code by remember { mutableStateOf(state.currency) }
        val valid = Money.isValidCurrency(code.trim())
        AlertDialog(
            onDismissRequest = { editingCurrency = false },
            title = { Text("Currency") },
            text = {
                Column {
                    OutlinedTextField(
                        code, { code = it.uppercase().take(3) },
                        label = { Text("ISO code, e.g. EUR, USD, GBP") }, singleLine = true, isError = !valid,
                    )
                    if (state.txns.isNotEmpty() && Money.digits(code.trim()) != Money.digits(state.currency) && valid) {
                        Text(
                            "${code.trim()} uses a different number of decimal places from ${state.currency}, " +
                                "so existing amounts would be misread. Amounts are never converted.",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text("Changing this relabels amounts; it doesn't convert them.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                val sameDigits = state.txns.isEmpty() || Money.digits(code.trim()) == Money.digits(state.currency)
                TextButton(enabled = valid && sameDigits, onClick = { vm.setCurrency(code.trim()); editingCurrency = false }) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { editingCurrency = false }) { Text("Cancel") } },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore a backup?") },
            text = { Text("Everything in this ledger will be replaced by the backup. Consider backing up first.") },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; restore.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) {
                    Text("Choose file")
                }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }

    importResult?.let { r ->
        AlertDialog(
            onDismissRequest = { vm.importResult.value = null },
            title = { Text("Import finished") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Imported ${r.imported} transaction(s).")
                    if (r.duplicates > 0) Text("Skipped ${r.duplicates} already in the ledger.")
                    r.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (r.errors.isNotEmpty()) {
                        Text("${r.errors.size} row(s) couldn't be read:", color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp))
                        r.errors.take(30).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (r.errors.size > 30) Text("…and ${r.errors.size - 30} more.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.importResult.value = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun Item(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider()
}
