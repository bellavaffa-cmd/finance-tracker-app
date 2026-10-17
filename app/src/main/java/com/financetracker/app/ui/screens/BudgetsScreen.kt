package com.financetracker.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financetracker.app.domain.BudgetLine
import com.financetracker.app.domain.Ledger
import com.financetracker.app.domain.Money
import com.financetracker.app.ui.AmountLine
import com.financetracker.app.ui.ColorDot
import com.financetracker.app.ui.LedgerState
import com.financetracker.app.ui.MonthSwitcher
import com.financetracker.app.ui.SectionCard
import com.financetracker.app.ui.netColor
import com.financetracker.app.ui.theme.ExpenseRed
import com.financetracker.app.ui.theme.IncomeGreen
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(state: LedgerState, onSetBudget: (Long, Long?) -> Unit) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var editing by remember { mutableStateOf<BudgetLine?>(null) }
    val lines = remember(state, month) { Ledger.budgetLines(state.categories, state.budgets, state.txns, month) }
    val budgeted = lines.filter { it.limit != null }
    val totalLimit = budgeted.sumOf { it.limit ?: 0 }
    val totalSpent = budgeted.sumOf { it.spent }

    Scaffold(topBar = { TopAppBar(title = { Text("Budgets") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { MonthSwitcher(month, { month = it }) }
            item {
                SectionCard("Budgeted categories") {
                    if (budgeted.isEmpty()) {
                        Text("No budgets yet. Tap a category below to set a monthly limit.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        AmountLine("Limit", totalLimit, state.currency)
                        AmountLine("Spent", totalSpent, state.currency)
                        AmountLine("Left", totalLimit - totalSpent, state.currency, netColor(totalLimit - totalSpent), bold = true)
                    }
                }
            }
            items(lines, key = { it.category.id }) { line -> BudgetRow(line, state.currency) { editing = line } }
        }
    }

    editing?.let { line ->
        var input by remember(line) { mutableStateOf(line.limit?.let { Money.plain(it, state.currency) } ?: "") }
        var invalid by remember(line) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Monthly budget: ${line.category.name}") },
            text = {
                OutlinedTextField(
                    value = input, onValueChange = { input = it; invalid = false },
                    label = { Text("Limit (${state.currency})") }, singleLine = true, isError = invalid,
                    supportingText = { Text(if (invalid) "Enter an amount above zero." else "Applies to every month.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = Money.parse(input, state.currency)
                    if (v == null || v <= 0) invalid = true else { onSetBudget(line.category.id, v); editing = null }
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (line.limit != null) {
                        TextButton(onClick = { onSetBudget(line.category.id, null); editing = null }) { Text("Remove") }
                    }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun BudgetRow(line: BudgetLine, currency: String, onClick: () -> Unit) {
    SectionCard(null, Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(Color(line.category.color))
            Spacer(Modifier.width(10.dp))
            Text(line.category.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(Money.format(line.spent, currency), fontWeight = FontWeight.Medium)
        }
        val limit = line.limit
        if (limit == null) {
            Text("No budget · tap to set one", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Spacer(Modifier.height(8.dp))
            val over = line.spent > limit
            LinearProgressIndicator(
                progress = { line.fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    over -> ExpenseRed
                    line.fraction >= 0.8f -> Color(0xFFF5A623)
                    else -> IncomeGreen
                },
            )
            Spacer(Modifier.height(4.dp))
            Column {
                val remaining = limit - line.spent
                Text(
                    (if (over) "${Money.format(-remaining, currency)} over" else "${Money.format(remaining, currency)} left") +
                        " of ${Money.format(limit, currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
