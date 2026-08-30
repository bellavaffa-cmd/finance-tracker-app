package com.financetracker.app.data.account

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AccountType(val label: String) {
    CASH("Cash"),
    BANK("Bank"),
    CARD("Credit card"),
    SAVINGS("Savings"),
    INVESTMENT("Investment");

    /** Credit cards normally carry a debt, so their opening balance defaults to negative-friendly. */
    val isLiability: Boolean get() = this == CARD
}

@Entity(tableName = "account")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val currencyCode: String,
    /** Signed: the balance the account held before the first transaction was recorded. */
    val openingBalanceMinor: Long = 0,
    val colorArgb: Int,
    val includeInNetWorth: Boolean = true,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0
)

/** An account joined with its computed running balance. */
data class AccountWithBalance(
    val id: Long,
    val name: String,
    val type: AccountType,
    val currencyCode: String,
    val colorArgb: Int,
    val includeInNetWorth: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int,
    val balanceMinor: Long
)
