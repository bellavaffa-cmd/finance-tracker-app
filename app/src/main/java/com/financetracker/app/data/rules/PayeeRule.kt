package com.financetracker.app.data.rules

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MatchType(val label: String, val hint: String) {
    CONTAINS("Contains", "matches anywhere in the payee"),
    STARTS_WITH("Starts with", "matches the beginning of the payee"),
    EQUALS("Is exactly", "matches the whole payee");

    fun matches(payee: String, pattern: String): Boolean {
        val subject = payee.trim().lowercase()
        val needle = pattern.trim().lowercase()
        if (needle.isEmpty()) return false
        return when (this) {
            CONTAINS -> subject.contains(needle)
            STARTS_WITH -> subject.startsWith(needle)
            EQUALS -> subject == needle
        }
    }
}

/**
 * "When the payee looks like this, file it here."
 *
 * Deliberately not regular expressions. A regex that backtracks badly can hang the entry screen,
 * and a wrong one fails in ways that are hard to see; three plain match types cover essentially
 * every real merchant string without either problem.
 *
 * [renameTo] exists because bank exports rarely give a clean name - "CARD PURCHASE LIDL 4432 MILANO"
 * is one merchant across a hundred different strings, and normalising it is what makes payee
 * history and autocomplete useful afterwards.
 */
@Entity(
    tableName = "payee_rule",
    indices = [Index("isActive"), Index("priority")]
)
data class PayeeRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val matchType: MatchType = MatchType.CONTAINS,
    val categoryId: Long? = null,
    /** Optional: also file it to a particular account. */
    val accountId: Long? = null,
    /** Optional: rewrite the messy payee to a clean name. */
    val renameTo: String? = null,
    /** Lower runs first. Ties break on pattern length, so the more specific rule wins. */
    val priority: Int = 0,
    val isActive: Boolean = true,
    val createdAtMillis: Long
) {
    val isUseful: Boolean get() = categoryId != null || accountId != null || !renameTo.isNullOrBlank()
}

/** What a rule wants done to a transaction. */
data class RuleOutcome(
    val rule: PayeeRule,
    val categoryId: Long?,
    val accountId: Long?,
    val payee: String?
)

/**
 * Matching, kept pure so the ordering rules can be reasoned about and tested directly.
 */
object PayeeRuleEngine {

    /**
     * The first rule that matches wins.
     *
     * Order is by explicit priority, then by pattern length descending: given both "lidl" and
     * "lidl express", the longer pattern is the more specific statement of intent and should not be
     * shadowed by the shorter one just because it was created later.
     */
    fun sort(rules: List<PayeeRule>): List<PayeeRule> =
        rules.sortedWith(compareBy<PayeeRule> { it.priority }.thenByDescending { it.pattern.length })

    fun firstMatch(rules: List<PayeeRule>, payee: String): PayeeRule? {
        if (payee.isBlank()) return null
        return sort(rules.filter { it.isActive }).firstOrNull { it.matchType.matches(payee, it.pattern) }
    }

    fun apply(rules: List<PayeeRule>, payee: String): RuleOutcome? {
        val rule = firstMatch(rules, payee) ?: return null
        return RuleOutcome(
            rule = rule,
            categoryId = rule.categoryId,
            accountId = rule.accountId,
            payee = rule.renameTo?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Suggests rules from history: payees filed to the same category several times already.
     *
     * Only proposals - nothing is created without the user agreeing. The threshold keeps a single
     * coincidence from becoming a rule that then silently mis-files everything similar.
     */
    fun suggestions(
        history: List<PayeeCategoryCount>,
        existing: List<PayeeRule>,
        minimumOccurrences: Int = 3
    ): List<RuleSuggestion> {
        val covered = existing.map { it.pattern.trim().lowercase() }.toSet()
        return history
            .filter { it.count >= minimumOccurrences }
            .filter { it.payee.trim().lowercase() !in covered }
            // A payee filed under two different categories is not a rule waiting to happen.
            .groupBy { it.payee.trim().lowercase() }
            .filterValues { it.size == 1 }
            .map { (_, rows) -> rows.first() }
            .map { RuleSuggestion(it.payee, it.categoryId, it.categoryName, it.count) }
            .sortedByDescending { it.occurrences }
    }
}

/** How often one payee was filed under one category. */
data class PayeeCategoryCount(
    val payee: String,
    val categoryId: Long,
    val categoryName: String,
    val count: Int
)

data class RuleSuggestion(
    val payee: String,
    val categoryId: Long,
    val categoryName: String,
    val occurrences: Int
)
