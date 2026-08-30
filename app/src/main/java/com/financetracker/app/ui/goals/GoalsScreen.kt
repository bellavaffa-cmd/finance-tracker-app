package com.financetracker.app.ui.goals

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
import com.financetracker.app.data.goal.Goal
import com.financetracker.app.data.goal.GoalProgress
import com.financetracker.app.ui.accounts.PickerRow
import com.financetracker.app.ui.common.CategoryDot
import com.financetracker.app.ui.common.ColorPickerRow
import com.financetracker.app.ui.common.ConfirmDialog
import com.financetracker.app.ui.common.EmptyState
import com.financetracker.app.ui.common.OptionPickerDialog
import com.financetracker.app.ui.common.PALETTE
import com.financetracker.app.ui.common.ProgressBar
import com.financetracker.app.ui.common.SectionCard
import com.financetracker.app.ui.common.fullDate
import com.financetracker.app.ui.common.withTimeFrom
import com.financetracker.app.ui.theme.Accent
import com.financetracker.app.ui.theme.Bg
import com.financetracker.app.ui.theme.Negative
import com.financetracker.app.ui.theme.Positive
import com.financetracker.app.ui.theme.Warn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(viewModel: GoalsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Goal?>(null) }
    var deleting by remember { mutableStateOf<Goal?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Savings goals") },
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
                Icon(Icons.Filled.Add, contentDescription = "Add goal")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.goals.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        "No goals yet",
                        if (state.accounts.isEmpty()) "Add an account first, then set a target on it."
                        else "Pick a savings account and a target, and this tracks the rest."
                    )
                }
            }

            for (progress in state.goals) {
                item(key = progress.goal.id) {
                    GoalCard(
                        progress = progress,
                        onEdit = { editing = progress.goal },
                        onDelete = { deleting = progress.goal }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (creating || editing != null) {
        GoalEditorDialog(
            existing = editing,
            accounts = state.accounts,
            onSave = { name, accountId, target, date, colour, note ->
                viewModel.saveGoal(editing, name, accountId, target, date, colour, note)
                creating = false
                editing = null
            },
            onDismiss = { creating = false; editing = null }
        )
    }

    deleting?.let { goal ->
        ConfirmDialog(
            title = "Delete ${goal.name}?",
            body = "The account and its money are untouched - only the target is removed.",
            onConfirm = { viewModel.delete(goal); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun GoalCard(progress: GoalProgress, onEdit: () -> Unit, onDelete: () -> Unit) {
    SectionCard(modifier = Modifier.clickable { onEdit() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(progress.goal.colorArgb, size = 11)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    progress.goal.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    progress.accountName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${Math.round(progress.fraction * 100)}%",
                style = MaterialTheme.typography.titleSmall,
                color = if (progress.isComplete) Positive else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(10.dp))
        ProgressBar(
            fraction = progress.fraction,
            color = when {
                progress.isComplete -> Positive
                progress.isOverdue -> Negative
                else -> Accent
            },
            height = 10
        )
        Spacer(Modifier.height(8.dp))

        Text(
            "${Money.format(progress.savedMinor, progress.currencyCode)} of " +
                Money.format(progress.goal.targetMinor, progress.currencyCode),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(4.dp))
        Text(
            statusLine(progress),
            style = MaterialTheme.typography.labelSmall,
            color = when {
                progress.isComplete -> Positive
                progress.isOverdue -> Negative
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDelete) { Text("Delete", color = Negative) }
        }
    }
}

/** The one line that answers "am I on track", which is the only question a goal card needs to. */
private fun statusLine(progress: GoalProgress): String {
    if (progress.isComplete) return "Reached"
    val remaining = Money.format(progress.remainingMinor, progress.currencyCode)
    val perMonth = progress.requiredPerMonthMinor
    val months = progress.monthsRemaining

    return when {
        progress.isOverdue -> "$remaining short, and the date has passed"
        perMonth != null && months != null ->
            "$remaining to go · ${Money.format(perMonth, progress.currencyCode)} a month for $months more"
        else -> "$remaining to go"
    }
}

@Composable
private fun GoalEditorDialog(
    existing: Goal?,
    accounts: List<com.financetracker.app.data.account.AccountWithBalance>,
    onSave: (String, Long, String, Long?, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var accountId by remember { mutableStateOf(existing?.accountId ?: accounts.firstOrNull()?.id) }
    val account = accounts.firstOrNull { it.id == accountId }
    val currency = account?.currencyCode ?: "EUR"
    var target by remember {
        mutableStateOf(existing?.let { Money.editString(it.targetMinor, currency) } ?: "")
    }
    var targetDate by remember { mutableStateOf(existing?.targetDateMillis) }
    var colour by remember { mutableStateOf(existing?.colorArgb ?: PALETTE.first()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New goal" else "Edit goal") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Emergency fund") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                PickerRow("Account", account?.name ?: "Pick one") { showAccountPicker = true }

                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Target ($currency)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                PickerRow(
                    "By",
                    targetDate?.let { fullDate(it) } ?: "No deadline"
                ) { showDatePicker = true }

                if (targetDate != null) {
                    TextButton(onClick = { targetDate = null }) { Text("Clear deadline") }
                }

                if (existing == null && account != null && account.balanceMinor > 0) {
                    Text(
                        "This account already holds " +
                            "${Money.format(account.balanceMinor, currency)}. That is treated as " +
                            "the starting point, so the goal begins at zero progress.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Warn
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text("Colour", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                ColorPickerRow(selected = colour, onSelect = { colour = it })

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { accountId?.let { onSave(name, it, target, targetDate, colour, note) } },
                enabled = name.isNotBlank() && target.isNotBlank() && accountId != null
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showAccountPicker) {
        OptionPickerDialog(
            title = "Account",
            options = accounts,
            selected = account,
            label = { it.name },
            supporting = { "${it.type.label} · ${Money.format(it.balanceMinor, it.currencyCode)}" },
            leadingColor = { it.colorArgb },
            onSelect = { accountId = it.id; showAccountPicker = false },
            onDismiss = { showAccountPicker = false }
        )
    }

    if (showDatePicker) {
        GoalDatePicker(
            initialMillis = targetDate ?: System.currentTimeMillis(),
            onPick = { targetDate = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDatePicker(initialMillis: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
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
