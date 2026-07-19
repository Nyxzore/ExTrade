package com.example.exotrade.models

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    var id: String? = null,
    val conversationId: String? = null,
    val senderId: String? = null,
    var messageText: String? = null,
    val encryptedBody: String? = null,
    val nonce: String? = null,
    var sentAt: String? = null,
    var isSending: Boolean = false,
    var isListingRef: Boolean = false,
    var isImageRef: Boolean = false,
    var attachmentUrl: String? = null,
    var listingId: String? = null,
    var listingCommonName: String? = null,
    var listingScientificName: String? = null,
    var listingPrice: String? = null,
    var listingImageUrl: String? = null,
    val senderUsername: String? = null,
    val senderProfilePic: String? = null,
    val senderSubscriptionTier: Int = 0
) {
    sealed class ParsedRef {
        data class Listing(val id: String, val common: String?, val scientific: String?, val price: String?, val imageUrl: String?) : ParsedRef()
        data class Image(val url: String) : ParsedRef()

        fun getShortLabel(): String = when (this) {
            is Listing -> "Shared a listing: ${common ?: scientific ?: "Animal"}"
            is Image -> "📷 Photo"
        }
    }

    companion object {
        private const val PREFIX = "REF:"
        private const val TYPE_LISTING = "LISTING:"
        private const val TYPE_IMAGE = "IMAGE:"

        fun createListingRef(id: String, common: String?, scientific: String?, price: String?, imageUrl: String?): String {
            return "${PREFIX}${TYPE_LISTING}$id|$common|$scientific|$price|$imageUrl"
        }

        fun createImageRef(url: String): String = "${PREFIX}${TYPE_IMAGE}$url"

        fun parseRef(decryptedBody: String): ParsedRef? {
            if (!decryptedBody.startsWith(PREFIX)) return null
            val content = decryptedBody.substring(PREFIX.length)

            return when {
                content.startsWith(TYPE_LISTING) -> {
                    val data = content.substring(TYPE_LISTING.length).split("|")
                    if (data.size >= 5) {
                        ParsedRef.Listing(data[0], data[1], data[2], data[3], data[4])
                    } else null
                }
                content.startsWith(TYPE_IMAGE) -> {
                    ParsedRef.Image(content.substring(TYPE_IMAGE.length))
                }
                else -> null
            }
        }
    }
}
