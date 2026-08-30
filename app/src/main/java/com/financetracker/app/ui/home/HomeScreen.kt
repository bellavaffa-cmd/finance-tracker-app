package com.financetracker.app.ui.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.Money
import com.financetracker.app.data.account.AccountWithBalance
import com.financetracker.app.data.budget.BudgetProgress
import com.financetracker.app.data.recurring.RecurringRuleDetail
import com.financetracker.app.ui.common.CategoryDot
import com.financetracker.app.ui.common.EmptyState
import com.financetracker.app.ui.common.ProgressBar
import com.financetracker.app.ui.common.SectionCard
import com.financetracker.app.ui.common.StatTile
import com.financetracker.app.ui.common.TransactionRow
import com.financetracker.app.ui.common.amountColor
import com.financetracker.app.ui.common.relativeDueLabel
import com.financetracker.app.ui.theme.Accent
import com.financetracker.app.ui.theme.Negative
import com.financetracker.app.ui.theme.Positive
import com.financetracker.app.ui.theme.Surface2
import com.financetracker.app.ui.theme.Warn

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenTransactions: () -> Unit,
    onEditTransaction: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            NetWorthCard(
                state = state,
                onToggleHide = { viewModel.setHideBalances(!state.hideBalances) },
                onOpenSettings = onOpenSettings
            )
        }

        if (state.dueRules.isNotEmpty()) {
            item {
                DueRulesCard(
                    rules = state.dueRules,
                    onConfirm = viewModel::confirmRule,
                    onSkip = viewModel::skipRule
                )
            }
        }

        item {
            SectionCard(
                title = "Accounts",
                trailing = { TextButton(onClick = onOpenAccounts) { Text("Manage") } }
            ) {
                if (state.accounts.isEmpty()) {
                    EmptyState("No accounts yet", "Add one to start recording transactions.")
                } else {
                    state.accounts.forEach { account ->
                        AccountRow(account, hidden = state.hideBalances)
                    }
                }
            }
        }

        if (state.budgets.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Budgets · ${state.period.label}",
                    trailing = { TextButton(onClick = onOpenBudgets) { Text("All") } }
                ) {
                    state.budgets.take(4).forEach { progress ->
                        BudgetMiniRow(progress, state.baseCurrency, state.hideBalances)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Recent",
                trailing = { TextButton(onClick = onOpenTransactions) { Text("See all") } }
            ) {
                if (state.recent.isEmpty()) {
                    EmptyState(
                        "Nothing recorded yet",
                        "Tap the + button to add your first transaction."
                    )
                } else {
                    state.recent.forEach { detail ->
                        TransactionRow(
                            detail = detail,
                            showDate = true,
                            onClick = { onEditTransaction(detail.id) }
                        )
                    }
                }
            }
        }

        if (state.monthlyCommitmentMinor > 0) {
            item {
                SectionCard(title = "Committed each month") {
                    Text(
                        Money.format(state.monthlyCommitmentMinor, state.baseCurrency),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Recurring expenses, normalised to a monthly figure.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun NetWorthCard(
    state: HomeUiState,
    onToggleHide: () -> Unit,
    onOpenSettings: () -> Unit
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    "Net worth",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.hideBalances) HIDDEN
                    else Money.format(state.netWorthMinor, state.baseCurrency),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (state.netWorthMinor < 0) Negative
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            Row {
                IconButton(onClick = onToggleHide) {
                    Icon(
                        if (state.hideBalances) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = "Show or hide balances"
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "In",
                value = if (state.hideBalances) HIDDEN else Money.format(state.incomeMinor, state.baseCurrency),
                valueColor = Positive,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Out",
                value = if (state.hideBalances) HIDDEN else Money.format(state.expenseMinor, state.baseCurrency),
                valueColor = Negative,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Net",
                value = if (state.hideBalances) HIDDEN
                else Money.format(state.netMinor, state.baseCurrency, withSign = true),
                valueColor = amountColor(state.netMinor >= 0),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            state.period.rangeLabel?.let { "${state.period.label} · $it" } ?: state.period.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Rules that post only on confirmation surface here rather than in a notification, so the decision
 * happens where the numbers it affects are already visible.
 */
@Composable
private fun DueRulesCard(
    rules: List<RecurringRuleDetail>,
    onConfirm: (Long) -> Unit,
    onSkip: (Long) -> Unit
) {
    SectionCard(title = "Waiting for confirmation") {
        rules.forEach { rule ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(rule.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${Money.format(rule.amountMinor, rule.accountCurrency)} · ${relativeDueLabel(rule.nextDueMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Warn
                    )
                }
                TextButton(onClick = { onSkip(rule.id) }) { Text("Skip") }
                TextButton(onClick = { onConfirm(rule.id) }) { Text("Add") }
            }
        }
    }
}

@Composable
private fun AccountRow(account: AccountWithBalance, hidden: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(account.colorArgb).copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            CategoryDot(account.colorArgb, size = 10)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                account.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${account.type.label} · ${account.currencyCode}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            if (hidden) HIDDEN else Money.format(account.balanceMinor, account.currencyCode),
            style = MaterialTheme.typography.bodyLarge,
            color = if (account.balanceMinor < 0) Negative else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BudgetMiniRow(progress: BudgetProgress, baseCurrency: String, hidden: Boolean) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                progress.categoryName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (hidden) HIDDEN else remainingLabel(progress, baseCurrency),
                style = MaterialTheme.typography.labelMedium,
                color = if (progress.isOverBudget) Negative else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(
            fraction = progress.fraction,
            color = budgetColor(progress)
        )
    }
}

private fun remainingLabel(progress: BudgetProgress, baseCurrency: String): String =
    if (progress.isOverBudget) "${Money.format(-progress.remainingMinorBase, baseCurrency)} over"
    else "${Money.format(progress.remainingMinorBase, baseCurrency)} left"

/** Amber once four-fifths of the allowance is gone, so the warning arrives before the overspend. */
internal fun budgetColor(progress: BudgetProgress): Color = when {
    progress.isOverBudget -> Negative
    progress.fraction >= 0.8f -> Warn
    else -> Accent
}

internal const val HIDDEN = "••••"

/** Small filled circle used as the add button's backdrop on screens without a Scaffold FAB. */
@Composable
internal fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Add transaction")
    }
}
