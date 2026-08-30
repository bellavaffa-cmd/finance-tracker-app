package com.financetracker.app.data.tag

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A cross-cutting label. Categories answer "what kind of spending is this"; tags answer "what was
 * this part of" - one holiday's total across flights, food and hotels, or everything that needs
 * reimbursing. A transaction has exactly one category and any number of tags.
 */
@Entity(
    tableName = "tag",
    indices = [Index(value = ["name"], unique = true)]
)
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int
)

/**
 * Join row. The composite primary key makes a duplicate tagging impossible at the schema level
 * rather than relying on the UI never sending one twice.
 */
@Entity(
    tableName = "txn_tag",
    primaryKeys = ["txnId", "tagId"],
    indices = [Index("tagId")]
)
data class TxnTag(
    val txnId: Long,
    val tagId: Long
)

/** A tag with its total for some window, in base minor units. */
data class TagTotal(
    val tag: Tag,
    val amountMinorBase: Long,
    val transactionCount: Int
)
