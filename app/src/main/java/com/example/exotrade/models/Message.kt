package com.example.exotrade.models

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    var id: String? = null,
    val conversationId: String? = null,
    val senderId: String? = null,
    val messageText: String? = null,
    val encryptedBody: String? = null,
    val nonce: String? = null,
    var sentAt: String? = null,
    var isSending: Boolean = false,
    val isListingRef: Boolean = false,
    val listingId: String? = null,
    val listingCommonName: String? = null,
    val listingScientificName: String? = null,
    val listingPrice: String? = null,
    val listingImageUrl: String? = null,
    val senderUsername: String? = null,
    val senderProfilePic: String? = null,
    val senderSubscriptionTier: Int = 0
) {
    companion object {
        fun createListingRef(id: String, common: String?, scientific: String?, price: String?, imageUrl: String?): String {
            return "LISTING_REF:$id|$common|$scientific|$price|$imageUrl"
        }
    }
}
