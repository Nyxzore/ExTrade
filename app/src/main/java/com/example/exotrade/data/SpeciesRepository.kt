package com.example.exotrade.data

import android.content.Context
import com.example.exotrade.models.Species
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File

class SpeciesRepository(
    private val apiService: ApiService,
    private val sessionRepository: SessionRepository,
    private val context: Context
) {
    private var speciesCache: List<Species> = emptyList()
    private val cacheFile = File(context.filesDir, "species_cache.json")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun preloadCache() {
        if (speciesCache.isNotEmpty()) return
        
        if (cacheFile.exists()) {
            try {
                val content = cacheFile.readText()
                speciesCache = json.decodeFromString<List<Species>>(content)
                if (speciesCache.isNotEmpty()) return
            } catch (e: Exception) {
                cacheFile.delete()
            }
        }
        syncFromServer(false)
    }

    suspend fun syncFromServer(force: Boolean = false) {
        if (!sessionRepository.isLoggedIn()) return
        if (!force && speciesCache.isNotEmpty()) return
        
        try {
            val response: String = apiService.get("listings/get_all_species", sessionRepository.authParams())
            val root = Json.parseToJsonElement(response).jsonObject
            if (root["status"]?.toString()?.contains("success") == true) {
                val data = root["species"]?.jsonArray ?: return
                val list = data.map { json.decodeFromJsonElement<Species>(it) }
                speciesCache = list
                cacheFile.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Species.serializer()), list))
            }
        } catch (e: Exception) {
            // Log error
        }
    }

    fun getNames(isScientific: Boolean = false): List<String> {
        return if (isScientific) {
            speciesCache.map { it.scientificName }
        } else {
            speciesCache.mapNotNull { it.commonName }
        }
    }

    fun getNames(query: String, isScientific: Boolean = false): List<String> {
        val all = getNames(isScientific)
        return all.filter { it.contains(query, ignoreCase = true) }
    }

    fun getScientificName(commonName: String): String? {
        return speciesCache.find { it.commonName?.equals(commonName, ignoreCase = true) == true }?.scientificName
    }

    fun getCommonName(scientificName: String): String? {
        return speciesCache.find { it.scientificName.equals(scientificName, ignoreCase = true) }?.commonName
    }

    fun isValidSpecies(name: String): Boolean {
        return speciesCache.any { it.scientificName.equals(name, ignoreCase = true) || it.commonName?.equals(name, ignoreCase = true) == true }
    }

    fun getLsid(name: String): String? {
        return speciesCache.find { it.scientificName.equals(name, ignoreCase = true) || it.commonName?.equals(name, ignoreCase = true) == true }?.speciesLsid
    }
}
