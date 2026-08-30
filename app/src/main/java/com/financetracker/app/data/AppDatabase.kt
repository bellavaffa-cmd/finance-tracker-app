package com.financetracker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.financetracker.app.data.account.Account
import com.financetracker.app.data.account.AccountDao
import com.financetracker.app.data.budget.Budget
import com.financetracker.app.data.budget.BudgetDao
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.category.CategoryDao
import com.financetracker.app.data.debt.Debt
import com.financetracker.app.data.debt.DebtDao
import com.financetracker.app.data.goal.Goal
import com.financetracker.app.data.goal.GoalDao
import com.financetracker.app.data.recurring.RecurringDao
import com.financetracker.app.data.recurring.RecurringRule
import com.financetracker.app.data.settings.CurrencyRate
import com.financetracker.app.data.settings.CurrencyRateDao
import com.financetracker.app.data.tag.Tag
import com.financetracker.app.data.tag.TagDao
import com.financetracker.app.data.tag.TxnTag
import com.financetracker.app.data.txn.SplitDao
import com.financetracker.app.data.txn.Transaction
import com.financetracker.app.data.txn.TransactionDao
import com.financetracker.app.data.txn.TxnSplit

/**
 * Adds tags and split transactions.
 *
 * Purely additive - three new tables and their indices, nothing touched on existing rows. Version 1
 * shipped as a signed release, so this has to migrate rather than recreate: a destructive fallback
 * here would silently erase somebody's entire ledger on update.
 *
 * The SQL is written to match exactly what Room generates for these entities. Any drift and Room's
 * schema validation throws IllegalStateException on first open after the update.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tag` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`name` TEXT NOT NULL, " +
                "`colorArgb` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_name` ON `tag` (`name`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `txn_tag` (" +
                "`txnId` INTEGER NOT NULL, " +
                "`tagId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`txnId`, `tagId`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_txn_tag_tagId` ON `txn_tag` (`tagId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `txn_split` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`txnId` INTEGER NOT NULL, " +
                "`categoryId` INTEGER, " +
                "`amountMinor` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_txn_split_txnId` ON `txn_split` (`txnId`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_txn_split_categoryId` ON `txn_split` (`categoryId`)"
        )
    }
}

/**
 * Adds savings goals and tracked debts. Additive again - two new tables, nothing existing touched.
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goal` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`name` TEXT NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`targetMinor` INTEGER NOT NULL, " +
                "`startingBalanceMinor` INTEGER NOT NULL, " +
                "`targetDateMillis` INTEGER, " +
                "`colorArgb` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`isArchived` INTEGER NOT NULL, " +
                "`createdAtMillis` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_accountId` ON `goal` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_isArchived` ON `goal` (`isArchived`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `debt` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`name` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`balanceMinor` INTEGER NOT NULL, " +
                "`currencyCode` TEXT NOT NULL, " +
                "`annualRatePercent` REAL NOT NULL, " +
                "`minimumPaymentMinor` INTEGER NOT NULL, " +
                "`colorArgb` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`isActive` INTEGER NOT NULL, " +
                "`createdAtMillis` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_isActive` ON `debt` (`isActive`)")
    }
}

@Database(
    entities = [
        Account::class,
        Category::class,
        Transaction::class,
        Budget::class,
        RecurringRule::class,
        CurrencyRate::class,
        Tag::class,
        TxnTag::class,
        TxnSplit::class,
        Goal::class,
        Debt::class
    ],
    version = 3,
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
    abstract fun tagDao(): TagDao
    abstract fun splitDao(): SplitDao
    abstract fun goalDao(): GoalDao
    abstract fun debtDao(): DebtDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance-tracker.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
