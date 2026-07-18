package com.example.exotrade.data

class SpeciesRepository(private val apiService: ApiService) {
    suspend fun preloadCache() {
        // Implementation
    }

    suspend fun syncFromServer(force: Boolean = false) {
        // Implementation
    }

    fun getNames(isScientific: Boolean = false): List<String> = emptyList()
    fun getNames(query: String, isScientific: Boolean = false): List<String> = emptyList()
    fun getScientificName(commonName: String): String? = null
    fun getCommonName(scientificName: String): String? = null
    fun isValidSpecies(name: String): Boolean = true
    fun getLsid(name: String): String? = null
}
