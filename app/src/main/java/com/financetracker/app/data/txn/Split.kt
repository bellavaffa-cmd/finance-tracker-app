package com.financetracker.app.data.txn

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One leg of a split transaction: part of a single payment attributed to its own category.
 *
 * The invariant, enforced when saving rather than by the schema: a transaction either carries a
 * [Transaction.categoryId] and has no splits, or has two or more splits and a null categoryId.
 * Reporting relies on that being true - a row that had both would be counted twice.
 *
 * Splits are stored in the *account's* currency, like the parent amount, and must sum to it.
 */
@Entity(
    tableName = "txn_split",
    indices = [Index("txnId"), Index("categoryId")]
)
data class TxnSplit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txnId: Long,
    /** Null only if the category was later deleted. */
    val categoryId: Long? = null,
    val amountMinor: Long,
    val note: String = ""
)

/** A split joined with its category, for the editor and the detail row. */
data class SplitDetail(
    val id: Long,
    val txnId: Long,
    val categoryId: Long?,
    val categoryName: String?,
    val colorArgb: Int?,
    val amountMinor: Long,
    val note: String
)

/** A split being edited, before it has been given an id. */
data class SplitDraft(
    val categoryId: Long? = null,
    val amountMinor: Long = 0,
    val note: String = ""
)
