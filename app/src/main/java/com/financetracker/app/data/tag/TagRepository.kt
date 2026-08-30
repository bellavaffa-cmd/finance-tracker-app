package com.financetracker.app.data.tag

import com.financetracker.app.data.Money
import com.financetracker.app.data.txn.TxnType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TagRepository(private val dao: TagDao) {

    val allTags: Flow<List<Tag>> = dao.observeAll()

    suspend fun all(): List<Tag> = dao.all()

    suspend fun usageCount(tagId: Long): Int = dao.usageCount(tagId)

    /**
     * Finds an existing tag by name or creates it. Matching is case-insensitive so "#Holiday" and
     * "#holiday" do not become two tags that each hold half the trip.
     */
    suspend fun findOrCreate(name: String, colorArgb: Int): Tag? {
        val cleaned = name.trim().removePrefix("#").trim()
        if (cleaned.isEmpty()) return null
        dao.byName(cleaned)?.let { return it }
        val id = dao.insert(Tag(name = cleaned, colorArgb = colorArgb))
        // insert returns -1 when the unique index rejected a race; re-read rather than assume.
        return if (id > 0) Tag(id = id, name = cleaned, colorArgb = colorArgb) else dao.byName(cleaned)
    }

    suspend fun rename(tag: Tag, name: String, colorArgb: Int) {
        val cleaned = name.trim().removePrefix("#").trim()
        if (cleaned.isEmpty()) return
        dao.update(tag.copy(name = cleaned, colorArgb = colorArgb))
    }

    /** Deleting a tag drops its links; the transactions themselves are untouched. */
    suspend fun delete(tag: Tag) = dao.delete(tag)

    fun tagTotals(type: TxnType, fromMillis: Long, toMillis: Long, baseCurrency: String): Flow<List<TagTotal>> =
        dao.observeTagAmounts(type.name, fromMillis, toMillis).map { rows ->
            rows.groupBy { it.tagId }
                .map { (_, group) ->
                    val first = group.first()
                    TagTotal(
                        tag = Tag(first.tagId, first.name, first.colorArgb),
                        amountMinorBase = group.sumOf {
                            Money.toBaseMinor(it.amountMinor, it.currencyCode, it.fxRateToBase, baseCurrency)
                        },
                        transactionCount = group.distinctBy { it.txnId }.size
                    )
                }
                .sortedByDescending { it.amountMinorBase }
        }
}
