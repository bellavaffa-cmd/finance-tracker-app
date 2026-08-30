package com.financetracker.app.ui.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.FinanceApplication
import com.financetracker.app.data.Money
import com.financetracker.app.data.account.AccountRepository
import com.financetracker.app.data.debt.Debt
import com.financetracker.app.data.debt.DebtCalculator
import com.financetracker.app.data.debt.DebtKind
import com.financetracker.app.data.debt.DebtRepository
import com.financetracker.app.data.debt.PayoffProjection
import com.financetracker.app.data.debt.StrategyComparison
import com.financetracker.app.data.settings.SettingsRepository
import com.financetracker.app.ui.common.FinanceViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DebtsUiState(
    val debts: List<Debt> = emptyList(),
    val baseCurrency: String = SettingsRepository.DEFAULT_BASE_CURRENCY,
    val extraMonthlyInput: String = "",
    val comparison: StrategyComparison? = null,
    /** Per-debt projection on its minimum payment alone, keyed by debt id. */
    val minimumOnlyProjections: Map<Long, PayoffProjection> = emptyMap(),
    val netPositionMinorBase: Long = 0,
    val loading: Boolean = true
) {
    val owed: List<Debt> get() = debts.filter { it.kind == DebtKind.OWED_BY_ME }
    val owedToMe: List<Debt> get() = debts.filter { it.kind == DebtKind.OWED_TO_ME }

    val payable: List<Debt>
        get() = owed.filter { it.isActive && it.balanceMinor > 0 }

    /**
     * Comparing debts across currencies would mean adding pounds to euros, so the strategy section
     * is only offered when everything payable shares one currency.
     */
    val payableCurrency: String? get() = payable.map { it.currencyCode }.distinct().singleOrNull()

    val mixedCurrencies: Boolean get() = payable.isNotEmpty() && payableCurrency == null

    val totalOwedMinor: Long get() = payable.sumOf { it.balanceMinor }

    val totalMinimumsMinor: Long get() = payable.sumOf { it.minimumPaymentMinor }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DebtsViewModel(
    private val debts: DebtRepository,
    private val accounts: AccountRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val extraMonthly = MutableStateFlow("")

    val uiState: StateFlow<DebtsUiState> = combine(
        debts.allDebts,
        accounts.rates,
        settings.baseCurrency,
        extraMonthly
    ) { debtList, rateList, baseCurrency, extraInput ->
        val payable = debtList.filter {
            it.isActive && it.kind == DebtKind.OWED_BY_ME && it.balanceMinor > 0
        }
        val currency = payable.map { it.currencyCode }.distinct().singleOrNull()
        val extra = currency?.let { Money.parseToMinor(extraInput, it) } ?: 0L

        DebtsUiState(
            debts = debtList,
            baseCurrency = baseCurrency,
            extraMonthlyInput = extraInput,
            comparison = if (currency != null && payable.isNotEmpty()) {
                DebtRepository.compare(payable, extra.coerceAtLeast(0))
            } else null,
            minimumOnlyProjections = payable.associate { debt ->
                debt.id to DebtCalculator.project(
                    balanceMinor = debt.balanceMinor,
                    annualRatePercent = debt.annualRatePercent,
                    monthlyPaymentMinor = debt.minimumPaymentMinor
                )
            },
            netPositionMinorBase = DebtRepository.netPositionMinorBase(
                debtList,
                rateList.associate { it.code to it.rateToBase },
                baseCurrency
            ),
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtsUiState())

    fun setExtraMonthly(input: String) { extraMonthly.value = input }

    fun saveDebt(
        existing: Debt?,
        name: String,
        kind: DebtKind,
        balanceInput: String,
        currencyCode: String,
        rateInput: String,
        minimumInput: String,
        colorArgb: Int,
        note: String
    ) = viewModelScope.launch {
        val balance = Money.parseToMinor(balanceInput, currencyCode) ?: return@launch
        if (name.isBlank() || balance < 0) return@launch
        val rate = rateInput.trim().replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val minimum = Money.parseToMinor(minimumInput.ifBlank { "0" }, currencyCode) ?: 0L

        if (existing == null) {
            debts.insert(
                Debt(
                    name = name.trim(),
                    kind = kind,
                    balanceMinor = balance,
                    currencyCode = currencyCode,
                    annualRatePercent = rate,
                    minimumPaymentMinor = minimum,
                    colorArgb = colorArgb,
                    note = note.trim(),
                    isActive = balance > 0,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        } else {
            debts.update(
                existing.copy(
                    name = name.trim(),
                    kind = kind,
                    balanceMinor = balance,
                    currencyCode = currencyCode,
                    annualRatePercent = rate,
                    minimumPaymentMinor = minimum,
                    colorArgb = colorArgb,
                    note = note.trim(),
                    isActive = balance > 0
                )
            )
        }
    }

    /** Records a payment against a debt, closing it automatically when it reaches zero. */
    fun recordPayment(debt: Debt, amountInput: String) = viewModelScope.launch {
        val amount = Money.parseToMinor(amountInput, debt.currencyCode) ?: return@launch
        if (amount <= 0) return@launch
        debts.applyPayment(debt.id, amount)
    }

    fun delete(debt: Debt) = viewModelScope.launch { debts.delete(debt) }

    companion object {
        fun factory(app: FinanceApplication) = FinanceViewModelFactory(app) {
            DebtsViewModel(
                debts = it.debtRepository,
                accounts = it.accountRepository,
                settings = it.settingsRepository
            )
        }
    }
}
