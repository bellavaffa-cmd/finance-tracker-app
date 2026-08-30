package com.financetracker.app.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.FinanceApplication
import com.financetracker.app.data.Money
import com.financetracker.app.data.account.AccountRepository
import com.financetracker.app.data.account.AccountWithBalance
import com.financetracker.app.data.goal.Goal
import com.financetracker.app.data.goal.GoalProgress
import com.financetracker.app.data.goal.GoalRepository
import com.financetracker.app.data.settings.SettingsRepository
import com.financetracker.app.ui.common.FinanceViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalsUiState(
    val goals: List<GoalProgress> = emptyList(),
    val accounts: List<AccountWithBalance> = emptyList(),
    val baseCurrency: String = SettingsRepository.DEFAULT_BASE_CURRENCY,
    val loading: Boolean = true
) {
    val activeCount: Int get() = goals.count { !it.goal.isArchived && !it.isComplete }
    val completedCount: Int get() = goals.count { it.isComplete }
}

class GoalsViewModel(
    private val goals: GoalRepository,
    private val accounts: AccountRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<GoalsUiState> = combine(
        goals.allGoals,
        accounts.accountsWithBalances,
        settings.baseCurrency
    ) { goalList, accountList, baseCurrency ->
        GoalsUiState(
            goals = GoalRepository.progressFor(goalList.filter { !it.isArchived }, accountList),
            accounts = accountList.filter { !it.isArchived },
            baseCurrency = baseCurrency,
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    /**
     * Creating a goal snapshots the account's current balance, so an account that already holds
     * money does not make a brand-new goal look half finished.
     */
    fun saveGoal(
        existing: Goal?,
        name: String,
        accountId: Long,
        targetInput: String,
        targetDateMillis: Long?,
        colorArgb: Int,
        note: String
    ) = viewModelScope.launch {
        val account = uiState.value.accounts.firstOrNull { it.id == accountId } ?: return@launch
        val target = Money.parseToMinor(targetInput, account.currencyCode) ?: return@launch
        if (name.isBlank() || target <= 0) return@launch

        if (existing == null) {
            goals.insert(
                Goal(
                    name = name.trim(),
                    accountId = accountId,
                    targetMinor = target,
                    startingBalanceMinor = account.balanceMinor,
                    targetDateMillis = targetDateMillis,
                    colorArgb = colorArgb,
                    note = note.trim(),
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        } else {
            // Editing keeps the original starting balance: re-snapshotting it would wipe out
            // progress already made just because the name or target was tweaked.
            goals.update(
                existing.copy(
                    name = name.trim(),
                    accountId = accountId,
                    targetMinor = target,
                    targetDateMillis = targetDateMillis,
                    colorArgb = colorArgb,
                    note = note.trim(),
                    startingBalanceMinor = if (existing.accountId == accountId) {
                        existing.startingBalanceMinor
                    } else {
                        // Pointing a goal at a different account has to re-baseline, or the new
                        // account's existing money would be counted as progress.
                        account.balanceMinor
                    }
                )
            )
        }
    }

    fun delete(goal: Goal) = viewModelScope.launch { goals.delete(goal) }

    fun setArchived(goal: Goal, archived: Boolean) = viewModelScope.launch {
        goals.setArchived(goal.id, archived)
    }

    companion object {
        fun factory(app: FinanceApplication) = FinanceViewModelFactory(app) {
            GoalsViewModel(
                goals = it.goalRepository,
                accounts = it.accountRepository,
                settings = it.settingsRepository
            )
        }
    }
}
