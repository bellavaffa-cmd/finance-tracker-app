package com.financetracker.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AccountType(val label: String) {
    CASH("Cash"), BANK("Bank"), CARD("Credit card"), SAVINGS("Savings")
}

enum class CategoryKind { EXPENSE, INCOME }

enum class TxnType(val label: String) {
    EXPENSE("Expense"), INCOME("Income"), TRANSFER("Transfer")
}

/** All money fields are a count of minor units (cents), never a floating-point value. */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val openingBalance: Long = 0,
    val archived: Boolean = false,
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    /** ARGB. */
    val color: Int,
)

/**
 * One ledger entry. [amount] is always positive; [type] says which way it moves.
 * A transfer moves [amount] from [accountId] to [toAccountId] and has no category.
 */
@Entity(
    tableName = "transactions",
    indices = [Index("date"), Index("accountId"), Index("toAccountId"), Index("categoryId")],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxnType,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    /** Days since 1970-01-01 (LocalDate.toEpochDay). */
    val date: Long,
    val payee: String = "",
    val note: String = "",
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val categoryId: Long,
    val monthlyLimit: Long,
)
