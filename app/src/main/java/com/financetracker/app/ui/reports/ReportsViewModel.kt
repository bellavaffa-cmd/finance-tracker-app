package com.financetracker.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.FinanceApplication
import com.financetracker.app.data.Money
import com.financetracker.app.data.MonthPeriod
import com.financetracker.app.data.settings.SettingsRepository
import com.financetracker.app.data.txn.TransactionRepository
import com.financetracker.app.data.txn.TxnType
import com.financetracker.app.ui.common.FinanceViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** One row of the breakdown beneath the donut. */
data class CategoryBreakdown(
    val categoryId: Long?,
    val name: String,
    val colorArgb: Int,
    val amountMinorBase: Long,
    val fraction: Float,
    val transactionCount: Int
)

data class ReportsUiState(
    val period: MonthPeriod = MonthPeriod.current(1),
    val baseCurrency: String = SettingsRepository.DEFAULT_BASE_CURRENCY,
    val direction: TxnType = TxnType.EXPENSE,
    val breakdown: List<CategoryBreakdown> = emptyList(),
    val totalMinorBase: Long = 0,
    val comparisonMinorBase: Long = 0,
    val loading: Boolean = true
) {
    /** Signed change against the previous month, as a percentage. Null when there is nothing to compare. */
    val changePercent: Int?
        get() = if (comparisonMinorBase <= 0) null
        else Math.round((totalMinorBase - comparisonMinorBase) * 100.0 / comparisonMinorBase).toInt()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    private val settings: SettingsRepository,
    private val transactions: TransactionRepository
) : ViewModel() {

    private val monthOffset = MutableStateFlow(0L)
    private val direction = MutableStateFlow(TxnType.EXPENSE)

    val uiState: StateFlow<ReportsUiState> = combine(
        settings.baseCurrency,
        settings.monthStartDay,
        monthOffset,
        direction
    ) { base, startDay, offset, dir -> Params(base, startDay, offset, dir) }
        .flatMapLatest { params ->
            val period = MonthPeriod.current(params.startDay).plusMonths(params.offset)
            val previous = period.plusMonths(-1)

            combine(
                transactions.amountRows(params.direction, period.startMillis, period.endMillisExclusive),
                transactions.amountRows(params.direction, previous.startMillis, previous.endMillisExclusive)
            ) { rows, previousRows ->
                val total = TransactionRepository.sumToBase(rows, params.baseCurrency)

                // Rows arrive grouped to their top-level category by SQL, so subcategory spending
                // rolls up into the parent slice instead of shattering the chart into 40 slivers.
                val breakdown = rows
                    .groupBy { it.categoryId to it.categoryName }
                    .map { (key, group) ->
                        val amount = TransactionRepository.sumToBase(group, params.baseCurrency)
                        CategoryBreakdown(
                            categoryId = key.first,
                            name = key.second ?: "Uncategorised",
                            colorArgb = group.firstOrNull()?.colorArgb ?: UNCATEGORISED_COLOR,
                            amountMinorBase = amount,
                            fraction = if (total <= 0) 0f else amount.toFloat() / total.toFloat(),
                            transactionCount = group.size
                        )
                    }
                    .sortedByDescending { it.amountMinorBase }

                ReportsUiState(
                    period = period,
                    baseCurrency = params.baseCurrency,
                    direction = params.direction,
                    breakdown = breakdown,
                    totalMinorBase = total,
                    comparisonMinorBase = TransactionRepository.sumToBase(previousRows, params.baseCurrency),
                    loading = false
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    fun previousMonth() { monthOffset.value -= 1 }
    fun nextMonth() { monthOffset.value += 1 }
    fun setDirection(type: TxnType) { direction.value = type }

    /** Compact centre label for the donut, which has room for roughly six characters. */
    fun centerLabel(state: ReportsUiState): String =
        Money.formatCompact(state.totalMinorBase, state.baseCurrency)

    private data class Params(
        val baseCurrency: String,
        val startDay: Int,
        val offset: Long,
        val direction: TxnType
    )

    companion object {
        const val UNCATEGORISED_COLOR = 0xFF6B7688.toInt()

        fun factory(app: FinanceApplication) = FinanceViewModelFactory(app) {
            ReportsViewModel(
                settings = it.settingsRepository,
                transactions = it.transactionRepository
            )
        }
    }
}
