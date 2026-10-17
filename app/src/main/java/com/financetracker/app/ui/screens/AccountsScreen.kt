package com.financetracker.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.Account
import com.financetracker.app.data.AccountType
import com.financetracker.app.domain.Money
import com.financetracker.app.ui.DropdownField
import com.financetracker.app.ui.LedgerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(state: LedgerState, onSave: (Account) -> Unit, onDelete: (Long) -> Unit, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Account(name = "", type = AccountType.BANK) }) {
                Icon(Icons.Default.Add, "Add account")
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(state.accounts, key = { it.id }) { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = a }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name)
                        Text(a.type.label + if (a.archived) " · archived" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Money.format(state.balances[a.id] ?: 0, state.currency))
                }
                HorizontalDivider()
            }
        }
    }

    editing?.let { account ->
        val isNew = account.id == 0L
        var name by remember(account) { mutableStateOf(account.name) }
        var type by remember(account) { mutableStateOf(account.type) }
        var opening by remember(account) { mutableStateOf(if (isNew) "" else Money.plain(account.openingBalance, state.currency)) }
        var problem by remember(account) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (isNew) "New account" else "Edit account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it; problem = null }, label = { Text("Name") }, singleLine = true)
                    DropdownField("Type", AccountType.entries, type, { it.label }, { type = it })
                    OutlinedTextField(
                        opening, { opening = it; problem = null },
                        label = { Text("Opening balance") }, singleLine = true,
                        // Text keyboard on purpose: numeric keypads often have no minus key.
                        supportingText = { Text("The balance before your first entry here. Use - for a card balance owed.") },
                    )
                    problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!isNew) {
                        Row {
                            TextButton(onClick = { onSave(account.copy(archived = !account.archived)); editing = null }) {
                                Text(if (account.archived) "Unarchive" else "Archive")
                            }
                            TextButton(onClick = { onDelete(account.id); editing = null }) { Text("Delete") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val balance = if (opening.isBlank()) 0L else Money.parse(opening, state.currency)
                    val clash = state.accounts.any { it.id != account.id && it.name.equals(name.trim(), ignoreCase = true) }
                    problem = when {
                        name.isBlank() -> "Give the account a name."
                        clash -> "Another account already has that name."
                        balance == null -> "The opening balance isn't a valid amount."
                        else -> null
                    }
                    if (problem == null) {
                        onSave(account.copy(name = name.trim(), type = type, openingBalance = balance!!))
                        editing = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}
