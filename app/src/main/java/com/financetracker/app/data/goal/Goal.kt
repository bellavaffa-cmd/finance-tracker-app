package com.financetracker.app.data.goal

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * A savings target attached to an account.
 *
 * Progress is the linked account's balance rather than a number kept alongside it, so it can never
 * drift from reality: money moved into the account counts immediately, and money taken back out
 * stops counting. That is the whole reason a goal points at an account instead of tracking its own
 * running total.
 *
 * [startingBalanceMinor] is what the account already held when the goal was set, so a goal started
 * on an account with money in it does not begin life falsely near completion.
 */
@Entity(
    tableName = "goal",
    indices = [Index("accountId"), Index("isArchived")]
)
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val accountId: Long,
    val targetMinor: Long,
    val startingBalanceMinor: Long = 0,
    /** Optional deadline; without one the goal simply has no monthly figure to report. */
    val targetDateMillis: Long? = null,
    val colorArgb: Int,
    val note: String = "",
    val isArchived: Boolean = false,
    val createdAtMillis: Long
)

/** A goal resolved against its account's current balance. */
data class GoalProgress(
    val goal: Goal,
    val accountName: String,
    val currencyCode: String,
    val accountBalanceMinor: Long
) {
    /** Never negative: withdrawing below the starting point means no progress, not anti-progress. */
    val savedMinor: Long
        get() = (accountBalanceMinor - goal.startingBalanceMinor).coerceAtLeast(0)

    val remainingMinor: Long get() = (goal.targetMinor - savedMinor).coerceAtLeast(0)

    val isComplete: Boolean get() = savedMinor >= goal.targetMinor

    val fraction: Float
        get() = if (goal.targetMinor <= 0) 1f
        else (savedMinor.toFloat() / goal.targetMinor.toFloat()).coerceIn(0f, 1f)

    /** Whole months left, counting the current one. Null when the goal has no deadline. */
    val monthsRemaining: Long?
        get() = goal.targetDateMillis?.let { millis ->
            val target = java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            ChronoUnit.MONTHS.between(LocalDate.now().withDayOfMonth(1), target.withDayOfMonth(1))
                .coerceAtLeast(0)
        }

    /**
     * What must go in each month to arrive on time. Null when there is no deadline or the goal is
     * already met; when the deadline is this month or past, the answer is simply everything left.
     */
    val requiredPerMonthMinor: Long?
        get() {
            if (isComplete) return null
            val months = monthsRemaining ?: return null
            return if (months <= 0) remainingMinor else remainingMinor / months
        }

    val isOverdue: Boolean
        get() = !isComplete && (monthsRemaining?.let { it <= 0L } ?: false)
}
