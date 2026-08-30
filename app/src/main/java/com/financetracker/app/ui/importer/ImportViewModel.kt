package com.financetracker.app.ui.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.FinanceApplication
import com.financetracker.app.data.account.AccountRepository
import com.financetracker.app.data.account.AccountWithBalance
import com.financetracker.app.data.importer.DateStyle
import com.financetracker.app.data.importer.ImportField
import com.financetracker.app.data.importer.ImportPlan
import com.financetracker.app.data.importer.ImportPreview
import com.financetracker.app.data.importer.ImportRepository
import com.financetracker.app.data.importer.ImportResult
import com.financetracker.app.data.importer.LoadedCsv
import com.financetracker.app.data.settings.SettingsRepository
import com.financetracker.app.ui.common.FinanceViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which step of the import the user is on. */
enum class ImportStage { PICK, MAP, DONE }

data class ImportUiState(
    val stage: ImportStage = ImportStage.PICK,
    val busy: Boolean = false,
    val fileName: String? = null,
    val csv: LoadedCsv? = null,
    val mapping: Map<ImportField, Int> = emptyMap(),
    val dateStyle: DateStyle = DateStyle.ISO,
    val dateStyleWasAmbiguous: Boolean = false,
    val preview: ImportPreview? = null,
    val accounts: List<AccountWithBalance> = emptyList(),
    val defaultAccountId: Long? = null,
    val createMissing: Boolean = true,
    val skipDuplicates: Boolean = true,
    val result: ImportResult? = null,
    val error: String? = null
) {
    val header: List<String> get() = csv?.header.orEmpty()

    val canImport: Boolean
        get() = ImportField.entries.filter { it.required }.all { it in mapping } &&
            defaultAccountId != null &&
            (preview?.usable?.isNotEmpty() == true)
}

class ImportViewModel(
    private val importer: ImportRepository,
    private val accounts: AccountRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val list = accounts.activeAccounts.first()
            _state.update { it.copy(accounts = list, defaultAccountId = list.firstOrNull()?.id) }
        }
    }

    fun load(uri: Uri, displayName: String?) = viewModelScope.launch {
        _state.update { it.copy(busy = true, error = null) }
        try {
            val csv = importer.load(uri)
            val mapping = ImportPlan.detectMapping(csv.header)
            val dateColumn = mapping[ImportField.DATE]
            val (style, ambiguous) = if (dateColumn != null) {
                ImportPlan.detectDateStyle(csv.rows, dateColumn)
            } else {
                DateStyle.ISO to false
            }
            _state.update {
                it.copy(
                    busy = false,
                    stage = ImportStage.MAP,
                    fileName = displayName,
                    csv = csv,
                    mapping = mapping,
                    dateStyle = style,
                    dateStyleWasAmbiguous = ambiguous
                )
            }
            rebuildPreview()
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = e.message ?: "That file could not be read.") }
        }
    }

    fun setColumn(field: ImportField, columnIndex: Int?) {
        _state.update { current ->
            val mapping = current.mapping.toMutableMap()
            if (columnIndex == null) mapping.remove(field) else mapping[field] = columnIndex
            current.copy(mapping = mapping)
        }
        // Changing the date column changes what layout the file appears to use.
        if (field == ImportField.DATE) redetectDateStyle()
        rebuildPreview()
    }

    fun setDateStyle(style: DateStyle) {
        _state.update { it.copy(dateStyle = style, dateStyleWasAmbiguous = false) }
        rebuildPreview()
    }

    fun setDefaultAccount(id: Long) {
        _state.update { it.copy(defaultAccountId = id) }
        rebuildPreview()
    }

    fun setCreateMissing(create: Boolean) {
        _state.update { it.copy(createMissing = create) }
        rebuildPreview()
    }

    fun setSkipDuplicates(skip: Boolean) = _state.update { it.copy(skipDuplicates = skip) }

    private fun redetectDateStyle() {
        val current = _state.value
        val csv = current.csv ?: return
        val dateColumn = current.mapping[ImportField.DATE] ?: return
        val (style, ambiguous) = ImportPlan.detectDateStyle(csv.rows, dateColumn)
        _state.update { it.copy(dateStyle = style, dateStyleWasAmbiguous = ambiguous) }
    }

    private fun rebuildPreview() = viewModelScope.launch {
        val current = _state.value
        val csv = current.csv ?: return@launch
        val (knownAccounts, knownCategories) = importer.knownNames()
        val defaultAccount = current.accounts.firstOrNull { it.id == current.defaultAccountId }

        val preview = ImportPlan.build(
            rows = csv.rows,
            mapping = current.mapping,
            dateStyle = current.dateStyle,
            dateStyleWasAmbiguous = current.dateStyleWasAmbiguous,
            defaultCurrency = defaultAccount?.currencyCode ?: settings.currentBaseCurrency(),
            defaultAccountName = defaultAccount?.name.orEmpty(),
            knownAccounts = knownAccounts,
            knownCategories = knownCategories
        )
        _state.update { it.copy(preview = preview) }
    }

    fun commit() = viewModelScope.launch {
        val current = _state.value
        val preview = current.preview ?: return@launch
        val accountId = current.defaultAccountId ?: return@launch

        _state.update { it.copy(busy = true, error = null) }
        try {
            val result = importer.commit(
                rows = preview.usable,
                defaultAccountId = accountId,
                createMissing = current.createMissing,
                skipDuplicates = current.skipDuplicates
            )
            _state.update {
                it.copy(
                    busy = false,
                    stage = ImportStage.DONE,
                    result = result.copy(rowsSkipped = preview.skipped.size)
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = e.message ?: "The import failed.") }
        }
    }

    fun reset() = _state.update {
        ImportUiState(accounts = it.accounts, defaultAccountId = it.defaultAccountId)
    }

    companion object {
        fun factory(app: FinanceApplication) = FinanceViewModelFactory(app) {
            ImportViewModel(
                importer = it.importRepository,
                accounts = it.accountRepository,
                settings = it.settingsRepository
            )
        }
    }
}
