package com.financetracker.app.data.category

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(private val dao: CategoryDao) {

    val allCategories: Flow<List<Category>> = dao.observeAll()

    val expenseGroups: Flow<List<CategoryGroup>> = groupsOf(CategoryKind.EXPENSE)
    val incomeGroups: Flow<List<CategoryGroup>> = groupsOf(CategoryKind.INCOME)

    private fun groupsOf(kind: CategoryKind): Flow<List<CategoryGroup>> =
        dao.observeByKind(kind).map { flat -> group(flat) }

    suspend fun byId(id: Long): Category? = dao.byId(id)

    suspend fun count(): Int = dao.count()

    suspend fun insert(category: Category): Long = dao.insert(category)

    suspend fun update(category: Category) = dao.update(category)

    suspend fun transactionCount(id: Long): Int = dao.transactionCount(id)

    suspend fun childCount(id: Long): Int = dao.childCount(id)

    /**
     * Deleting a category keeps its transactions - they simply become uncategorised, and its
     * subcategories are promoted to top level. Losing spending history because a label was renamed
     * out of existence would be a far worse outcome than an "Uncategorised" slice in a chart.
     */
    suspend fun delete(category: Category) {
        dao.detachChildren(category.id)
        dao.clearCategoryOnTransactions(category.id)
        dao.delete(category)
    }

    companion object {
        /** Nests a flat list into parents with their children, preserving sort order. */
        fun group(flat: List<Category>): List<CategoryGroup> {
            val byParent = flat.filter { it.parentId != null }.groupBy { it.parentId!! }
            return flat.filter { it.parentId == null }
                .map { parent -> CategoryGroup(parent, byParent[parent.id].orEmpty()) }
        }
    }
}
