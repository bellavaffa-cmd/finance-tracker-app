package com.financetracker.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.TxnType
import com.financetracker.app.domain.Ledger
import com.financetracker.app.domain.Ledger.epochDays
import com.financetracker.app.domain.Money
import com.financetracker.app.ui.EmptyHint
import com.financetracker.app.ui.LedgerState
import com.financetracker.app.ui.MonthSwitcher
import com.financetracker.app.ui.TxnRow
import com.financetracker.app.ui.categoryName
import com.financetracker.app.ui.dayFormat
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(state: LedgerState, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(state, month, query) {
        val days = month.epochDays()
        val q = query.trim().lowercase()
        state.txns.filter { t ->
            t.date in days && (q.isEmpty() ||
                t.payee.lowercase().contains(q) || t.note.lowercase().contains(q) ||
                categoryName(state, t.categoryId).lowercase().contains(q) ||
                state.accountById[t.accountId]?.name?.lowercase()?.contains(q) == true)
        }
    }
    val totals = remember(filtered) { Ledger.totals(filtered, month) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ledger") }) },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add transaction") } },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            MonthSwitcher(month, { month = it }, Modifier.padding(horizontal = 8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search payee, note, category, account") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Text(
                "In ${Money.format(totals.income, state.currency)}  ·  Out ${Money.format(totals.expense, state.currency)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (filtered.isEmpty()) {
                EmptyHint(if (query.isBlank()) "Nothing recorded this month." else "No matches.")
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                filtered.groupBy { it.date }.forEach { (day, list) ->
                    item(key = "d$day") {
                        val dayNet = list.sumOf { if (it.type == TxnType.INCOME) it.amount else if (it.type == TxnType.EXPENSE) -it.amount else 0 }
                        Column {
                            HorizontalDivider()
                            Text(
                                LocalDate.ofEpochDay(day).format(dayFormat) + "   " + Money.format(dayNet, state.currency, signed = true),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
                            )
                        }
                    }
                    items(list, key = { it.id }) { t -> TxnRow(t, state, onClick = { onOpen(t.id) }) }
                }
            }
        }
    }
}
