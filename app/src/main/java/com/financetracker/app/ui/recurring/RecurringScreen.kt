package com.financetracker.app.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.Money
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.recurring.Frequency
import com.financetracker.app.data.recurring.RecurringRepository
import com.financetracker.app.data.recurring.RecurringRuleDetail
import com.financetracker.app.data.txn.TxnType
import com.financetracker.app.ui.accounts.PickerRow
import com.financetracker.app.ui.common.CategoryDot
import com.financetracker.app.ui.common.ConfirmDialog
import com.financetracker.app.ui.common.EmptyState
import com.financetracker.app.ui.common.OptionPickerDialog
import com.financetracker.app.ui.common.SectionCard
import com.financetracker.app.ui.common.StatTile
import com.financetracker.app.ui.common.relativeDueLabel
import com.financetracker.app.ui.common.shortDate
import com.financetracker.app.ui.common.withTimeFrom
import com.financetracker.app.ui.settings.ManageViewModel
import com.financetracker.app.ui.theme.Accent
import com.financetracker.app.ui.theme.Bg
import com.financetracker.app.ui.theme.Negative
import com.financetracker.app.ui.theme.Positive
import com.financetracker.app.ui.theme.Surface2
import com.financetracker.app.ui.theme.Warn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(viewModel: ManageViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringRuleDetail?>(null) }
    var deleting by remember { mutableStateOf<RecurringRuleDetail?>(null) }

    val allCategories = remember(state.expenseGroups, state.incomeGroups) {
        (state.expenseGroups + state.incomeGroups).flatMap { listOf(it.parent) + it.children }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring") },
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
                Icon(Icons.Filled.Add, contentDescription = "Add recurring rule")
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
            item {
                StatTile(
                    label = "Committed each month",
                    value = Money.format(state.monthlyCommitmentMinor, state.baseCurrency),
                    caption = "Active recurring expenses, normalised to a month",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.rules.isEmpty()) {
                item {
                    EmptyState(
                        "No recurring rules",
                        "Add rent, salary or a subscription and it posts itself on schedule."
                    )
                }
            }

            for (rule in state.rules) {
                item(key = rule.id) {
                    RuleCard(
                        rule = rule,
                        onEdit = { editing = rule },
                        onToggle = { viewModel.setRuleActive(rule, !rule.isActive) },
                        onDelete = { deleting = rule },
                        onConfirmNow = { viewModel.confirmRule(rule.id) },
                        onSkip = { viewModel.skipRule(rule.id) }
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (creating || editing != null) {
        RuleEditorDialog(
            existing = editing,
            accounts = state.accounts.filter { !it.isArchived },
            categories = allCategories,
            onSave = { name, type, accountId, toAccountId, categoryId, amount, currency,
                       payee, note, frequency, interval, firstDue, endDate, autoPost ->
                viewModel.saveRule(
                    existingId = editing?.id,
                    name = name,
                    type = type,
                    accountId = accountId,
                    toAccountId = toAccountId,
                    categoryId = categoryId,
                    amountInput = amount,
                    currencyCode = currency,
                    payee = payee,
                    note = note,
                    frequency = frequency,
                    interval = interval,
                    firstDueMillis = firstDue,
                    endDateMillis = endDate,
                    autoPost = autoPost
                )
                creating = false
                editing = null
            },
            onDismiss = { creating = false; editing = null }
        )
    }

    deleting?.let { rule ->
        ConfirmDialog(
            title = "Delete ${rule.name}?",
            body = "Transactions it already created stay in your history.",
            onConfirm = { viewModel.deleteRule(rule.id); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun RuleCard(
    rule: RecurringRuleDetail,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onConfirmNow: () -> Unit,
    onSkip: () -> Unit
) {
    val isDue = rule.isActive && rule.nextDueMillis <= System.currentTimeMillis()

    SectionCard(modifier = Modifier.clickable { onEdit() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(rule.colorArgb, size = 11)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rule.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    scheduleLabel(rule),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.format(rule.amountMinor, rule.accountCurrency),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (rule.type == TxnType.INCOME) Positive else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${Money.format(
                        RecurringRepository.monthlyEquivalentMinor(rule.amountMinor, rule.frequency, rule.interval),
                        rule.accountCurrency
                    )}/mo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Surface2)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    if (rule.isActive) "Next ${relativeDueLabel(rule.nextDueMillis)}" else "Paused",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDue) Warn else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            if (!rule.autoPost) {
                Text(
                    "asks first",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            Switch(checked = rule.isActive, onCheckedChange = { onToggle() })
        }

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (isDue && !rule.autoPost) {
                TextButton(onClick = onSkip) { Text("Skip") }
                TextButton(onClick = onConfirmNow) { Text("Add now") }
            }
            TextButton(onClick = onDelete) { Text("Delete", color = Negative) }
        }
    }
}

private fun scheduleLabel(rule: RecurringRuleDetail): String {
    val every = if (rule.interval == 1) rule.frequency.label
    else when (rule.frequency) {
        Frequency.DAILY -> "Every ${rule.interval} days"
        Frequency.WEEKLY -> "Every ${rule.interval} weeks"
        Frequency.MONTHLY -> "Every ${rule.interval} months"
        Frequency.YEARLY -> "Every ${rule.interval} years"
    }
    val where = when (rule.type) {
        TxnType.TRANSFER -> "${rule.accountName} to ${rule.toAccountName.orEmpty()}"
        else -> listOfNotNull(rule.categoryName, rule.accountName).joinToString(" · ")
    }
    return "$every · $where"
}

@Composable
private fun RuleEditorDialog(
    existing: RecurringRuleDetail?,
    accounts: List<com.financetracker.app.data.account.AccountWithBalance>,
    categories: List<Category>,
    onSave: (String, TxnType, Long, Long?, Long?, String, String, String, String, Frequency, Int, Long, Long?, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember { mutableStateOf(existing?.type ?: TxnType.EXPENSE) }
    var accountId by remember { mutableStateOf(existing?.accountId ?: accounts.firstOrNull()?.id) }
    var toAccountId by remember { mutableStateOf(existing?.toAccountId) }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var frequency by remember { mutableStateOf(existing?.frequency ?: Frequency.MONTHLY) }
    var interval by remember { mutableStateOf((existing?.interval ?: 1).toString()) }
    var firstDue by remember { mutableStateOf(existing?.nextDueMillis ?: System.currentTimeMillis()) }
    var autoPost by remember { mutableStateOf(existing?.autoPost ?: true) }
    var payee by remember { mutableStateOf(existing?.payee.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }

    val account = accounts.firstOrNull { it.id == accountId }
    val currency = account?.currencyCode ?: "EUR"
    var amount by remember {
        mutableStateOf(existing?.let { Money.editString(it.amountMinor, it.accountCurrency) } ?: "")
    }

    var showTypePicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showToAccountPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showFrequencyPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New recurring rule" else "Edit rule") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Rent, Netflix, Salary") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                PickerRow("Type", type.label) { showTypePicker = true }
                PickerRow(
                    if (type == TxnType.INCOME) "To account" else "From account",
                    account?.name ?: "Pick one"
                ) { showAccountPicker = true }

                if (type == TxnType.TRANSFER) {
                    PickerRow(
                        "To account",
                        accounts.firstOrNull { it.id == toAccountId }?.name ?: "Pick one"
                    ) { showToAccountPicker = true }
                } else {
                    PickerRow(
                        "Category",
                        categories.firstOrNull { it.id == categoryId }?.name ?: "Pick one"
                    ) { showCategoryPicker = true }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount ($currency)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                PickerRow("Repeats", frequency.label) { showFrequencyPicker = true }
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it.filter { char -> char.isDigit() } },
                    label = { Text("Every N ${frequency.label.lowercase().removeSuffix("ly")} periods") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                PickerRow("First due", shortDate(firstDue)) { showDatePicker = true }

                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = payee,
                    onValueChange = { payee = it },
                    label = { Text("Payee (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoPost, onCheckedChange = { autoPost = it })
                    Column {
                        Text("Post automatically", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Off means it waits on the dashboard for you to confirm each time.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = accountId ?: return@TextButton
                    onSave(
                        name, type, id, toAccountId, categoryId, amount, currency,
                        payee, note, frequency, interval.toIntOrNull() ?: 1, firstDue, null, autoPost
                    )
                },
                enabled = name.isNotBlank() && amount.isNotBlank() && accountId != null &&
                    (type != TxnType.TRANSFER || toAccountId != null) &&
                    (type == TxnType.TRANSFER || categoryId != null)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showTypePicker) {
        OptionPickerDialog(
            title = "Type",
            options = TxnType.entries,
            selected = type,
            label = { it.label },
            onSelect = { type = it; categoryId = null; showTypePicker = false },
            onDismiss = { showTypePicker = false }
        )
    }
    if (showAccountPicker) {
        OptionPickerDialog(
            title = "Account",
            options = accounts,
            selected = account,
            label = { it.name },
            supporting = { it.currencyCode },
            leadingColor = { it.colorArgb },
            onSelect = { accountId = it.id; showAccountPicker = false },
            onDismiss = { showAccountPicker = false }
        )
    }
    if (showToAccountPicker) {
        OptionPickerDialog(
            title = "Transfer to",
            options = accounts.filter { it.id != accountId },
            selected = accounts.firstOrNull { it.id == toAccountId },
            label = { it.name },
            leadingColor = { it.colorArgb },
            onSelect = { toAccountId = it.id; showToAccountPicker = false },
            onDismiss = { showToAccountPicker = false }
        )
    }
    if (showCategoryPicker) {
        OptionPickerDialog(
            title = "Category",
            options = categories,
            selected = categories.firstOrNull { it.id == categoryId },
            label = { if (it.parentId == null) it.name else "    ${it.name}" },
            leadingColor = { it.colorArgb },
            onSelect = { categoryId = it.id; showCategoryPicker = false },
            onDismiss = { showCategoryPicker = false }
        )
    }
    if (showFrequencyPicker) {
        OptionPickerDialog(
            title = "Repeats",
            options = Frequency.entries,
            selected = frequency,
            label = { it.label },
            onSelect = { frequency = it; showFrequencyPicker = false },
            onDismiss = { showFrequencyPicker = false }
        )
    }
    if (showDatePicker) {
        RuleDatePicker(
            initialMillis = firstDue,
            onPick = { firstDue = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDatePicker(initialMillis: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val pickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initialMillis
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val selected = pickerState.selectedDateMillis
                if (selected != null) {
                    val localDate = java.time.Instant.ofEpochMilli(selected)
                        .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    onPick(localDate.withTimeFrom(initialMillis))
                } else onDismiss()
            }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        androidx.compose.material3.DatePicker(state = pickerState)
    }
}
