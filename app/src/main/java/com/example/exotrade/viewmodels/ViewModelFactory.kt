package com.example.exotrade.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.SharedContainer

/**
 * Factory for creating ViewModels with dependencies from the [SharedContainer].
 */
class ViewModelFactory(
    private val container: SharedContainer = ExoTradeApplication.container
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(BrowseListingsViewModel::class.java) -> {
                BrowseListingsViewModel(
                    container.apiService,
                    container.sessionRepository
                ) as T
            }
            modelClass.isAssignableFrom(LoginViewModel::class.java) -> {
                LoginViewModel(
                    container.apiService,
                    container.sessionRepository,
                    container.speciesRepository,
                    container.encryptionManager
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
