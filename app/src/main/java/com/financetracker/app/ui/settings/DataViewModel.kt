package com.financetracker.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.FinanceApplication
import com.financetracker.app.data.backup.BackupFormatException
import com.financetracker.app.data.backup.BackupRepository
import com.financetracker.app.data.backup.RestoreSummary
import com.financetracker.app.data.settings.SettingsRepository
import com.financetracker.app.ui.common.FinanceViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DataUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

class DataViewModel(
    private val backup: BackupRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DataUiState())
    val state: StateFlow<DataUiState> = _state.asStateFlow()

    val appLockEnabled: StateFlow<Boolean> = settings.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAppLockEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setAppLockEnabled(enabled)
    }

    fun exportBackup(target: Uri) {
        runExport("Backup saved") { backup.exportBackup(target) }
    }

    fun exportCsv(target: Uri) {
        runExport("Exported to CSV") { backup.exportCsv(target) }
    }

    fun restore(source: Uri) = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null, isError = false) }
        try {
            val summary: RestoreSummary = backup.restore(source)
            _state.update {
                it.copy(
                    busy = false,
                    isError = false,
                    message = "Restored " + listOf(
                        plural(summary.transactions, "transaction"),
                        plural(summary.accounts, "account"),
                        plural(summary.categories, "category", "categories"),
                        plural(summary.budgets, "budget"),
                        plural(summary.rules, "recurring rule")
                    ).joinToString(", ") + "."
                )
            }
        } catch (e: BackupFormatException) {
            fail(e.message ?: "That file could not be read.")
        } catch (e: Exception) {
            fail("Restore failed: ${e.message ?: e::class.simpleName}")
        }
    }

    /** Shared wrapper for the two export paths, which differ only in wording and the call made. */
    private fun runExport(verb: String, block: suspend () -> Int) = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null, isError = false) }
        try {
            val count = block()
            _state.update {
                it.copy(
                    busy = false,
                    isError = false,
                    message = "$verb: ${plural(count, "transaction")}."
                )
            }
        } catch (e: Exception) {
            fail("Could not write the file: ${e.message ?: e::class.simpleName}")
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(busy = false, message = message, isError = true) }
    }

    /** "1 transaction" / "2 transactions" - irregular plurals are passed in explicitly. */
    private fun plural(count: Int, singular: String, plural: String = "${singular}s") =
        "$count ${if (count == 1) singular else plural}"

    fun clearMessage() = _state.update { it.copy(message = null, isError = false) }

    companion object {
        fun factory(app: FinanceApplication) = FinanceViewModelFactory(app) {
            DataViewModel(
                backup = it.backupRepository,
                settings = it.settingsRepository
            )
        }
    }
}
