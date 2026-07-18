package com.example.exotrade.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val profilePic: String? = null,
    val bio: String? = null,
    val subscriptionTier: Int = 0,
    val isAdmin: Boolean = false,
    val whatsapp: String? = null,
    val facebook: String? = null,
    val instagram: String? = null,
    val publicKey: String? = null
)
