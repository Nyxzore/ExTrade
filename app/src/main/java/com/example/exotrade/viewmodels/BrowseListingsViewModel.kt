package com.example.exotrade.viewmodels

import androidx.lifecycle.ViewModel
import com.example.exotrade.data.ApiService
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.models.Listing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BrowseListingsViewModel(
    private val apiService: ApiService,
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val _listings = MutableStateFlow<List<Listing>>(emptyList())
    val listings: StateFlow<List<Listing>> = _listings

    val isLoading = MutableStateFlow(false)
    val isRefreshing = MutableStateFlow(false)

    fun refresh() {
        // Implementation
    }

    fun loadNextPage() {
        // Implementation
    }

    fun setSearchQuery(query: String) {
        // Implementation
    }
}
