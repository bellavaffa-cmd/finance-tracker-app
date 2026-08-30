package com.financetracker.app

import android.app.Application
import com.financetracker.app.data.AppDatabase
import com.financetracker.app.data.account.Account
import com.financetracker.app.data.account.AccountRepository
import com.financetracker.app.data.account.AccountType
import com.financetracker.app.data.backup.BackupRepository
import com.financetracker.app.data.budget.BudgetRepository
import com.financetracker.app.data.category.CategoryRepository
import com.financetracker.app.data.category.DefaultCategories
import com.financetracker.app.data.debt.DebtRepository
import com.financetracker.app.data.goal.GoalRepository
import com.financetracker.app.data.recurring.RecurringRepository
import com.financetracker.app.data.settings.SettingsRepository
import com.financetracker.app.data.tag.TagRepository
import com.financetracker.app.data.txn.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FinanceApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val accountRepository: AccountRepository by lazy {
        AccountRepository(database.accountDao(), database.currencyRateDao())
    }
    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database.categoryDao()) }
    val transactionRepository: TransactionRepository by lazy {
        TransactionRepository(
            database = database,
            dao = database.transactionDao(),
            splitDao = database.splitDao(),
            tagDao = database.tagDao()
        )
    }
    val tagRepository: TagRepository by lazy { TagRepository(database.tagDao()) }
    val goalRepository: GoalRepository by lazy { GoalRepository(database.goalDao()) }
    val debtRepository: DebtRepository by lazy { DebtRepository(database.debtDao()) }
    val budgetRepository: BudgetRepository by lazy {
        BudgetRepository(database.budgetDao(), transactionRepository)
    }
    val recurringRepository: RecurringRepository by lazy {
        RecurringRepository(
            dao = database.recurringDao(),
            transactions = transactionRepository,
            accounts = accountRepository,
            settings = settingsRepository
        )
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(
            context = this,
            database = database,
            settings = settingsRepository,
            appVersion = BuildConfig.VERSION_NAME
        )
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            seedIfEmpty()
            // Catching up recurring rules at launch is the only scheduling this app needs: there is
            // nothing to notify about, and a background worker would post entries you would then
            // discover after the fact rather than on the screen in front of you.
            runCatching { recurringRepository.postDue() }
        }
    }

    /**
     * First-launch data. A tracker that opens onto an empty screen with no categories and no
     * account cannot record anything until you have done ten minutes of setup, so it ships with a
     * usable starting point that is entirely editable.
     */
    private suspend fun seedIfEmpty() {
        if (settingsRepository.hasSeeded()) return

        if (categoryRepository.count() == 0) {
            DefaultCategories.seedInto(database.categoryDao())
        }
        if (accountRepository.count() == 0) {
            val base = settingsRepository.currentBaseCurrency()
            accountRepository.insert(
                Account(
                    name = "Cash",
                    type = AccountType.CASH,
                    currencyCode = base,
                    openingBalanceMinor = 0,
                    colorArgb = 0xFF3FBF8F.toInt(),
                    sortOrder = 0
                )
            )
            accountRepository.insert(
                Account(
                    name = "Current account",
                    type = AccountType.BANK,
                    currencyCode = base,
                    openingBalanceMinor = 0,
                    colorArgb = 0xFF4C8DFF.toInt(),
                    sortOrder = 1
                )
            )
        }
        settingsRepository.markSeeded()
    }
}
