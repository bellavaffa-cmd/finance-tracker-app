package com.financetracker.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.financetracker.app.FinanceApplication

/**
 * One factory for every screen. The app has no DI framework and does not need one - repositories
 * are already singletons on [FinanceApplication], so all a factory has to do is hand it over.
 */
class FinanceViewModelFactory(
    private val application: FinanceApplication,
    private val builder: (FinanceApplication) -> ViewModel
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = builder(application) as T
}
