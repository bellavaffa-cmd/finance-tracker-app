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

/** The subset of preferences that travels in a backup file. */
data class SettingsSnapshot(
    val baseCurrency: String,
    val monthStartDay: Int,
    val hideBalances: Boolean,
    val appLockEnabled: Boolean
)

/** App-wide preferences. Small enough to live in DataStore rather than another Room table. */
class SettingsRepository(private val context: Context) {

    private val baseCurrencyKey = stringPreferencesKey("base_currency")
    private val monthStartDayKey = intPreferencesKey("month_start_day")
    private val seededKey = booleanPreferencesKey("seeded")
    private val hideBalancesKey = booleanPreferencesKey("hide_balances")
    private val appLockKey = booleanPreferencesKey("app_lock")

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

    /** Requires the device keyguard (biometric, PIN, pattern) before the ledger is shown. */
    val appLockEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[appLockKey] ?: false }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[appLockKey] = enabled }
    }

    /** Preferences captured into a backup. The seeded flag is deliberately not included. */
    suspend fun snapshot(): SettingsSnapshot {
        val prefs = context.settingsDataStore.data.first()
        return SettingsSnapshot(
            baseCurrency = prefs[baseCurrencyKey] ?: DEFAULT_BASE_CURRENCY,
            monthStartDay = prefs[monthStartDayKey] ?: 1,
            hideBalances = prefs[hideBalancesKey] ?: false,
            appLockEnabled = prefs[appLockKey] ?: false
        )
    }

    /**
     * Restores preferences from a backup, and marks the database as seeded so the default
     * categories are not re-created on top of the ones that just came back.
     */
    suspend fun restore(snapshot: SettingsSnapshot) {
        context.settingsDataStore.edit {
            it[baseCurrencyKey] = snapshot.baseCurrency
            it[monthStartDayKey] = snapshot.monthStartDay.coerceIn(1, MonthPeriod.MAX_START_DAY)
            it[hideBalancesKey] = snapshot.hideBalances
            it[appLockKey] = snapshot.appLockEnabled
            it[seededKey] = true
        }
    }

    companion object {
        const val DEFAULT_BASE_CURRENCY = "EUR"
    }
}
