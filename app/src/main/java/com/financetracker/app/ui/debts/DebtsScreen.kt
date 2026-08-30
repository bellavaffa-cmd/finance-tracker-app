package com.financetracker.app.ui.debts

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.Money
import com.financetracker.app.data.debt.Debt
import com.financetracker.app.data.debt.DebtKind
import com.financetracker.app.data.debt.PayoffProjection
import com.financetracker.app.data.debt.StrategyComparison
import com.financetracker.app.data.debt.StrategyResult
import com.financetracker.app.ui.accounts.PickerRow
import com.financetracker.app.ui.common.CategoryDot
import com.financetracker.app.ui.common.ColorPickerRow
import com.financetracker.app.ui.common.ConfirmDialog
import com.financetracker.app.ui.common.EmptyState
import com.financetracker.app.ui.common.OptionPickerDialog
import com.financetracker.app.ui.common.PALETTE
import com.financetracker.app.ui.common.SectionCard
import com.financetracker.app.ui.common.StatTile
import com.financetracker.app.ui.theme.Accent
import com.financetracker.app.ui.theme.Bg
import com.financetracker.app.ui.theme.Negative
import com.financetracker.app.ui.theme.Positive
import com.financetracker.app.ui.theme.Surface2
import com.financetracker.app.ui.theme.Warn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtsScreen(viewModel: DebtsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Debt?>(null) }
    var deleting by remember { mutableStateOf<Debt?>(null) }
    var paying by remember { mutableStateOf<Debt?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debts") },
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
                Icon(Icons.Filled.Add, contentDescription = "Add debt")
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
            if (state.debts.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        "No debts tracked",
                        "Add a card or loan with its rate and minimum payment, and this works out " +
                            "when it will be gone."
                    )
                }
            }

            if (state.payable.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(
                            label = "Total owed",
                            value = Money.format(
                                state.totalOwedMinor,
                                state.payableCurrency ?: state.baseCurrency
                            ),
                            valueColor = Negative,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "Minimums",
                            value = Money.format(
                                state.totalMinimumsMinor,
                                state.payableCurrency ?: state.baseCurrency
                            ),
                            caption = "per month",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (state.mixedCurrencies) {
                item {
                    SectionCard {
                        Text(
                            "Payoff strategies are hidden while your debts are in different " +
                                "currencies - comparing them would mean adding one currency to " +
                                "another, which would give a confidently wrong answer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Warn
                        )
                    }
                }
            }

            state.comparison?.let { comparison ->
                item {
                    StrategySection(
                        comparison = comparison,
                        currency = state.payableCurrency ?: state.baseCurrency,
                        extraInput = state.extraMonthlyInput,
                        onExtraChange = viewModel::setExtraMonthly
                    )
                }
            }

            for (debt in state.owed) {
                item(key = "owed-${debt.id}") {
                    DebtCard(
                        debt = debt,
                        projection = state.minimumOnlyProjections[debt.id],
                        onEdit = { editing = debt },
                        onDelete = { deleting = debt },
                        onPay = { paying = debt }
                    )
                }
            }

            if (state.owedToMe.isNotEmpty()) {
                item {
                    Text(
                        "Owed to me",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                for (debt in state.owedToMe) {
                    item(key = "tome-${debt.id}") {
                        DebtCard(
                            debt = debt,
                            projection = null,
                            onEdit = { editing = debt },
                            onDelete = { deleting = debt },
                            onPay = { paying = debt }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (creating || editing != null) {
        DebtEditorDialog(
            existing = editing,
            baseCurrency = state.baseCurrency,
            onSave = { name, kind, balance, currency, rate, minimum, colour, note ->
                viewModel.saveDebt(editing, name, kind, balance, currency, rate, minimum, colour, note)
                creating = false
                editing = null
            },
            onDismiss = { creating = false; editing = null }
        )
    }

    paying?.let { debt ->
        PaymentDialog(
            debt = debt,
            onConfirm = { viewModel.recordPayment(debt, it); paying = null },
            onDismiss = { paying = null }
        )
    }

    deleting?.let { debt ->
        ConfirmDialog(
            title = "Delete ${debt.name}?",
            body = "Any transactions you recorded against it stay in the ledger.",
            onConfirm = { viewModel.delete(debt); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

/**
 * The comparison is the point of this screen: the same debts, the same money, two orderings, and
 * what the difference actually costs.
 */
@Composable
private fun StrategySection(
    comparison: StrategyComparison,
    currency: String,
    extraInput: String,
    onExtraChange: (String) -> Unit
) {
    SectionCard(title = "Payoff plan") {
        OutlinedTextField(
            value = extraInput,
            onValueChange = onExtraChange,
            label = { Text("Extra per month ($currency)") },
            supportingText = { Text("On top of the minimums") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        if (!comparison.usable) {
            Text(
                "On these minimums the balances never clear - the payments do not cover the " +
                    "interest. Raise a minimum or add something extra each month.",
                style = MaterialTheme.typography.bodyMedium,
                color = Negative
            )
            return@SectionCard
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StrategyTile(comparison.snowball, currency, Modifier.weight(1f))
            StrategyTile(comparison.avalanche, currency, Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        Text(
            when {
                comparison.strategiesAgree ->
                    "Both strategies clear your debts in the same order here, so pick whichever " +
                        "you find easier to stick to."
                comparison.interestSavedMinor > 0 ->
                    "Avalanche saves ${Money.format(comparison.interestSavedMinor, currency)} in " +
                        "interest" +
                        (if (comparison.monthsSaved > 0) " and finishes ${comparison.monthsSaved} " +
                            "month${if (comparison.monthsSaved == 1) "" else "s"} sooner" else "") +
                        ". Snowball clears a balance sooner, which some people find easier to keep up."
                else ->
                    "The two work out about the same here - pick whichever you find easier to " +
                        "stick to."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (comparison.avalanche.order.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Avalanche order",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            comparison.avalanche.order.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${index + 1}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        step.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        monthsLabel(step.clearedInMonth),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StrategyTile(result: StrategyResult, currency: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .padding(12.dp)
    ) {
        Text(result.strategy.label, style = MaterialTheme.typography.titleSmall)
        Text(
            result.strategy.explanation,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(monthsLabel(result.months), style = MaterialTheme.typography.bodyLarge)
        Text(
            "${Money.format(result.totalInterestMinor, currency)} interest",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "18 months" reads worse than "1 yr 6 mo" once a plan runs past a year. */
private fun monthsLabel(months: Int): String = when {
    months <= 0 -> "-"
    months < 12 -> "$months mo"
    months % 12 == 0 -> "${months / 12} yr"
    else -> "${months / 12} yr ${months % 12} mo"
}

@Composable
private fun DebtCard(
    debt: Debt,
    projection: PayoffProjection?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPay: () -> Unit
) {
    SectionCard(modifier = Modifier.clickable { onEdit() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(debt.colorArgb, size = 11)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    debt.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        if (debt.annualRatePercent > 0) append("%.2f%% APR".format(debt.annualRatePercent))
                        else append("Interest free")
                        if (debt.minimumPaymentMinor > 0) {
                            append(" · min ")
                            append(Money.format(debt.minimumPaymentMinor, debt.currencyCode))
                        }
                        if (!debt.isActive) append(" · cleared")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                Money.format(debt.balanceMinor, debt.currencyCode),
                style = MaterialTheme.typography.titleSmall,
                color = if (debt.kind == DebtKind.OWED_TO_ME) Positive else Negative
            )
        }

        projection?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                if (it.neverClears) {
                    "The minimum does not cover the interest, so this balance never falls."
                } else {
                    "On the minimum alone: ${monthsLabel(it.months)}, " +
                        "${Money.format(it.totalInterestMinor, debt.currencyCode)} interest"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (it.neverClears) Negative else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (debt.balanceMinor > 0) {
                TextButton(onClick = onPay) { Text("Record payment") }
            }
            TextButton(onClick = onDelete) { Text("Delete", color = Negative) }
        }
    }
}

@Composable
private fun PaymentDialog(debt: Debt, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var amount by remember {
        mutableStateOf(
            if (debt.minimumPaymentMinor > 0) Money.editString(debt.minimumPaymentMinor, debt.currencyCode)
            else ""
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Payment to ${debt.name}") },
        text = {
            Column {
                Text(
                    "This reduces the tracked balance. It does not create a transaction - record " +
                        "that separately if the money left one of your accounts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (${debt.currencyCode})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(amount) }, enabled = amount.isNotBlank()) {
                Text("Record")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DebtEditorDialog(
    existing: Debt?,
    baseCurrency: String,
    onSave: (String, DebtKind, String, String, String, String, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var kind by remember { mutableStateOf(existing?.kind ?: DebtKind.OWED_BY_ME) }
    var currency by remember { mutableStateOf(existing?.currencyCode ?: baseCurrency) }
    var balance by remember {
        mutableStateOf(existing?.let { Money.editString(it.balanceMinor, it.currencyCode) } ?: "")
    }
    var rate by remember {
        mutableStateOf(existing?.annualRatePercent?.takeIf { it > 0 }?.toString() ?: "")
    }
    var minimum by remember {
        mutableStateOf(
            existing?.minimumPaymentMinor?.takeIf { it > 0 }
                ?.let { Money.editString(it, existing.currencyCode) } ?: ""
        )
    }
    var colour by remember { mutableStateOf(existing?.colorArgb ?: PALETTE[3]) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var showKindPicker by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New debt" else "Edit debt") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Visa card") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                PickerRow("Direction", kind.label) { showKindPicker = true }
                PickerRow("Currency", currency) { showCurrencyPicker = true }

                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = { Text("Balance ($currency)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (kind == DebtKind.OWED_BY_ME) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rate,
                        onValueChange = { rate = it },
                        label = { Text("Annual rate %") },
                        placeholder = { Text("19.99") },
                        supportingText = { Text("Leave blank if interest free") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = minimum,
                        onValueChange = { minimum = it },
                        label = { Text("Minimum payment ($currency)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
                onClick = { onSave(name, kind, balance, currency, rate, minimum, colour, note) },
                enabled = name.isNotBlank() && balance.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showKindPicker) {
        OptionPickerDialog(
            title = "Direction",
            options = DebtKind.entries,
            selected = kind,
            label = { it.label },
            onSelect = { kind = it; showKindPicker = false },
            onDismiss = { showKindPicker = false }
        )
    }

    if (showCurrencyPicker) {
        OptionPickerDialog(
            title = "Currency",
            options = Money.COMMON_CODES,
            selected = currency,
            label = { "$it  ${Money.symbol(it)}" },
            onSelect = { currency = it; showCurrencyPicker = false },
            onDismiss = { showCurrencyPicker = false }
        )
    }
}
