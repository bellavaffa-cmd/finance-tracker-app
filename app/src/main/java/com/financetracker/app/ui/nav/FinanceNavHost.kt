package com.financetracker.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.financetracker.app.FinanceApplication
import com.financetracker.app.ui.accounts.AccountsScreen
import com.financetracker.app.ui.budget.BudgetScreen
import com.financetracker.app.ui.budget.BudgetViewModel
import com.financetracker.app.ui.categories.CategoriesScreen
import com.financetracker.app.ui.debts.DebtsScreen
import com.financetracker.app.ui.debts.DebtsViewModel
import com.financetracker.app.ui.goals.GoalsScreen
import com.financetracker.app.ui.goals.GoalsViewModel
import com.financetracker.app.ui.importer.ImportScreen
import com.financetracker.app.ui.importer.ImportViewModel
import com.financetracker.app.ui.entry.EntryScreen
import com.financetracker.app.ui.entry.EntryViewModel
import com.financetracker.app.ui.home.HomeScreen
import com.financetracker.app.ui.home.HomeViewModel
import com.financetracker.app.ui.recurring.RecurringScreen
import com.financetracker.app.ui.rules.RulesScreen
import com.financetracker.app.ui.rules.RulesViewModel
import com.financetracker.app.ui.reports.ReportsScreen
import com.financetracker.app.ui.reports.ReportsViewModel
import com.financetracker.app.ui.settings.DataScreen
import com.financetracker.app.ui.settings.DataViewModel
import com.financetracker.app.ui.settings.ManageViewModel
import com.financetracker.app.ui.settings.SettingsScreen
import com.financetracker.app.ui.transactions.TransactionsScreen
import com.financetracker.app.ui.theme.Accent
import com.financetracker.app.ui.theme.Bg
import com.financetracker.app.ui.transactions.TransactionsViewModel

@Composable
fun FinanceNavHost(application: FinanceApplication) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(application))
    val transactionsViewModel: TransactionsViewModel =
        viewModel(factory = TransactionsViewModel.factory(application))
    val budgetViewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.factory(application))
    val reportsViewModel: ReportsViewModel = viewModel(factory = ReportsViewModel.factory(application))
    val manageViewModel: ManageViewModel = viewModel(factory = ManageViewModel.factory(application))

    // The bottom bar and add button belong to the four top-level tabs only; the entry screen and
    // the management screens bring their own chrome.
    val showsTabBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = { if (showsTabBar) FinanceBottomBar(navController, currentRoute) },
        floatingActionButton = {
            if (showsTabBar) {
                FloatingActionButton(
                    onClick = { navController.navigate(Routes.entry()) },
                    containerColor = Accent,
                    contentColor = Bg
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onOpenBudgets = { navController.switchTab(Routes.BUDGETS) },
                    onOpenTransactions = { navController.switchTab(Routes.TRANSACTIONS) },
                    onEditTransaction = { navController.navigate(Routes.entry(it)) }
                )
            }

            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(
                    viewModel = transactionsViewModel,
                    onEditTransaction = { navController.navigate(Routes.entry(it)) }
                )
            }

            composable(Routes.BUDGETS) { BudgetScreen(viewModel = budgetViewModel) }

            composable(Routes.REPORTS) { ReportsScreen(viewModel = reportsViewModel) }

            composable(
                route = Routes.ENTRY,
                arguments = listOf(navArgument(Routes.ENTRY_ARG) { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong(Routes.ENTRY_ARG) ?: -1L
                // A fresh EntryViewModel per visit: the add screen must never open holding the
                // half-finished state of the transaction edited before it.
                val entryViewModel: EntryViewModel =
                    viewModel(factory = EntryViewModel.factory(application), key = "entry-$id")
                EntryScreen(
                    viewModel = entryViewModel,
                    editingId = id.takeIf { it > 0 },
                    onClose = { navController.popBackStack() }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = manageViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                    onOpenRecurring = { navController.navigate(Routes.RECURRING) },
                    onOpenData = { navController.navigate(Routes.DATA) },
                    onOpenGoals = { navController.navigate(Routes.GOALS) },
                    onOpenDebts = { navController.navigate(Routes.DEBTS) },
                    onOpenRules = { navController.navigate(Routes.RULES) }
                )
            }

            composable(Routes.ACCOUNTS) {
                AccountsScreen(viewModel = manageViewModel, onBack = { navController.popBackStack() })
            }

            composable(Routes.CATEGORIES) {
                CategoriesScreen(viewModel = manageViewModel, onBack = { navController.popBackStack() })
            }

            composable(Routes.RECURRING) {
                RecurringScreen(viewModel = manageViewModel, onBack = { navController.popBackStack() })
            }

            composable(Routes.GOALS) {
                val goalsViewModel: GoalsViewModel =
                    viewModel(factory = GoalsViewModel.factory(application))
                GoalsScreen(viewModel = goalsViewModel, onBack = { navController.popBackStack() })
            }

            composable(Routes.DEBTS) {
                val debtsViewModel: DebtsViewModel =
                    viewModel(factory = DebtsViewModel.factory(application))
                DebtsScreen(viewModel = debtsViewModel, onBack = { navController.popBackStack() })
            }

            composable(Routes.DATA) {
                val dataViewModel: DataViewModel =
                    viewModel(factory = DataViewModel.factory(application))
                DataScreen(
                    viewModel = dataViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenImport = { navController.navigate(Routes.IMPORT) }
                )
            }

            composable(Routes.RULES) {
                val rulesViewModel: RulesViewModel =
                    viewModel(factory = RulesViewModel.factory(application))
                RulesScreen(viewModel = rulesViewModel, onBack = { navController.popBackStack() })
            }

            composable(Routes.IMPORT) {
                val importViewModel: ImportViewModel =
                    viewModel(factory = ImportViewModel.factory(application))
                ImportScreen(viewModel = importViewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}

/** Tab switches reset to the tab's own root and restore whatever state it had. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun FinanceBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { if (currentRoute != item.route) navController.switchTab(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}
