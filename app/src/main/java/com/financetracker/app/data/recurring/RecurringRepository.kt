package com.financetracker.app.data.recurring

import com.financetracker.app.data.Money
import com.financetracker.app.data.account.AccountRepository
import com.financetracker.app.data.settings.SettingsRepository
import com.financetracker.app.data.txn.Transaction
import com.financetracker.app.data.txn.TransactionRepository
import com.financetracker.app.data.txn.TxnType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class RecurringRepository(
    private val dao: RecurringDao,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val settings: SettingsRepository
) {
    val allRules: Flow<List<RecurringRuleDetail>> = dao.observeAll()

    val activeRules: Flow<List<RecurringRuleDetail>> = dao.observeActive()

    fun dueForConfirmation(nowMillis: Long): Flow<List<RecurringRuleDetail>> =
        dao.observeDueForConfirmation(nowMillis)

    suspend fun byId(id: Long): RecurringRule? = dao.byId(id)

    suspend fun insert(rule: RecurringRule): Long = dao.insert(rule)

    suspend fun update(rule: RecurringRule) = dao.update(rule)

    suspend fun delete(rule: RecurringRule) = dao.delete(rule)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun setActive(rule: RecurringRule, active: Boolean) = dao.update(rule.copy(isActive = active))

    /**
     * Posts every occurrence that has come due on auto-posting rules, then advances each rule past
     * whatever it posted. Returns how many transactions were created.
     *
     * Occurrences are caught up one at a time rather than collapsed into a single entry, so leaving
     * the app closed for three months produces three rent payments, not one. [MAX_CATCH_UP] bounds
     * that in case a rule was created with a start date years in the past.
     */
    suspend fun postDue(nowMillis: Long = System.currentTimeMillis()): Int {
        val baseCurrency = settings.currentBaseCurrency()
        var posted = 0
        for (rule in dao.dueRules(nowMillis)) {
            if (!rule.autoPost) continue
            var working = rule
            var iterations = 0
            while (working.nextDueMillis <= nowMillis && iterations < MAX_CATCH_UP) {
                val end = working.endDateMillis
                if (end != null && working.nextDueMillis > end) break
                post(working, working.nextDueMillis, baseCurrency)
                posted++
                iterations++
                working = working.copy(
                    nextDueMillis = nextOccurrence(working, working.nextDueMillis),
                    lastPostedMillis = working.nextDueMillis
                )
            }
            val finished = working.endDateMillis?.let { working.nextDueMillis > it } ?: false
            dao.update(if (finished) working.copy(isActive = false) else working)
        }
        return posted
    }

    /** Confirms one occurrence of a manual rule and moves it on to the next. */
    suspend fun confirm(ruleId: Long) {
        val rule = dao.byId(ruleId) ?: return
        val baseCurrency = settings.currentBaseCurrency()
        post(rule, rule.nextDueMillis, baseCurrency)
        val advanced = rule.copy(
            nextDueMillis = nextOccurrence(rule, rule.nextDueMillis),
            lastPostedMillis = rule.nextDueMillis
        )
        val finished = advanced.endDateMillis?.let { advanced.nextDueMillis > it } ?: false
        dao.update(if (finished) advanced.copy(isActive = false) else advanced)
    }

    /** Skips this occurrence without posting anything - the "not this month" button. */
    suspend fun skip(ruleId: Long) {
        val rule = dao.byId(ruleId) ?: return
        dao.update(rule.copy(nextDueMillis = nextOccurrence(rule, rule.nextDueMillis)))
    }

    private suspend fun post(rule: RecurringRule, dateMillis: Long, baseCurrency: String) {
        val account = accounts.byId(rule.accountId) ?: return
        val rate = accounts.rateToBase(account.currencyCode, baseCurrency)

        // A cross-currency transfer template stores only the source amount; the landing amount is
        // recomputed at posting time because that is when the conversion actually happens.
        val toAmount = if (rule.type == TxnType.TRANSFER) {
            rule.toAmountMinor ?: rule.toAccountId?.let { toId ->
                val target = accounts.byId(toId) ?: return@let null
                val targetRate = accounts.rateToBase(target.currencyCode, baseCurrency)
                Money.convert(rule.amountMinor, account.currencyCode, rate, target.currencyCode, targetRate)
            }
        } else null

        transactions.insert(
            Transaction(
                type = rule.type,
                dateMillis = dateMillis,
                accountId = rule.accountId,
                toAccountId = rule.toAccountId,
                categoryId = rule.categoryId,
                amountMinor = rule.amountMinor,
                toAmountMinor = toAmount,
                fxRateToBase = rate,
                payee = rule.payee.ifBlank { rule.name },
                note = rule.note,
                recurringRuleId = rule.id,
                createdAtMillis = System.currentTimeMillis()
            )
        )
    }

    companion object {
        private const val MAX_CATCH_UP = 60
        private val zone: ZoneId get() = ZoneId.systemDefault()

        /**
         * The occurrence following [fromMillis].
         *
         * Monthly rules step by calendar month from the rule's [RecurringRule.anchorDayOfMonth]
         * rather than by adding 30 days, and re-derive the day from the anchor each time. That is
         * what keeps a rule due on the 31st from sliding permanently to the 28th after one visit
         * to February.
         */
        fun nextOccurrence(rule: RecurringRule, fromMillis: Long): Long {
            val from = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDateTime()
            val step = rule.interval.coerceAtLeast(1).toLong()
            val nextDate: LocalDate = when (rule.frequency) {
                Frequency.DAILY -> from.toLocalDate().plusDays(step)
                Frequency.WEEKLY -> from.toLocalDate().plusWeeks(step)
                Frequency.MONTHLY -> {
                    val target = YearMonth.from(from.toLocalDate()).plusMonths(step)
                    val anchor = rule.anchorDayOfMonth ?: from.dayOfMonth
                    target.atDay(anchor.coerceIn(1, target.lengthOfMonth()))
                }
                Frequency.YEARLY -> from.toLocalDate().plusYears(step)
            }
            return LocalDateTime.of(nextDate, from.toLocalTime())
                .atZone(zone).toInstant().toEpochMilli()
        }

        /**
         * What a rule costs per month, normalised, so a mix of weekly and yearly commitments can be
         * added up into one honest "this is what I am committed to" figure. Uses 365.25/12 days per
         * month so leap years do not skew the annualisation.
         */
        fun monthlyEquivalentMinor(amountMinor: Long, frequency: Frequency, interval: Int): Long {
            val n = interval.coerceAtLeast(1)
            val perMonth = when (frequency) {
                Frequency.DAILY -> 30.4375 / n
                Frequency.WEEKLY -> 4.348214 / n
                Frequency.MONTHLY -> 1.0 / n
                Frequency.YEARLY -> 1.0 / (12.0 * n)
            }
            return Math.round(amountMinor * perMonth)
        }
    }
}
