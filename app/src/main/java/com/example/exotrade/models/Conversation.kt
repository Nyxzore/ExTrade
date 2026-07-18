package com.example.exotrade.models

import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: String,
    val otherUserId: String,
    val otherUsername: String,
    val otherProfilePic: String? = null,
    val otherPublicKey: String? = null,
    val lastMessage: String? = null,
    val lastMessageTime: String? = null,
    val unreadCount: Int = 0,
    val subscriptionTier: Int = 0
)
