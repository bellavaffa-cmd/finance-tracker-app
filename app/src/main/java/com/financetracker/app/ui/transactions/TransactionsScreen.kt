package com.financetracker.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.Money
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.txn.TxnType
import com.financetracker.app.ui.common.EmptyState
import com.financetracker.app.ui.common.OptionPickerDialog
import com.financetracker.app.ui.common.PeriodBar
import com.financetracker.app.ui.common.TransactionRow
import com.financetracker.app.ui.common.dayHeader
import com.financetracker.app.ui.theme.Accent
import com.financetracker.app.ui.theme.Negative
import com.financetracker.app.ui.theme.Positive
import com.financetracker.app.ui.theme.Surface1
import com.financetracker.app.ui.theme.Surface2

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onEditTransaction: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var searching by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        PeriodBar(
            period = state.period,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryPill("In", Money.format(state.incomeMinor, state.baseCurrency), Positive, Modifier.weight(1f))
            SummaryPill("Out", Money.format(state.expenseMinor, state.baseCurrency), Negative, Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))

        if (searching) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Payee, note or category") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { viewModel.setQuery(""); searching = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close search")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!searching) {
                FilterChip(
                    label = "Search",
                    selected = state.query.isNotBlank(),
                    onClick = { searching = true },
                    leading = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.width(15.dp)) }
                )
            }
            TxnType.entries.forEach { type ->
                FilterChip(
                    label = type.label,
                    selected = state.type == type,
                    onClick = { viewModel.setType(if (state.type == type) null else type) }
                )
            }
            FilterChip(
                label = state.accounts.firstOrNull { it.id == state.accountId }?.name ?: "Account",
                selected = state.accountId != null,
                onClick = { showAccountPicker = true }
            )
            FilterChip(
                label = state.categories.firstOrNull { it.id == state.categoryId }?.name ?: "Category",
                selected = state.categoryId != null,
                onClick = { showCategoryPicker = true }
            )
            if (state.isFiltered) {
                TextButton(onClick = viewModel::clearFilters) { Text("Clear") }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.days.isEmpty() && !state.loading) {
            EmptyState(
                title = if (state.isFiltered) "Nothing matches" else "No transactions this month",
                body = if (state.isFiltered) "Try widening or clearing the filters."
                else "Tap + to record one."
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)
        ) {
            state.days.forEach { day ->
                item(key = "header-${day.dateMillis}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            dayHeader(day.dateMillis),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            Money.format(day.netMinorBase, state.baseCurrency, withSign = true),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (day.netMinorBase >= 0) Positive else Negative
                        )
                    }
                }
                items(day.items.size, key = { index -> day.items[index].id }) { index ->
                    val detail = day.items[index]
                    TransactionRow(
                        detail = detail,
                        onClick = { onEditTransaction(detail.id) }
                    )
                }
            }
        }
    }

    if (showAccountPicker) {
        OptionPickerDialog(
            title = "Filter by account",
            options = state.accounts,
            selected = state.accounts.firstOrNull { it.id == state.accountId },
            label = { it.name },
            supporting = { it.currencyCode },
            leadingColor = { it.colorArgb },
            onSelect = {
                viewModel.setAccount(if (state.accountId == it.id) null else it.id)
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false }
        )
    }

    if (showCategoryPicker) {
        val selectable = remember(state.categories) {
            state.categories.filter { !it.isArchived }.sortedBy { it.sortOrder }
        }
        OptionPickerDialog(
            title = "Filter by category",
            options = selectable,
            selected = selectable.firstOrNull { it.id == state.categoryId },
            label = { category: Category ->
                if (category.parentId == null) category.name else "    ${category.name}"
            },
            leadingColor = { it.colorArgb },
            onSelect = {
                viewModel.setCategory(if (state.categoryId == it.id) null else it.id)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }
}

@Composable
private fun SummaryPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Accent.copy(alpha = 0.22f) else Surface2)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Accent else MaterialTheme.colorScheme.onSurface
        )
    }
}
