package com.financetracker.app.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.Money
import com.financetracker.app.data.MonthPeriod
import com.financetracker.app.ui.common.OptionPickerDialog
import com.financetracker.app.ui.common.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ManageViewModel,
    onBack: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenData: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showStartDayPicker by remember { mutableStateOf(false) }
    var editingRate by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "Manage") {
                    NavRow("Accounts", "${state.accounts.size} set up", Icons.Filled.AccountBalanceWallet, onOpenAccounts)
                    NavRow("Categories", "Spending and income", Icons.Filled.Sell, onOpenCategories)
                    NavRow(
                        "Recurring",
                        "${state.rules.count { it.isActive }} active",
                        Icons.Filled.Autorenew,
                        onOpenRecurring
                    )
                    NavRow(
                        "Data & security",
                        "Backup, CSV export, app lock",
                        Icons.Filled.Shield,
                        onOpenData
                    )
                }
            }

            item {
                SectionCard(title = "Money") {
                    SettingRow(
                        label = "Base currency",
                        value = "${state.baseCurrency} ${Money.symbol(state.baseCurrency)}",
                        supporting = "Reports, budgets and net worth are shown in this currency.",
                        onClick = { showCurrencyPicker = true }
                    )
                    SettingRow(
                        label = "Month starts on",
                        value = ordinal(state.monthStartDay),
                        supporting = "Set this to your pay day to line budgets up with your pay cycle.",
                        onClick = { showStartDayPicker = true }
                    )
                }
            }

            if (state.currenciesInUse.any { it != state.baseCurrency }) {
                item {
                    SectionCard(title = "Exchange rates") {
                        Text(
                            "Used for net worth and to pre-fill new entries. Rates already saved " +
                                "on past transactions never change.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        state.currenciesInUse.filter { it != state.baseCurrency }.forEach { code ->
                            val rate = state.rates.firstOrNull { it.code == code }?.rateToBase ?: 1.0
                            SettingRow(
                                label = "1 $code",
                                value = "${"%.4f".format(rate)} ${state.baseCurrency}",
                                onClick = { editingRate = code }
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "About") {
                    Text(
                        "Everything is stored on this device only. There is no account, no server " +
                            "and no network access. Take a backup from Data & security so it " +
                            "survives losing the phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    if (showCurrencyPicker) {
        OptionPickerDialog(
            title = "Base currency",
            options = Money.COMMON_CODES,
            selected = state.baseCurrency,
            label = { "$it  ${Money.symbol(it)}" },
            onSelect = { viewModel.setBaseCurrency(it); showCurrencyPicker = false },
            onDismiss = { showCurrencyPicker = false }
        )
    }

    if (showStartDayPicker) {
        val days = remember { (1..MonthPeriod.MAX_START_DAY).toList() }
        OptionPickerDialog(
            title = "Month starts on",
            options = days,
            selected = state.monthStartDay,
            label = { ordinal(it) },
            supporting = { if (it == 1) "Calendar months" else null },
            onSelect = { viewModel.setMonthStartDay(it); showStartDayPicker = false },
            onDismiss = { showStartDayPicker = false }
        )
    }

    editingRate?.let { code ->
        val current = state.rates.firstOrNull { it.code == code }?.rateToBase ?: 1.0
        RateDialog(
            code = code,
            baseCurrency = state.baseCurrency,
            initial = current,
            onConfirm = { viewModel.setRate(code, it); editingRate = null },
            onDismiss = { editingRate = null }
        )
    }
}

@Composable
private fun NavRow(label: String, supporting: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                supporting,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    supporting: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (supporting != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                supporting,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RateDialog(
    code: String,
    baseCurrency: String,
    initial: Double,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$code to $baseCurrency") },
        text = {
            Column {
                Text(
                    "How many $baseCurrency one $code is worth.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("1 $code =") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** "1st", "22nd" - reads better than a bare number next to "Month starts on". */
private fun ordinal(day: Int): String {
    val suffix = when {
        day % 100 in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}
