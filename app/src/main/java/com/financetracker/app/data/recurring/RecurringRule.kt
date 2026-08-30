package com.financetracker.app.data.recurring

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.financetracker.app.data.txn.TxnType

enum class Frequency(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

/**
 * A template that posts a transaction on a schedule - salary, rent, subscriptions.
 *
 * [nextDueMillis] is the single source of truth for "when next", advanced only after a posting
 * actually succeeds, so a rule can never silently skip an occurrence because the app was closed.
 */
@Entity(
    tableName = "recurring_rule",
    indices = [Index("nextDueMillis"), Index("accountId")]
)
data class RecurringRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TxnType,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val amountMinor: Long,
    val toAmountMinor: Long? = null,
    val payee: String = "",
    val note: String = "",
    val frequency: Frequency,
    /** Every N periods: 2 + WEEKLY is fortnightly. */
    val interval: Int = 1,
    /**
     * Day of month the rule anchors to, kept separately from [nextDueMillis] so a rule due on the
     * 31st lands on the 30th in April and then returns to the 31st in May, instead of permanently
     * drifting to the 30th the way naive "add one month" arithmetic does.
     */
    val anchorDayOfMonth: Int? = null,
    val nextDueMillis: Long,
    val endDateMillis: Long? = null,
    /** When false the rule waits for you to confirm each occurrence instead of posting itself. */
    val autoPost: Boolean = true,
    val isActive: Boolean = true,
    val lastPostedMillis: Long? = null
)

/** A rule joined with the names needed to display it without a second query. */
data class RecurringRuleDetail(
    val id: Long,
    val name: String,
    val type: TxnType,
    val accountId: Long,
    val accountName: String,
    val accountCurrency: String,
    val toAccountId: Long?,
    val toAccountName: String?,
    val categoryId: Long?,
    val categoryName: String?,
    val colorArgb: Int?,
    val amountMinor: Long,
    val toAmountMinor: Long?,
    val payee: String,
    val note: String,
    val frequency: Frequency,
    val interval: Int,
    val anchorDayOfMonth: Int?,
    val nextDueMillis: Long,
    val endDateMillis: Long?,
    val autoPost: Boolean,
    val isActive: Boolean
) {
    fun toRule(lastPostedMillis: Long? = null) = RecurringRule(
        id = id,
        name = name,
        type = type,
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        amountMinor = amountMinor,
        toAmountMinor = toAmountMinor,
        payee = payee,
        note = note,
        frequency = frequency,
        interval = interval,
        anchorDayOfMonth = anchorDayOfMonth,
        nextDueMillis = nextDueMillis,
        endDateMillis = endDateMillis,
        autoPost = autoPost,
        isActive = isActive,
        lastPostedMillis = lastPostedMillis
    )
}
