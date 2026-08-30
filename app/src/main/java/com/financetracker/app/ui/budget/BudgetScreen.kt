package com.financetracker.app.ui.budget

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.financetracker.app.data.budget.Budget
import com.financetracker.app.data.budget.BudgetProgress
import com.financetracker.app.data.budget.BudgetRepository
import com.financetracker.app.data.category.Category
import com.financetracker.app.ui.common.CategoryDot
import com.financetracker.app.ui.common.ConfirmDialog
import com.financetracker.app.ui.common.EmptyState
import com.financetracker.app.ui.common.OptionPickerDialog
import com.financetracker.app.ui.common.PeriodBar
import com.financetracker.app.ui.common.ProgressBar
import com.financetracker.app.ui.common.SectionCard
import com.financetracker.app.ui.common.StatTile
import com.financetracker.app.ui.home.budgetColor
import com.financetracker.app.ui.theme.Accent
import com.financetracker.app.ui.theme.Negative
import com.financetracker.app.ui.theme.Positive

@Composable
fun BudgetScreen(viewModel: BudgetViewModel) {
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<BudgetProgress?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Budget?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PeriodBar(
                period = state.period,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Budgeted",
                    value = Money.format(state.totalBudgetedMinor, state.baseCurrency),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Spent",
                    value = Money.format(state.totalSpentMinor, state.baseCurrency),
                    valueColor = if (state.totalSpentMinor > state.totalBudgetedMinor) Negative
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Over",
                    value = state.overBudgetCount.toString(),
                    valueColor = if (state.overBudgetCount > 0) Negative else Positive,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (state.progress.isEmpty() && !state.loading) {
            item {
                EmptyState(
                    "No budgets yet",
                    "Set a limit on a category and this screen will track it every month."
                )
            }
        }

        for (progress in state.progress) {
            item(key = progress.budget.id) {
                BudgetCard(
                    progress = progress,
                    baseCurrency = state.baseCurrency,
                    onEdit = { editing = progress },
                    onDelete = { deleting = progress.budget }
                )
            }
        }

        item {
            TextButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add a budget")
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    if (creating) {
        BudgetEditorDialog(
            title = "New budget",
            baseCurrency = state.baseCurrency,
            categories = state.budgetableCategories,
            allowOverall = !state.hasOverallBudget,
            initialCategoryId = null,
            initialAmount = "",
            initialRollover = false,
            lockCategory = false,
            onSave = { categoryId, amount, rollover ->
                viewModel.saveBudget(categoryId, amount, rollover)
                creating = false
            },
            onDismiss = { creating = false }
        )
    }

    editing?.let { progress ->
        BudgetEditorDialog(
            title = progress.categoryName,
            baseCurrency = state.baseCurrency,
            categories = state.budgetableCategories,
            allowOverall = false,
            initialCategoryId = progress.budget.categoryId,
            initialAmount = Money.editString(progress.budget.amountMinorBase, state.baseCurrency),
            initialRollover = progress.budget.rollover,
            lockCategory = true,
            onSave = { categoryId, amount, rollover ->
                viewModel.saveBudget(categoryId, amount, rollover)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { budget ->
        ConfirmDialog(
            title = "Remove this budget?",
            body = "Your transactions are untouched - only the limit is removed.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.deleteBudget(budget); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun BudgetCard(
    progress: BudgetProgress,
    baseCurrency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    SectionCard(modifier = Modifier.clickable { onEdit() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(progress.colorArgb, size = 10)
            Spacer(Modifier.width(10.dp))
            Text(
                progress.categoryName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (progress.isOverBudget) "${Money.format(-progress.remainingMinorBase, baseCurrency)} over"
                else "${Money.format(progress.remainingMinorBase, baseCurrency)} left",
                style = MaterialTheme.typography.labelMedium,
                color = if (progress.isOverBudget) Negative else Positive
            )
        }

        Spacer(Modifier.height(10.dp))
        ProgressBar(fraction = progress.fraction, color = budgetColor(progress), height = 10)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${Money.format(progress.spentMinorBase, baseCurrency)} of ${Money.format(progress.allowanceMinorBase, baseCurrency)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onDelete) {
                Text("Remove", style = MaterialTheme.typography.labelSmall, color = Negative)
            }
        }

        // Carry-over is only worth a line when it actually moved the allowance.
        if (progress.budget.rollover && progress.carryOverMinorBase != 0L) {
            Text(
                if (progress.carryOverMinorBase > 0)
                    "${Money.format(progress.carryOverMinorBase, baseCurrency)} carried in from earlier months"
                else
                    "${Money.format(-progress.carryOverMinorBase, baseCurrency)} of earlier overspend carried in",
                style = MaterialTheme.typography.labelSmall,
                color = if (progress.carryOverMinorBase > 0) Positive else Negative
            )
        }
    }
}

@Composable
private fun BudgetEditorDialog(
    title: String,
    baseCurrency: String,
    categories: List<Category>,
    allowOverall: Boolean,
    initialCategoryId: Long?,
    initialAmount: String,
    initialRollover: Boolean,
    lockCategory: Boolean,
    onSave: (Long?, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var categoryId by remember { mutableStateOf(initialCategoryId) }
    var amount by remember { mutableStateOf(initialAmount) }
    var rollover by remember { mutableStateOf(initialRollover) }
    var showPicker by remember { mutableStateOf(false) }
    // "Overall" is only a valid starting choice when no overall budget exists yet.
    var isOverall by remember { mutableStateOf(initialCategoryId == null && lockCategory) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (!lockCategory) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isOverall) { showPicker = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Category",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(96.dp)
                        )
                        Text(
                            when {
                                isOverall -> BudgetRepository.OVERALL_LABEL
                                categoryId == null -> "Pick one"
                                else -> categories.firstOrNull { it.id == categoryId }?.name ?: "Pick one"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isOverall) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (allowOverall) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isOverall,
                                onCheckedChange = {
                                    isOverall = it
                                    if (it) categoryId = null
                                }
                            )
                            Text(
                                "Cap total spending instead",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monthly limit ($baseCurrency)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rollover, onCheckedChange = { rollover = it })
                    Column {
                        Text("Roll over", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Unspent money raises next month's limit; overspending lowers it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(if (isOverall) null else categoryId, amount, rollover) },
                enabled = amount.isNotBlank() && (isOverall || categoryId != null || lockCategory)
            ) { Text("Save", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showPicker) {
        OptionPickerDialog(
            title = "Category",
            options = categories,
            selected = categories.firstOrNull { it.id == categoryId },
            label = { it.name },
            leadingColor = { it.colorArgb },
            onSelect = { categoryId = it.id; showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}
