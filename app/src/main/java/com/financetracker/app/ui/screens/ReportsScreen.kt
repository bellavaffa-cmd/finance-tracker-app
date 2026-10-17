package com.financetracker.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.CategoryKind
import com.financetracker.app.domain.CategorySlice
import com.financetracker.app.domain.Ledger
import com.financetracker.app.domain.MonthTotals
import com.financetracker.app.domain.Money
import com.financetracker.app.ui.ColorDot
import com.financetracker.app.ui.EmptyHint
import com.financetracker.app.ui.LedgerState
import com.financetracker.app.ui.MonthSwitcher
import com.financetracker.app.ui.SectionCard
import com.financetracker.app.ui.categoryColor
import com.financetracker.app.ui.categoryName
import com.financetracker.app.ui.theme.ExpenseRed
import com.financetracker.app.ui.theme.IncomeGreen
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(state: LedgerState) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var kind by rememberSaveable { mutableStateOf(CategoryKind.EXPENSE) }
    val slices = remember(state.txns, month, kind) { Ledger.byCategory(state.txns, month, kind) }
    val history = remember(state.txns, month) { Ledger.history(state.txns, month, 6) }
    val total = slices.sumOf { it.amount }

    Scaffold(topBar = { TopAppBar(title = { Text("Reports") }) }) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonthSwitcher(month, { month = it })
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(CategoryKind.EXPENSE to "Spending", CategoryKind.INCOME to "Income").forEachIndexed { i, (k, label) ->
                    SegmentedButton(selected = kind == k, onClick = { kind = k },
                        shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                }
            }
            SectionCard(if (kind == CategoryKind.EXPENSE) "Spending by category" else "Income by category") {
                if (slices.isEmpty()) {
                    EmptyHint("Nothing recorded this month.")
                } else {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Donut(slices.map { categoryColor(state, it.categoryId) to it.amount }, Modifier.size(180.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(total, state.currency), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    slices.forEach { LegendRow(it, total, state) }
                }
            }
            SectionCard("Last 6 months") {
                MonthBars(history, state.currency)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LegendRow(slice: CategorySlice, total: Long, state: LedgerState) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ColorDot(categoryColor(state, slice.categoryId))
        Spacer(Modifier.width(10.dp))
        Text(categoryName(state, slice.categoryId), Modifier.weight(1f))
        Text(
            "${(slice.amount * 1000 / total.coerceAtLeast(1) + 5) / 10}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(12.dp))
        Text(Money.format(slice.amount, state.currency), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Donut(parts: List<Pair<Color, Long>>, modifier: Modifier) {
    val total = parts.sumOf { it.second }.toFloat()
    Canvas(modifier) {
        val stroke = size.minDimension * 0.16f
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        // A hairline gap between slices keeps neighbouring colours distinct; skip it for a single slice.
        val gap = if (parts.size > 1) 1.2f else 0f
        var start = -90f
        for ((color, amount) in parts) {
            val sweep = 360f * amount / total
            drawArc(color, start + gap / 2, (sweep - gap).coerceAtLeast(0.1f), useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            start += sweep
        }
    }
}

@Composable
private fun MonthBars(history: List<MonthTotals>, currency: String) {
    val max = history.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(1).toFloat()
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { ColorDot(IncomeGreen, 10); Spacer(Modifier.width(6.dp)); Text("Income", style = MaterialTheme.typography.bodySmall) }
        Row(verticalAlignment = Alignment.CenterVertically) { ColorDot(ExpenseRed, 10); Spacer(Modifier.width(6.dp)); Text("Spending", style = MaterialTheme.typography.bodySmall) }
    }
    Spacer(Modifier.height(8.dp))
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val slot = size.width / history.size
        val bar = slot * 0.3f
        history.forEachIndexed { i, m ->
            val x = i * slot + slot / 2
            val hIn = size.height * m.income / max
            val hOut = size.height * m.expense / max
            drawRoundRect(IncomeGreen, Offset(x - bar - 1.dp.toPx(), size.height - hIn), Size(bar, hIn), CornerRadius(3.dp.toPx()))
            drawRoundRect(ExpenseRed, Offset(x + 1.dp.toPx(), size.height - hOut), Size(bar, hOut), CornerRadius(3.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        history.forEach {
            Text(it.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), Modifier.weight(1f),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(12.dp))
    history.reversed().forEach { m ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(m.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + m.month.year, Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall)
            Text(Money.format(m.net, currency, signed = true), style = MaterialTheme.typography.bodySmall,
                color = if (m.net < 0) ExpenseRed else IncomeGreen)
        }
    }
}
