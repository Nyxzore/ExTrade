package com.example.exotrade.utils

/**
 * Data class representing user information retrieved and verified
 * through the Digital Credentials API.
 */
data class VerifiedUserInfo(
    val email: String,
    val displayName: String? = null
)
