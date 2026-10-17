package com.financetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.Txn
import com.financetracker.app.data.TxnType
import com.financetracker.app.domain.Money
import com.financetracker.app.ui.theme.ExpenseRed
import com.financetracker.app.ui.theme.IncomeGreen
import com.financetracker.app.ui.theme.UncategorisedGrey
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
val dayFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
fun MonthSwitcher(month: YearMonth, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) { Icon(Icons.Default.ChevronLeft, "Previous month") }
        Text(
            month.format(monthFormat),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable { onChange(YearMonth.now()) },
        )
        IconButton(onClick = { onChange(month.plusMonths(1)) }) { Icon(Icons.Default.ChevronRight, "Next month") }
    }
}

@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
            }
            content()
        }
    }
}

@Composable
fun ColorDot(color: Color, size: Int = 12) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

fun categoryColor(state: LedgerState, categoryId: Long?): Color =
    categoryId?.let { state.categoryById[it] }?.let { Color(it.color) } ?: UncategorisedGrey

fun categoryName(state: LedgerState, categoryId: Long?): String =
    categoryId?.let { state.categoryById[it]?.name } ?: "Uncategorised"

@Composable
fun TxnRow(txn: Txn, state: LedgerState, onClick: () -> Unit, showDate: Boolean = false) {
    val account = state.accountById[txn.accountId]?.name.orEmpty()
    val title: String
    val subtitle: String
    val amountColor: Color
    val signed: Long
    when (txn.type) {
        TxnType.TRANSFER -> {
            title = txn.payee.ifBlank { "Transfer" }
            subtitle = "$account → ${state.accountById[txn.toAccountId]?.name.orEmpty()}"
            amountColor = MaterialTheme.colorScheme.onSurfaceVariant
            signed = txn.amount
        }
        else -> {
            title = txn.payee.ifBlank { categoryName(state, txn.categoryId) }
            subtitle = (if (txn.payee.isBlank()) "" else categoryName(state, txn.categoryId) + " · ") + account
            amountColor = if (txn.type == TxnType.INCOME) IncomeGreen else MaterialTheme.colorScheme.onSurface
            signed = if (txn.type == TxnType.INCOME) txn.amount else -txn.amount
        }
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (txn.type == TxnType.TRANSFER) {
            Icon(Icons.Default.SwapHoriz, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { ColorDot(categoryColor(state, txn.categoryId)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            val sub = if (showDate) LocalDate.ofEpochDay(txn.date).format(dayFormat) + " · " + subtitle else subtitle
            Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            Money.format(signed, state.currency, signed = txn.type == TxnType.INCOME),
            color = amountColor, fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun AmountLine(label: String, amount: Long, currency: String, color: Color = Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Money.format(amount, currency), color = color, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}

fun netColor(amount: Long): Color = if (amount < 0) ExpenseRed else IncomeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T?,
    display: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(display) ?: placeholder,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(display(option)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}
