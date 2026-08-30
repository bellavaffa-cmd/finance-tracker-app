package com.financetracker.app.data.category

/**
 * The starter category set, created once on first launch. Every one of these is editable or
 * deletable - the point is that the first transaction can be entered without a setup detour.
 *
 * Colours are chosen to stay distinguishable next to each other in the donut chart, which is the
 * one place a dozen categories appear side by side.
 */
object DefaultCategories {

    data class Seed(val name: String, val color: Long, val children: List<String> = emptyList())

    val EXPENSE = listOf(
        Seed("Groceries", 0xFF4CAF7D, listOf("Supermarket", "Butcher", "Bakery")),
        Seed("Dining out", 0xFFE8A33D, listOf("Restaurants", "Takeaway", "Coffee", "Bars")),
        Seed("Transport", 0xFF4C8DFF, listOf("Fuel", "Public transport", "Taxi", "Parking")),
        Seed("Housing", 0xFF9B7BE8, listOf("Rent", "Mortgage", "Maintenance")),
        Seed("Utilities", 0xFF5EC8D8, listOf("Electricity", "Gas", "Water", "Internet", "Phone")),
        Seed("Health", 0xFFE85E7A, listOf("Pharmacy", "Doctor", "Dentist", "Insurance")),
        Seed("Shopping", 0xFFD98BC8, listOf("Clothing", "Electronics", "Home")),
        Seed("Entertainment", 0xFFE0C24E, listOf("Subscriptions", "Cinema", "Games", "Sport")),
        Seed("Travel", 0xFF62B8E8, listOf("Flights", "Accommodation", "Activities")),
        Seed("Personal", 0xFFB88A5E, listOf("Haircut", "Gym", "Gifts")),
        Seed("Fees & interest", 0xFF8A94A6, listOf("Bank fees", "Interest", "Taxes")),
        Seed("Other", 0xFF6B7688)
    )

    val INCOME = listOf(
        Seed("Salary", 0xFF3FBF8F),
        Seed("Freelance", 0xFF4C8DFF),
        Seed("Bonus", 0xFFE0C24E),
        Seed("Investments", 0xFF9B7BE8, listOf("Dividends", "Interest", "Capital gains")),
        Seed("Refunds", 0xFF5EC8D8),
        Seed("Gifts", 0xFFD98BC8),
        Seed("Other income", 0xFF6B7688)
    )

    /** Flattens the seed tree into rows, resolving parent ids as it goes. */
    suspend fun seedInto(dao: CategoryDao) {
        var order = 0
        suspend fun seedKind(seeds: List<Seed>, kind: CategoryKind) {
            for (seed in seeds) {
                val parentId = dao.insert(
                    Category(
                        name = seed.name,
                        kind = kind,
                        parentId = null,
                        colorArgb = seed.color.toInt(),
                        sortOrder = order++
                    )
                )
                for (child in seed.children) {
                    dao.insert(
                        Category(
                            name = child,
                            kind = kind,
                            parentId = parentId,
                            colorArgb = seed.color.toInt(),
                            sortOrder = order++
                        )
                    )
                }
            }
        }
        seedKind(EXPENSE, CategoryKind.EXPENSE)
        seedKind(INCOME, CategoryKind.INCOME)
    }
}
