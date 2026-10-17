package com.financetracker.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.financetracker.app.ui.screens.AccountsScreen
import com.financetracker.app.ui.screens.BudgetsScreen
import com.financetracker.app.ui.screens.CategoriesScreen
import com.financetracker.app.ui.screens.EntryScreen
import com.financetracker.app.ui.screens.HomeScreen
import com.financetracker.app.ui.screens.ReportsScreen
import com.financetracker.app.ui.screens.SettingsScreen
import com.financetracker.app.ui.screens.TransactionsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Default.Home),
    TXNS("transactions", "Ledger", Icons.Default.Receipt),
    BUDGETS("budgets", "Budgets", Icons.Default.Savings),
    REPORTS("reports", "Reports", Icons.Default.PieChart),
    SETTINGS("settings", "Settings", Icons.Default.Settings),
}

@Composable
fun FinanceApp(vm: FinanceViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val openEntry: (Long?) -> Unit = { id -> nav.navigate("entry?id=${id ?: 0L}") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (Tab.entries.any { it.route == route }) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding)) {
            composable(Tab.HOME.route) {
                HomeScreen(state, onAdd = { openEntry(null) }, onOpen = openEntry,
                    onSeeAll = { nav.navigate(Tab.TXNS.route) { launchSingleTop = true } },
                    onBudgets = { nav.navigate(Tab.BUDGETS.route) { launchSingleTop = true } })
            }
            composable(Tab.TXNS.route) { TransactionsScreen(state, onAdd = { openEntry(null) }, onOpen = openEntry) }
            composable(Tab.BUDGETS.route) { BudgetsScreen(state, onSetBudget = vm::setBudget) }
            composable(Tab.REPORTS.route) { ReportsScreen(state) }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(
                    state = state, vm = vm,
                    onAccounts = { nav.navigate("accounts") },
                    onCategories = { nav.navigate("categories") },
                )
            }
            composable(
                "entry?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                EntryScreen(
                    state = state,
                    txnId = id.takeIf { it != 0L },
                    onSave = { vm.save(it); nav.popBackStack() },
                    onDelete = { vm.deleteTxn(it); nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("accounts") {
                AccountsScreen(state, onSave = vm::save, onDelete = vm::deleteAccount, onBack = { nav.popBackStack() })
            }
            composable("categories") {
                CategoriesScreen(state, onSave = vm::save, onDelete = vm::deleteCategory, onBack = { nav.popBackStack() })
            }
        }
    }
}
