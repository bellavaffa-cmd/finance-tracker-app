package com.financetracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.CategoryKind
import com.financetracker.app.data.Txn
import com.financetracker.app.data.TxnType
import com.financetracker.app.domain.Money
import com.financetracker.app.ui.DropdownField
import com.financetracker.app.ui.LedgerState
import com.financetracker.app.ui.dayFormat
import java.time.LocalDate

private const val DAY_MS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    state: LedgerState,
    txnId: Long?,
    onSave: (Txn) -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val existing = remember(txnId) { txnId?.let { id -> state.txns.firstOrNull { it.id == id } } }
    if (txnId != null && existing == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val defaultAccount = state.activeAccounts.firstOrNull()?.id

    var type by rememberSaveable { mutableStateOf(existing?.type ?: TxnType.EXPENSE) }
    var amountText by rememberSaveable { mutableStateOf(existing?.let { Money.plain(it.amount, state.currency) } ?: "") }
    var accountId by rememberSaveable { mutableStateOf(existing?.accountId ?: defaultAccount) }
    var toAccountId by rememberSaveable { mutableStateOf(existing?.toAccountId) }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId) }
    var epochDay by rememberSaveable { mutableStateOf(existing?.date ?: LocalDate.now().toEpochDay()) }
    var payee by rememberSaveable { mutableStateOf(existing?.payee ?: "") }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Archived accounts stay selectable only for the entry that already uses them.
    val accounts = state.accounts.filter { !it.archived || it.id == existing?.accountId || it.id == existing?.toAccountId }
    val kind = if (type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categories = state.categories.filter { it.kind == kind }

    fun recallCategory(name: String) {
        if (categoryId != null || existing != null || type == TxnType.TRANSFER || name.isBlank()) return
        state.txns.firstOrNull { it.type == type && it.categoryId != null && it.payee.equals(name.trim(), ignoreCase = true) }
            ?.let { categoryId = it.categoryId }
    }

    fun save() {
        val amount = Money.parse(amountText, state.currency)
        val from = accountId
        error = when {
            amount == null -> "Enter an amount, e.g. 12.50."
            amount <= 0 -> "The amount must be more than zero. Choose Expense or Income for its direction."
            from == null -> "Choose an account."
            type == TxnType.TRANSFER && toAccountId == null -> "Choose the account the money goes to."
            type == TxnType.TRANSFER && toAccountId == from -> "A transfer needs two different accounts."
            else -> null
        }
        if (error != null) return
        onSave(
            Txn(
                id = existing?.id ?: 0,
                type = type,
                amount = amount!!,
                accountId = from!!,
                toAccountId = if (type == TxnType.TRANSFER) toAccountId else null,
                categoryId = if (type == TxnType.TRANSFER) null else categoryId?.takeIf { id -> categories.any { it.id == id } },
                date = epochDay,
                payee = payee.trim(),
                note = note.trim(),
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New transaction" else "Edit transaction") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TxnType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = { if (type != t) { type = t; categoryId = null } },
                        shape = SegmentedButtonDefaults.itemShape(i, TxnType.entries.size),
                    ) { Text(t.label) }
                }
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it; error = null },
                label = { Text("Amount (${state.currency})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = if (type == TxnType.TRANSFER) "From account" else "Account",
                options = accounts, selected = accounts.firstOrNull { it.id == accountId },
                display = { it.name }, onSelect = { accountId = it.id },
            )
            if (type == TxnType.TRANSFER) {
                DropdownField(
                    label = "To account",
                    options = accounts.filter { it.id != accountId },
                    selected = accounts.firstOrNull { it.id == toAccountId },
                    display = { it.name }, onSelect = { toAccountId = it.id }, placeholder = "Choose",
                )
            } else {
                DropdownField(
                    label = "Category",
                    options = listOf(null) + categories,
                    selected = categories.firstOrNull { it.id == categoryId },
                    display = { it?.name ?: "Uncategorised" },
                    onSelect = { categoryId = it?.id },
                    placeholder = "Uncategorised",
                )
            }
            OutlinedTextField(
                value = payee,
                // Typing a payee you've filed before recalls the category you used last time.
                onValueChange = { payee = it; recallCategory(it) },
                label = { Text(if (type == TxnType.INCOME) "From" else if (type == TxnType.TRANSFER) "Description" else "Payee") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Date: " + LocalDate.ofEpochDay(epochDay).format(dayFormat))
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }

    if (pickingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = epochDay * DAY_MS)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker reports UTC midnight, which maps exactly onto an epoch day.
                    picker.selectedDateMillis?.let { epochDay = Math.floorDiv(it, DAY_MS) }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(picker) }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this transaction?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(existing.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
