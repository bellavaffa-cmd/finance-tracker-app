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
        TxnSplit::class
    ],
    version = 2,
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

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance-tracker.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
