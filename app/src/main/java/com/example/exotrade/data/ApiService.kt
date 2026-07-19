package com.example.exotrade.data

import com.example.exotrade.utils.Helpers
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class ApiService(private val sessionRepository: SessionRepository) {
    val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 15000
        }
        defaultRequest {
            url(Helpers.getBaseUrl())
        }
        // Business-logic errors return JSON with status/message; let callers read the body.
        expectSuccess = false
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status == HttpStatusCode.Unauthorized) {
                    sessionRepository.clearSession(isExpired = true)
                }
            }
        }
    }

    suspend inline fun <reified T> get(url: String, params: Map<String, String?> = emptyMap()): T {
        return client.get(url) {
            params.forEach { (key, value) ->
                if (value != null) parameter(key, value)
            }
        }.body()
    }

    suspend inline fun <reified T> getWithParams(url: String, params: Map<String, String?> = emptyMap()): T {
        return client.get(url) {
            params.forEach { (key, value) ->
                if (value != null) parameter(key, value)
            }
        }.body()
    }

    suspend inline fun <reified T> postForm(url: String, params: Map<String, String?> = emptyMap()): T {
        return client.submitForm(
            url = url,
            formParameters = parameters {
                params.forEach { (key, value) ->
                    if (value != null) append(key, value)
                }
            }
        ).body()
    }
}
