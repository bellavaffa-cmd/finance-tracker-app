package com.financetracker.app.data.debt

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DebtKind(val label: String) {
    /** Money you owe: loans, cards, anything being paid down. */
    OWED_BY_ME("I owe"),

    /** Money owed to you. Tracked, but never included in payoff strategies. */
    OWED_TO_ME("Owed to me")
}

/**
 * A debt tracked outside the account balances.
 *
 * Deliberately separate from [com.financetracker.app.data.account.Account]: an account records what
 * a balance *is*, while a debt records the terms it runs on - rate, minimum payment, and how much
 * of the original principal is left. A credit card can exist as both, and that is fine; the debt
 * row is the thing that answers "when will this be gone".
 *
 * [balanceMinor] is maintained by hand rather than derived from transactions, because payments to a
 * debt are usually transfers whose split between interest and principal the app cannot see.
 */
@Entity(
    tableName = "debt",
    indices = [Index("isActive")]
)
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: DebtKind = DebtKind.OWED_BY_ME,
    val balanceMinor: Long,
    val currencyCode: String,
    /** Nominal annual rate as a percentage: 19.99 means 19.99% APR. Zero for interest-free. */
    val annualRatePercent: Double = 0.0,
    val minimumPaymentMinor: Long = 0,
    val colorArgb: Int,
    val note: String = "",
    val isActive: Boolean = true,
    val createdAtMillis: Long
) {
    fun toSnapshot() = DebtSnapshot(
        id = id,
        name = name,
        balanceMinor = balanceMinor,
        annualRatePercent = annualRatePercent,
        minimumPaymentMinor = minimumPaymentMinor
    )
}
