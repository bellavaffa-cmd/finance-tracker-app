package com.financetracker.app.data.txn

import com.financetracker.app.data.Money
import kotlinx.coroutines.flow.Flow

/** The filter state of the transactions screen, passed straight through to one SQL query. */
data class TxnFilter(
    val fromMillis: Long,
    val toMillis: Long,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val type: TxnType? = null,
    val query: String = "",
    val minMinor: Long? = null,
    val maxMinor: Long? = null
) {
    val isNarrowed: Boolean
        get() = accountId != null || categoryId != null || type != null ||
            query.isNotBlank() || minMinor != null || maxMinor != null
}

class TransactionRepository(private val dao: TransactionDao) {

    fun recent(limit: Int): Flow<List<TransactionDetail>> = dao.observeRecent(limit)

    fun inRange(fromMillis: Long, toMillis: Long): Flow<List<TransactionDetail>> =
        dao.observeInRange(fromMillis, toMillis)

    fun filtered(filter: TxnFilter): Flow<List<TransactionDetail>> = dao.observeFiltered(
        fromMillis = filter.fromMillis,
        toMillis = filter.toMillis,
        accountId = filter.accountId,
        categoryId = filter.categoryId,
        type = filter.type?.name,
        query = filter.query.trim(),
        minMinor = filter.minMinor,
        maxMinor = filter.maxMinor
    )

    fun amountRows(type: TxnType, fromMillis: Long, toMillis: Long): Flow<List<AmountRow>> =
        dao.observeAmountRows(type.name, fromMillis, toMillis)

    suspend fun amountRowsOnce(type: TxnType, fromMillis: Long, toMillis: Long, categoryId: Long? = null): List<AmountRow> =
        dao.amountRows(type.name, fromMillis, toMillis, categoryId)

    suspend fun detailById(id: Long): TransactionDetail? = dao.detailById(id)

    suspend fun byId(id: Long): Transaction? = dao.byId(id)

    suspend fun payeeSuggestions(prefix: String): List<String> =
        if (prefix.isBlank()) emptyList() else dao.payeeSuggestions(prefix.trim())

    suspend fun lastCategoryForPayee(payee: String): Long? =
        if (payee.isBlank()) null else dao.lastCategoryForPayee(payee.trim())

    suspend fun earliestDateMillis(): Long? = dao.earliestDateMillis()

    suspend fun insert(transaction: Transaction): Long = dao.insert(transaction)

    suspend fun update(transaction: Transaction) = dao.update(transaction)

    suspend fun softDelete(id: Long) = dao.softDelete(id, System.currentTimeMillis())

    suspend fun restore(id: Long) = dao.restore(id)

    companion object {
        /** Sums mixed-currency rows into base minor units using each row's own frozen rate. */
        fun sumToBase(rows: List<AmountRow>, baseCurrency: String): Long =
            rows.sumOf { Money.toBaseMinor(it.amountMinor, it.currencyCode, it.fxRateToBase, baseCurrency) }
    }
}
