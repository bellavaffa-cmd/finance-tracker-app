package com.financetracker.app.data.category

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CategoryKind { EXPENSE, INCOME }

@Entity(
    tableName = "category",
    indices = [Index("parentId"), Index("kind")]
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    /** Null for a top-level category; otherwise the id of the parent it belongs to. */
    val parentId: Long? = null,
    val iconKey: String = "category",
    val colorArgb: Int,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false
)

/** A parent category with its subcategories, for the picker and the management screen. */
data class CategoryGroup(
    val parent: Category,
    val children: List<Category>
)
