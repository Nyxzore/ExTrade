package com.example.exotrade

import android.content.Context
import com.example.exotrade.data.ApiService
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.data.SpeciesRepository
import com.example.exotrade.utils.EncryptionManager

/**
 * Service locator for providing singletons.
 */
class SharedContainer(context: Context) {
    val apiService: ApiService by lazy {
        ApiService()
    }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(context)
    }

    val speciesRepository: SpeciesRepository by lazy {
        SpeciesRepository(apiService)
    }

    val encryptionManager: EncryptionManager by lazy {
        EncryptionManager()
    }
}
