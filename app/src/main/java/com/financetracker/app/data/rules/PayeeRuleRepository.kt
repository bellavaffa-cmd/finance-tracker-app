package com.financetracker.app.data.rules

import androidx.room.withTransaction
import com.financetracker.app.data.AppDatabase
import kotlinx.coroutines.flow.Flow

/** Outcome of running the rules over entries already in the ledger. */
data class BackfillResult(val examined: Int, val categorised: Int)

class PayeeRuleRepository(
    private val database: AppDatabase,
    private val dao: PayeeRuleDao
) {
    val allRules: Flow<List<PayeeRule>> = dao.observeAll()

    suspend fun active(): List<PayeeRule> = dao.active()

    suspend fun all(): List<PayeeRule> = dao.all()

    suspend fun save(rule: PayeeRule): Long =
        if (rule.id == 0L) {
            dao.insert(rule.copy(priority = dao.highestPriority() + 1))
        } else {
            dao.update(rule)
            rule.id
        }

    suspend fun delete(rule: PayeeRule) = dao.delete(rule)

    suspend fun setActive(rule: PayeeRule, active: Boolean) = dao.update(rule.copy(isActive = active))

    /** Moves a rule earlier or later in the order rules are tested. */
    suspend fun move(rule: PayeeRule, up: Boolean) {
        val ordered = PayeeRuleEngine.sort(dao.all())
        val index = ordered.indexOfFirst { it.id == rule.id }
        if (index < 0) return
        val target = if (up) index - 1 else index + 1
        if (target !in ordered.indices) return

        // Priorities are rewritten as a dense sequence rather than swapped, so ties introduced by
        // older rules cannot make the order look arbitrary after a few moves.
        val reordered = ordered.toMutableList().apply {
            val moved = removeAt(index)
            add(target, moved)
        }
        database.withTransaction {
            reordered.forEachIndexed { position, item ->
                if (item.priority != position) dao.update(item.copy(priority = position))
            }
        }
    }

    suspend fun suggestions(): List<RuleSuggestion> =
        PayeeRuleEngine.suggestions(dao.payeeHistory(), dao.all())

    /**
     * Runs the rules over entries that already have a payee but no category.
     *
     * Only ever fills a gap - an entry that already has a category is left alone, because a rule
     * added today should not silently rewrite a decision made deliberately months ago.
     */
    suspend fun backfill(): BackfillResult {
        val rules = dao.active().filter { it.categoryId != null }
        if (rules.isEmpty()) return BackfillResult(0, 0)

        val rows = dao.uncategorisedWithPayee()
        var categorised = 0
        database.withTransaction {
            for (row in rows) {
                val categoryId = PayeeRuleEngine.firstMatch(rules, row.payee)?.categoryId ?: continue
                dao.setCategory(row.id, categoryId)
                categorised++
            }
        }
        return BackfillResult(examined = rows.size, categorised = categorised)
    }
}
