package com.financetracker.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.financetracker.app.data.MonthPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "finance_settings")

/** App-wide preferences. Small enough to live in DataStore rather than another Room table. */
class SettingsRepository(private val context: Context) {

    private val baseCurrencyKey = stringPreferencesKey("base_currency")
    private val monthStartDayKey = intPreferencesKey("month_start_day")
    private val seededKey = booleanPreferencesKey("seeded")
    private val hideBalancesKey = booleanPreferencesKey("hide_balances")

    val baseCurrency: Flow<String> =
        context.settingsDataStore.data.map { it[baseCurrencyKey] ?: DEFAULT_BASE_CURRENCY }

    /** Day of month the budgeting period rolls over. See [MonthPeriod]. */
    val monthStartDay: Flow<Int> =
        context.settingsDataStore.data.map {
            (it[monthStartDayKey] ?: 1).coerceIn(1, MonthPeriod.MAX_START_DAY)
        }

    /** Blurs every amount on screen, for checking your balance somewhere public. */
    val hideBalances: Flow<Boolean> =
        context.settingsDataStore.data.map { it[hideBalancesKey] ?: false }

    suspend fun currentBaseCurrency(): String = baseCurrency.first()

    suspend fun currentMonthStartDay(): Int = monthStartDay.first()

    suspend fun setBaseCurrency(code: String) {
        context.settingsDataStore.edit { it[baseCurrencyKey] = code }
    }

    suspend fun setMonthStartDay(day: Int) {
        context.settingsDataStore.edit {
            it[monthStartDayKey] = day.coerceIn(1, MonthPeriod.MAX_START_DAY)
        }
    }

    suspend fun setHideBalances(hide: Boolean) {
        context.settingsDataStore.edit { it[hideBalancesKey] = hide }
    }

    suspend fun hasSeeded(): Boolean = context.settingsDataStore.data.first()[seededKey] ?: false

    suspend fun markSeeded() {
        context.settingsDataStore.edit { it[seededKey] = true }
    }

    companion object {
        const val DEFAULT_BASE_CURRENCY = "EUR"
    }
}
