package com.financetracker.app.ui.reports

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.Money
import com.financetracker.app.data.tag.TagTotal
import com.financetracker.app.data.txn.TxnType
import com.financetracker.app.ui.common.CategoryDot
import com.financetracker.app.ui.common.EmptyState
import com.financetracker.app.ui.common.PeriodBar
import com.financetracker.app.ui.common.ProgressBar
import com.financetracker.app.ui.common.SectionCard
import com.financetracker.app.ui.theme.Negative
import com.financetracker.app.ui.theme.Positive
import com.financetracker.app.ui.theme.Surface2

@Composable
fun ReportsScreen(viewModel: ReportsViewModel) {
    val state by viewModel.uiState.collectAsState()

    // Ten slices is about the limit of what a donut stays readable at; the rest are summed into a
    // single "Other" arc, which is still clickable-through in the list below.
    val slices = remember(state.breakdown) {
        val top = state.breakdown.take(MAX_SLICES)
        val rest = state.breakdown.drop(MAX_SLICES)
        buildList {
            addAll(top.map { DonutSlice(it.name, it.amountMinorBase, Color(it.colorArgb)) })
            if (rest.isNotEmpty()) {
                add(
                    DonutSlice(
                        "Other",
                        rest.sumOf { it.amountMinorBase },
                        Color(ReportsViewModel.UNCATEGORISED_COLOR)
                    )
                )
            }
        }
    }

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
            DirectionToggle(
                selected = state.direction,
                onSelect = viewModel::setDirection
            )
        }

        item {
            SectionCard {
                if (state.breakdown.isEmpty()) {
                    EmptyState(
                        title = if (state.direction == TxnType.EXPENSE) "No spending this month"
                        else "No income this month",
                        body = "Record a transaction and the breakdown appears here."
                    )
                } else {
                    DonutChart(
                        slices = slices,
                        centerPrimary = Money.formatCompact(state.totalMinorBase, state.baseCurrency),
                        centerSecondary = state.period.shortLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )

                    Spacer(Modifier.height(12.dp))
                    ComparisonLine(state)
                }
            }
        }

        if (state.breakdown.isNotEmpty()) {
            item {
                Text(
                    "Breakdown",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            for (row in state.breakdown) {
                item(key = row.categoryId ?: -1L) {
                    BreakdownRow(row, state.baseCurrency, state.direction)
                }
            }
        }

        if (state.tagTotals.isNotEmpty()) {
            item {
                SectionCard(title = "By tag") {
                    Text(
                        "A transaction can carry several tags, so these can add up to more than " +
                            "the month's total.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    state.tagTotals.forEach { total ->
                        TagTotalRow(total, state.baseCurrency, state.totalMinorBase)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun DirectionToggle(selected: TxnType, onSelect: (TxnType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(TxnType.EXPENSE, TxnType.INCOME).forEach { type ->
            val isSelected = type == selected
            val tint = if (type == TxnType.EXPENSE) Negative else Positive
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) tint.copy(alpha = 0.20f) else Color.Transparent)
                    .clickable { onSelect(type) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (type == TxnType.EXPENSE) "Spending" else "Income",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) tint else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Month-on-month change. For spending, up is bad and down is good; for income it is the other way
 * round, so the colour is chosen from the direction rather than from the sign alone.
 */
@Composable
private fun ComparisonLine(state: ReportsUiState) {
    val change = state.changePercent ?: return
    val isSpending = state.direction == TxnType.EXPENSE
    val isFavourable = if (isSpending) change <= 0 else change >= 0
    val previous = Money.format(state.comparisonMinorBase, state.baseCurrency)
    val summary = when {
        change > 0 -> "$change% more than last month ($previous)"
        change < 0 -> "${-change}% less than last month ($previous)"
        else -> "Level with last month ($previous)"
    }

    Text(
        summary,
        style = MaterialTheme.typography.bodyMedium,
        color = if (change == 0) MaterialTheme.colorScheme.onSurfaceVariant
        else if (isFavourable) Positive else Negative,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BreakdownRow(row: CategoryBreakdown, baseCurrency: String, direction: TxnType) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(row.colorArgb, size = 10)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${row.transactionCount} ${if (row.transactionCount == 1) "entry" else "entries"} · ${Math.round(row.fraction * 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                Money.format(row.amountMinorBase, baseCurrency),
                style = MaterialTheme.typography.bodyLarge,
                color = if (direction == TxnType.EXPENSE) MaterialTheme.colorScheme.onSurface else Positive
            )
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(
            fraction = row.fraction,
            color = Color(row.colorArgb),
            height = 5
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TagTotalRow(total: TagTotal, baseCurrency: String, periodTotalMinor: Long) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(total.tag.colorArgb, size = 9)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "#" + total.tag.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${total.transactionCount} ${if (total.transactionCount == 1) "entry" else "entries"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                Money.format(total.amountMinorBase, baseCurrency),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(5.dp))
        ProgressBar(
            fraction = if (periodTotalMinor <= 0) 0f
            else total.amountMinorBase.toFloat() / periodTotalMinor.toFloat(),
            color = Color(total.tag.colorArgb),
            height = 4
        )
    }
}

private const val MAX_SLICES = 10
