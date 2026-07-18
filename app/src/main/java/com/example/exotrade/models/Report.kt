package com.example.exotrade.models

import kotlinx.serialization.Serializable

@Serializable
data class Report(
    val id: String? = null,
    val reporterId: String? = null,
    val reporterName: String? = null,
    val targetType: String? = null, // "user" or "listing"
    val targetId: String? = null,
    val reason: String? = null,
    val details: String? = null,
    val createdAt: String? = null,
    val status: String = "pending"
)
