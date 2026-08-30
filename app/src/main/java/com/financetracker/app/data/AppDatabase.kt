package com.financetracker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.financetracker.app.data.account.Account
import com.financetracker.app.data.account.AccountDao
import com.financetracker.app.data.budget.Budget
import com.financetracker.app.data.budget.BudgetDao
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.category.CategoryDao
import com.financetracker.app.data.recurring.RecurringDao
import com.financetracker.app.data.recurring.RecurringRule
import com.financetracker.app.data.settings.CurrencyRate
import com.financetracker.app.data.settings.CurrencyRateDao
import com.financetracker.app.data.txn.Transaction
import com.financetracker.app.data.txn.TransactionDao

@Database(
    entities = [
        Account::class,
        Category::class,
        Transaction::class,
        Budget::class,
        RecurringRule::class,
        CurrencyRate::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun currencyRateDao(): CurrencyRateDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance-tracker.db"
                ).build().also { INSTANCE = it }
            }
    }
}
