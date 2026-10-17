package com.financetracker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.financetracker.app.domain.Defaults

@Database(entities = [Account::class, Category::class, Txn::class, Budget::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dao(): LedgerDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "ledger.db")
                .addCallback(Seed)
                .build()
                .also { instance = it }
        }
    }

    /** A usable starting point on first launch: one account and a default set of categories. */
    private object Seed : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO accounts (name, type, openingBalance, archived) VALUES (?, ?, 0, 0)",
                arrayOf("Current account", AccountType.BANK.name),
            )
            Defaults.categories.forEachIndexed { i, (name, kind) ->
                db.execSQL(
                    "INSERT INTO categories (name, kind, color) VALUES (?, ?, ?)",
                    arrayOf(name, kind.name, Defaults.colorFor(i)),
                )
            }
        }
    }
}
