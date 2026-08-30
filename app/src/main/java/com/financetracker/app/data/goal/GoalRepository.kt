package com.financetracker.app.data.goal

import com.financetracker.app.data.account.AccountWithBalance
import kotlinx.coroutines.flow.Flow

class GoalRepository(private val dao: GoalDao) {

    val allGoals: Flow<List<Goal>> = dao.observeAll()

    suspend fun all(): List<Goal> = dao.all()

    suspend fun byId(id: Long): Goal? = dao.byId(id)

    suspend fun insert(goal: Goal): Long = dao.insert(goal)

    suspend fun update(goal: Goal) = dao.update(goal)

    suspend fun delete(goal: Goal) = dao.delete(goal)

    suspend fun setArchived(id: Long, archived: Boolean) {
        dao.byId(id)?.let { dao.update(it.copy(isArchived = archived)) }
    }

    companion object {
        /**
         * Resolves goals against live account balances. A goal whose account has been deleted is
         * dropped rather than shown against a zero balance, which would read as "no progress" when
         * the truth is that the goal no longer has anywhere to point.
         */
        fun progressFor(
            goals: List<Goal>,
            accounts: List<AccountWithBalance>
        ): List<GoalProgress> {
            val byId = accounts.associateBy { it.id }
            return goals.mapNotNull { goal ->
                val account = byId[goal.accountId] ?: return@mapNotNull null
                GoalProgress(
                    goal = goal,
                    accountName = account.name,
                    currencyCode = account.currencyCode,
                    accountBalanceMinor = account.balanceMinor
                )
            }
                // Unfinished goals first, then the ones closest to done - the ones worth a nudge.
                .sortedWith(compareBy({ it.isComplete }, { -it.fraction }))
        }
    }
}
