
package com.example.exotrade.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exotrade.data.ApiService
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.data.SpeciesRepository
import com.example.exotrade.utils.EncryptionManager
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

class LoginViewModel(
    private val apiService: ApiService,
    private val sessionRepository: SessionRepository,
    private val speciesRepository: SpeciesRepository,
    private val encryptionManager: EncryptionManager
) : ViewModel() {
    private val _sessionVerified = MutableStateFlow<Boolean?>(null)
    val sessionVerified = _sessionVerified.asStateFlow()

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess = _loginSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun verifySession() {
        if (!sessionRepository.isLoggedIn()) {
            _sessionVerified.value = false
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val params = sessionRepository.authParams()
                val response: String = apiService.postForm("auth/auth", params + ("mode" to "verify"))
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    _sessionVerified.value = true
                } else {
                    sessionRepository.clearSession()
                    _sessionVerified.value = false
                }
            } catch (e: Exception) {
                if (e is ClientRequestException && e.response.status == HttpStatusCode.Unauthorized) {
                    sessionRepository.clearSession()
                }
                _sessionVerified.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun login(username: String, password: String, rememberMe: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val params = mapOf(
                    "username" to username,
                    "password" to password,
                    "mode" to "login"
                )
                val response: String = apiService.postForm("auth/auth", params)
                
                try {
                    val json = Json.parseToJsonElement(response).jsonObject
                    
                    if (json["status"]?.jsonPrimitive?.content == "success") {
                        val uuid = json["uuid"]?.jsonPrimitive?.content ?: ""
                        val token = json["auth_token"]?.jsonPrimitive?.content ?: ""
                        val isAdmin = json["is_admin"]?.jsonPrimitive?.content == "true"
                        val tier = json["subscription_tier"]?.jsonPrimitive?.int ?: 0
                        
                        sessionRepository.createLoginSession(uuid, token, username, isAdmin, tier, rememberMe)
                        _loginSuccess.value = true
                    } else {
                        _errorMessage.value = json["message"]?.jsonPrimitive?.content ?: "Login failed"
                    }
                } catch (e: Exception) {
                    // Server returned something that is not JSON (likely an HTML error page)
                    _errorMessage.value = "Server is currently down (Invalid response)"
                }
            } catch (e: Exception) {
                _errorMessage.value = when (e) {
                    is HttpRequestTimeoutException, is ConnectTimeoutException, is java.net.ConnectException -> 
                        "Server is currently down (Unreachable)"
                    else -> "Connection error"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun preloadSpecies() {
        viewModelScope.launch {
            speciesRepository.preloadCache()
        }
    }
}
