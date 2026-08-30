package com.financetracker.app.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.Money
import com.financetracker.app.data.account.Account
import com.financetracker.app.data.account.AccountType
import com.financetracker.app.data.account.AccountWithBalance
import com.financetracker.app.ui.common.CategoryDot
import com.financetracker.app.ui.common.ColorPickerRow
import com.financetracker.app.ui.common.ConfirmDialog
import com.financetracker.app.ui.common.OptionPickerDialog
import com.financetracker.app.ui.common.PALETTE
import com.financetracker.app.ui.common.SectionCard
import com.financetracker.app.ui.settings.ManageViewModel
import com.financetracker.app.ui.theme.Accent
import com.financetracker.app.ui.theme.Bg
import com.financetracker.app.ui.theme.Negative
import com.financetracker.app.ui.theme.Positive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(viewModel: ManageViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AccountWithBalance?>(null) }
    var blockedMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = Accent,
                contentColor = Bg
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add account")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.accounts.size, key = { state.accounts[it].id }) { index ->
                val account = state.accounts[index]
                AccountCard(
                    account = account,
                    onEdit = {
                        editing = Account(
                            id = account.id,
                            name = account.name,
                            type = account.type,
                            currencyCode = account.currencyCode,
                            openingBalanceMinor = 0,
                            colorArgb = account.colorArgb,
                            includeInNetWorth = account.includeInNetWorth,
                            isArchived = account.isArchived,
                            sortOrder = account.sortOrder
                        )
                    },
                    onToggleArchive = {
                        viewModel.setAccountArchived(account.id, !account.isArchived)
                    },
                    onDelete = { deleting = account }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // The card only carries a display copy of the account; the real row (with its true opening
    // balance) is fetched before the editor opens so saving cannot zero that balance out.
    var resolved by remember { mutableStateOf<Account?>(null) }
    LaunchedEffect(editing?.id) {
        resolved = editing?.let { viewModel.accountById(it.id) }
    }

    if (creating) {
        AccountEditorDialog(
            existing = null,
            baseCurrency = state.baseCurrency,
            onSave = { name, type, currency, opening, color, include ->
                viewModel.saveAccount(null, name, type, currency, opening, color, include)
                creating = false
            },
            onDismiss = { creating = false }
        )
    }

    resolved?.let { account ->
        AccountEditorDialog(
            existing = account,
            baseCurrency = state.baseCurrency,
            onSave = { name, type, currency, opening, color, include ->
                viewModel.saveAccount(account, name, type, currency, opening, color, include)
                editing = null
                resolved = null
            },
            onDismiss = { editing = null; resolved = null }
        )
    }

    deleting?.let { account ->
        ConfirmDialog(
            title = "Delete ${account.name}?",
            body = "Accounts with transactions can only be archived, which hides them without " +
                "touching your history.",
            onConfirm = {
                viewModel.deleteAccount(account.id) { count ->
                    blockedMessage = "${account.name} has $count transactions. Archive it instead."
                }
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }

    blockedMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { blockedMessage = null },
            title = { Text("Cannot delete") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { blockedMessage = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun AccountCard(
    account: AccountWithBalance,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    SectionCard(modifier = Modifier.clickable { onEdit() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(account.colorArgb, size = 12)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    account.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(account.type.label)
                        append(" · ")
                        append(account.currencyCode)
                        if (account.isArchived) append(" · archived")
                        if (!account.includeInNetWorth) append(" · excluded from net worth")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                Money.format(account.balanceMinor, account.currencyCode),
                style = MaterialTheme.typography.titleSmall,
                color = if (account.balanceMinor < 0) Negative else Positive
            )
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onToggleArchive) {
                Text(if (account.isArchived) "Unarchive" else "Archive")
            }
            TextButton(onClick = onDelete) {
                Text("Delete", color = Negative)
            }
        }
    }
}

@Composable
private fun AccountEditorDialog(
    existing: Account?,
    baseCurrency: String,
    onSave: (String, AccountType, String, String, Int, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember { mutableStateOf(existing?.type ?: AccountType.BANK) }
    var currency by remember { mutableStateOf(existing?.currencyCode ?: baseCurrency) }
    var opening by remember {
        mutableStateOf(
            existing?.let { Money.editString(it.openingBalanceMinor, it.currencyCode) } ?: ""
        )
    }
    var color by remember { mutableStateOf(existing?.colorArgb ?: PALETTE.first()) }
    var includeInNetWorth by remember { mutableStateOf(existing?.includeInNetWorth ?: true) }
    var showTypePicker by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New account" else "Edit account") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                PickerRow("Type", type.label) { showTypePicker = true }
                PickerRow("Currency", currency) { showCurrencyPicker = true }

                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = opening,
                    onValueChange = { opening = it },
                    label = { Text("Opening balance ($currency)") },
                    supportingText = {
                        Text(
                            if (type == AccountType.CARD)
                                "Money owed goes in as a negative number."
                            else
                                "What the account held before you started tracking."
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                Text("Colour", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                ColorPickerRow(selected = color, onSelect = { color = it })

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = includeInNetWorth,
                        onCheckedChange = { includeInNetWorth = it }
                    )
                    Text("Count towards net worth", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, type, currency, opening, color, includeInNetWorth) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showTypePicker) {
        OptionPickerDialog(
            title = "Account type",
            options = AccountType.entries,
            selected = type,
            label = { it.label },
            onSelect = { type = it; showTypePicker = false },
            onDismiss = { showTypePicker = false }
        )
    }

    if (showCurrencyPicker) {
        OptionPickerDialog(
            title = "Currency",
            options = Money.COMMON_CODES,
            selected = currency,
            label = { "$it  ${Money.symbol(it)}" },
            onSelect = { currency = it; showCurrencyPicker = false },
            onDismiss = { showCurrencyPicker = false }
        )
    }
}

@Composable
internal fun PickerRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
