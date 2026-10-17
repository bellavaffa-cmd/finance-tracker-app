package com.financetracker.app.domain

import com.financetracker.app.data.CategoryKind

object Defaults {

    val palette: List<Int> = listOf(
        0xFF4C8DFF, 0xFF2BB673, 0xFFF5A623, 0xFFE5484D, 0xFF8E6CF0, 0xFF19B5C8,
        0xFFEC6CB9, 0xFF7A8B99, 0xFFB7791F, 0xFF5E9E3A, 0xFFD9534F, 0xFF3D5A80,
    ).map { it.toInt() }

    val categories: List<Pair<String, CategoryKind>> = listOf(
        "Groceries", "Eating out", "Housing", "Utilities", "Transport", "Health",
        "Shopping", "Entertainment", "Travel", "Subscriptions", "Other",
    ).map { it to CategoryKind.EXPENSE } + listOf(
        "Salary", "Freelance", "Interest", "Gifts", "Other income",
    ).map { it to CategoryKind.INCOME }

    fun colorFor(index: Int): Int = palette[index.mod(palette.size)]
}
