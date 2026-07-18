package com.example.exotrade.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.exotrade.models.Conversation
import com.example.exotrade.R
import com.example.exotrade.databinding.MsgItemConversationBinding
import com.example.exotrade.utils.Helpers
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for displaying a list of conversations in a RecyclerView.
 * This adapter uses [ListAdapter] with [DiffUtil] for efficient updates.
 */
class ConversationAdapter(private val listener: OnConversationClickListener) :
    ListAdapter<Conversation, ConversationAdapter.ViewHolder>(DIFF_CALLBACK) {

    /**
     * Interface definition for a callback to be invoked when a conversation is clicked.
     */
    interface OnConversationClickListener {
        /**
         * Called when a conversation has been clicked.
         *
         * @param conversation The conversation object that was clicked.
         */
        fun onConversationClick(conversation: Conversation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = MsgItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = getItem(position)
        holder.bind(conversation, listener)
    }

    class ViewHolder(private val binding: MsgItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: Conversation, listener: OnConversationClickListener) {
            with(binding) {
                lblUsername.text = conversation.otherUsername

                val lastMsg = conversation.lastMessage
                if (lastMsg != null && lastMsg.startsWith("[LISTING_REF:") && lastMsg.endsWith("]")) {
                    try {
                        val json = JSONObject(lastMsg.substring(13, lastMsg.length - 1))
                        val rawName = json.optString("common_name", "")
                        val listingName = if (rawName.isEmpty() || rawName == "null") {
                            json.optString("scientific_name", "Animal")
                        } else rawName

                        lblLastMessage.text = itemView.context.getString(R.string.shared_listing_preview, listingName)

                        val thumbUrl = json.optString("image_url", "")
                        if (thumbUrl.isNotEmpty() && thumbUrl != "null") {
                            imgListingThumbnail.visibility = View.VISIBLE
                            Helpers.loadImage(thumbUrl, imgListingThumbnail)
                        } else {
                            imgListingThumbnail.visibility = View.GONE
                        }
                    } catch (e: Exception) {
                        lblLastMessage.setText(R.string.shared_a_listing)
                        imgListingThumbnail.visibility = View.GONE
                    }
                } else {
                    lblLastMessage.text = lastMsg
                    imgListingThumbnail.visibility = View.GONE
                }

                var formattedTime = conversation.lastMessageTime
                try {
                    if (formattedTime != null && formattedTime.length > 10) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        val date = sdf.parse(formattedTime)
                        if (date != null) {
                            formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
                        }
                    }
                } catch (ignored: Exception) {
                }
                lblTime.text = formattedTime

                if (conversation.unreadCount > 0) {
                    badgeUnread.visibility = View.VISIBLE
                    lblUnreadCount.text = conversation.unreadCount.toString()
                } else {
                    badgeUnread.visibility = View.GONE
                }

                Helpers.loadImage(conversation.otherProfilePic, imgProfile, R.drawable.ic_person_24)

                // Tier 1 outline logic
                if (conversation.subscriptionTier >= 1) {
                    imgProfile.strokeWidth = Helpers.dpToPx(itemView.context, 1).toFloat()
                    imgProfile.strokeColor = ContextCompat.getColorStateList(itemView.context, R.color.tier_1_orange)
                } else {
                    imgProfile.strokeWidth = 0f
                }

                root.setOnClickListener { listener.onConversationClick(conversation) }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Conversation>() {
            override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
                return oldItem.lastMessage == newItem.lastMessage &&
                        oldItem.unreadCount == newItem.unreadCount &&
                        oldItem.lastMessageTime == newItem.lastMessageTime
            }
        }
    }
}
