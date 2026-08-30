package com.financetracker.app.data.txn

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TxnType(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer")
}

/**
 * One ledger entry. The table is named `txn` because `transaction` is a SQLite keyword.
 *
 * Amounts are always **positive**; direction comes from [type]. A TRANSFER debits [accountId] by
 * [amountMinor] and credits [toAccountId] by [toAmountMinor] - two separate figures, so a
 * EUR -> USD move records exactly what left and exactly what landed instead of re-deriving the
 * second from a rate that will have moved by the time you look at it again.
 *
 * [fxRateToBase] is captured at entry time and never recalculated, so last year's reports do not
 * shift when this year's exchange rate does.
 */
@Entity(
    tableName = "txn",
    indices = [
        Index("dateMillis"), Index("accountId"), Index("toAccountId"),
        Index("categoryId"), Index("recurringRuleId")
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxnType,
    val dateMillis: Long,
    /** Source account for EXPENSE/TRANSFER, destination account for INCOME. */
    val accountId: Long,
    /** Destination account, TRANSFER only. */
    val toAccountId: Long? = null,
    /** Null for transfers, and for entries whose category was later deleted. */
    val categoryId: Long? = null,
    val amountMinor: Long,
    /** Amount credited to [toAccountId] in *its* currency. TRANSFER only. */
    val toAmountMinor: Long? = null,
    /** Major units of [accountId]'s currency -> major units of base currency, frozen at entry. */
    val fxRateToBase: Double = 1.0,
    val payee: String = "",
    val note: String = "",
    /** Set when this entry was posted from a recurring rule. */
    val recurringRuleId: Long? = null,
    val createdAtMillis: Long,
    /** Soft delete: rows stay so historical reports remain reproducible. */
    val deletedAtMillis: Long? = null
)

/** A transaction joined with the display fields the list needs, resolved in SQL. */
data class TransactionDetail(
    val id: Long,
    val type: TxnType,
    val dateMillis: Long,
    val amountMinor: Long,
    val toAmountMinor: Long?,
    val fxRateToBase: Double,
    val payee: String,
    val note: String,
    val accountId: Long,
    val accountName: String,
    val accountCurrency: String,
    val toAccountId: Long?,
    val toAccountName: String?,
    val toAccountCurrency: String?,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryColorArgb: Int?,
    val parentCategoryName: String?,
    val recurringRuleId: Long?
)

/**
 * A raw amount row still denominated in its own account's currency, carrying the rate needed to
 * convert it. Aggregation happens in Kotlin rather than SQL because SUM() across mixed currencies
 * would be meaningless.
 */
data class AmountRow(
    val categoryId: Long?,
    val categoryName: String?,
    val colorArgb: Int?,
    val amountMinor: Long,
    val currencyCode: String,
    val fxRateToBase: Double
)

/** An [AmountRow] carrying its date, so a long window can be bucketed into months in one query. */
data class DatedAmountRow(
    val categoryId: Long?,
    val categoryName: String?,
    val colorArgb: Int?,
    val amountMinor: Long,
    val currencyCode: String,
    val fxRateToBase: Double,
    val dateMillis: Long
)

/**
 * One movement of one account's balance. A transfer produces two of these - a debit on the source
 * and a credit on the destination - so replaying them in date order reconstructs what any account
 * held at any past moment without needing stored snapshots.
 */
data class BalanceEffect(
    val accountId: Long,
    val currencyCode: String,
    val dateMillis: Long,
    val deltaMinor: Long
)
