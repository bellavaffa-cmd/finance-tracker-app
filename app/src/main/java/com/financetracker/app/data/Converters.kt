package com.financetracker.app.data

import androidx.room.TypeConverter
import com.financetracker.app.data.account.AccountType
import com.financetracker.app.data.category.CategoryKind
import com.financetracker.app.data.debt.DebtKind
import com.financetracker.app.data.recurring.Frequency
import com.financetracker.app.data.txn.TxnType

/**
 * Enums are stored as their constant name rather than their ordinal, so reordering an enum can
 * never silently reinterpret existing rows - and so hand-written SQL can compare against a
 * readable literal like 'EXPENSE'.
 */
class Converters {

    @TypeConverter fun txnTypeToString(value: TxnType): String = value.name
    @TypeConverter fun stringToTxnType(value: String): TxnType = TxnType.valueOf(value)

    @TypeConverter fun accountTypeToString(value: AccountType): String = value.name
    @TypeConverter fun stringToAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter fun categoryKindToString(value: CategoryKind): String = value.name
    @TypeConverter fun stringToCategoryKind(value: String): CategoryKind = CategoryKind.valueOf(value)

    @TypeConverter fun frequencyToString(value: Frequency): String = value.name
    @TypeConverter fun stringToFrequency(value: String): Frequency = Frequency.valueOf(value)

    @TypeConverter fun debtKindToString(value: DebtKind): String = value.name
    @TypeConverter fun stringToDebtKind(value: String): DebtKind = DebtKind.valueOf(value)
}
