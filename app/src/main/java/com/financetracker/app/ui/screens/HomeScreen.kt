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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financetracker.app.domain.Ledger
import com.financetracker.app.domain.Money
import com.financetracker.app.ui.AmountLine
import com.financetracker.app.ui.EmptyHint
import com.financetracker.app.ui.LedgerState
import com.financetracker.app.ui.SectionCard
import com.financetracker.app.ui.TxnRow
import com.financetracker.app.ui.netColor
import com.financetracker.app.ui.theme.ExpenseRed
import com.financetracker.app.ui.theme.IncomeGreen
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: LedgerState,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onBudgets: () -> Unit,
) {
    val month = YearMonth.now()
    val totals = remember(state.txns) { Ledger.totals(state.txns, month) }
    val budgetAlerts = remember(state) {
        Ledger.budgetLines(state.categories, state.budgets, state.txns, month).filter { it.limit != null && it.fraction >= 0.8f }
    }
    val netWorth = state.activeAccounts.sumOf { state.balances[it.id] ?: 0 }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Finance Tracker") }) },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add transaction") } },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard("Net worth") {
                    Text(Money.format(netWorth, state.currency), style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    state.activeAccounts.forEach { a ->
                        AmountLine(a.name, state.balances[a.id] ?: 0, state.currency)
                    }
                }
            }
            item {
                SectionCard("This month") {
                    AmountLine("Income", totals.income, state.currency, IncomeGreen)
                    AmountLine("Spending", totals.expense, state.currency, ExpenseRed)
                    AmountLine("Net", totals.net, state.currency, netColor(totals.net), bold = true)
                }
            }
            if (budgetAlerts.isNotEmpty()) {
                item {
                    SectionCard("Budgets to watch", Modifier.clickable(onClick = onBudgets)) {
                        budgetAlerts.forEach { line ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(line.category.name, Modifier.width(110.dp))
                                LinearProgressIndicator(
                                    progress = { line.fraction.coerceAtMost(1f) },
                                    modifier = Modifier.weight(1f),
                                    color = if (line.fraction > 1f) ExpenseRed else MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("${(line.fraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSeeAll) { Text("See all") }
                }
            }
            val recent = state.txns.take(8)
            if (recent.isEmpty()) {
                item { EmptyHint("No transactions yet. Tap + to add your first one.") }
            } else {
                item {
                    SectionCard(null) {
                        Column { recent.forEach { t -> TxnRow(t, state, onClick = { onOpen(t.id) }, showDate = true) } }
                    }
                }
            }
        }
    }
}
