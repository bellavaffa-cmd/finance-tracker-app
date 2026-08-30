package com.financetracker.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val BUDGETS = "budgets"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"
    const val RECURRING = "recurring"
    const val DATA = "data"
    const val GOALS = "goals"
    const val DEBTS = "debts"

    /** Entry doubles as add and edit; -1 means "new". */
    const val ENTRY = "entry/{transactionId}"
    fun entry(transactionId: Long = -1L) = "entry/$transactionId"
    const val ENTRY_ARG = "transactionId"
}

data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

val bottomNavItems = listOf(
    BottomNavItem(Routes.HOME, "Home", Icons.Filled.Home),
    BottomNavItem(Routes.TRANSACTIONS, "Ledger", Icons.Filled.ReceiptLong),
    BottomNavItem(Routes.BUDGETS, "Budgets", Icons.Filled.DonutLarge),
    BottomNavItem(Routes.REPORTS, "Reports", Icons.Filled.PieChart)
)
