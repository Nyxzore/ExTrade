package com.example.exotrade.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.exotrade.models.Message
import com.example.exotrade.R
import com.example.exotrade.databinding.MsgItemMessageListingBinding
import com.example.exotrade.databinding.MsgItemMessageMediaBinding
import com.example.exotrade.databinding.MsgItemMessageReceivedBinding
import com.example.exotrade.databinding.MsgItemMessageSentBinding
import com.example.exotrade.utils.Helpers
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Adapter for displaying chat messages in a RecyclerView.
 * Supports different view types for sent, received, and listing-reference messages.
 */
class MessageAdapter(
    private val currentUserId: String,
    private val userClickListener: OnUserClickListener
) : ListAdapter<Message, MessageAdapter.MessageViewHolder>(DIFF_CALLBACK) {

    /**
     * Interface definition for a callback to be invoked when a user (sender) is clicked.
     */
    interface OnUserClickListener {
        fun onUserClick(userId: String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_LISTING -> MessageViewHolder.ListingViewHolder(MsgItemMessageListingBinding.inflate(inflater, parent, false))
            TYPE_SENT -> MessageViewHolder.SentViewHolder(MsgItemMessageSentBinding.inflate(inflater, parent, false))
            TYPE_IMAGE -> MessageViewHolder.MediaViewHolder(MsgItemMessageMediaBinding.inflate(inflater, parent, false))
            else -> MessageViewHolder.ReceivedViewHolder(MsgItemMessageReceivedBinding.inflate(inflater, parent, false))
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return when {
            message.isListingRef -> TYPE_LISTING
            message.isImageRef -> TYPE_IMAGE
            message.senderId == currentUserId -> TYPE_SENT
            else -> TYPE_RECEIVED
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val current = getItem(position)
        val previous = if (position > 0) getItem(position - 1) else null
        holder.bind(current, previous, userClickListener)
    }

    abstract class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(message: Message, previous: Message?, userClickListener: OnUserClickListener)

        protected fun shouldShowHeader(message: Message, previous: Message?): Boolean {
            if (previous == null || previous.senderId != message.senderId) return true
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val curDate = if (message.isSending || message.sentAt == "Just now") Date() else sdf.parse(message.sentAt)
                val prevDate = if (previous.isSending || previous.sentAt == "Just now") Date() else sdf.parse(previous.sentAt)
                if (curDate != null && prevDate != null) {
                    abs(curDate.time - prevDate.time) >= 60000
                } else true
            } catch (e: Exception) {
                message.sentAt != previous.sentAt
            }
        }

        protected fun bindHeader(
            message: Message,
            lblUsername: android.widget.TextView,
            lblTime: android.widget.TextView,
            imgSender: android.widget.ImageView,
            userClickListener: OnUserClickListener
        ) {
            lblUsername.text = message.senderUsername ?: "Unknown"
            val displayTime = formatTime(message.sentAt)
            lblTime.text = if (message.isSending) "Sending..." else displayTime
            Helpers.loadImage(message.senderProfilePic, imgSender, R.drawable.ic_person_24)

            if (imgSender is ShapeableImageView) {
                if (message.senderSubscriptionTier >= 1) {
                    imgSender.strokeWidth = Helpers.dpToPx(itemView.context, 1).toFloat()
                    imgSender.strokeColor = ContextCompat.getColorStateList(itemView.context, R.color.tier_1_orange)
                } else {
                    imgSender.strokeWidth = 0f
                }
            }

            val profileClick = View.OnClickListener {
                message.senderId?.let { userClickListener.onUserClick(it) }
            }
            imgSender.setOnClickListener(profileClick)
            lblUsername.setOnClickListener(profileClick)
        }

        protected fun updateMarginsAndPadding(root: View, showHeader: Boolean) {
            val content = (root as? ViewGroup)?.getChildAt(1)
            if (content != null && content.layoutParams is ViewGroup.MarginLayoutParams) {
                val lp = content.layoutParams as ViewGroup.MarginLayoutParams
                lp.marginStart = Helpers.dpToPx(itemView.context, if (showHeader) 12 else 56)
                content.layoutParams = lp
            }

            root.setPadding(
                root.paddingLeft,
                Helpers.dpToPx(itemView.context, if (showHeader) 6 else 0),
                root.paddingRight,
                Helpers.dpToPx(itemView.context, 2)
            )
        }

        private fun formatTime(rawTime: String?): String? {
            if (rawTime == null) return null
            try {
                if (rawTime.length > 10) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val date = sdf.parse(rawTime)
                    if (date != null) {
                        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
                    }
                }
            } catch (ignored: Exception) {
            }
            return rawTime
        }

        class SentViewHolder(private val binding: MsgItemMessageSentBinding) : MessageViewHolder(binding.root) {
            override fun bind(message: Message, previous: Message?, userClickListener: OnUserClickListener) {
                val showHeader = shouldShowHeader(message, previous)
                binding.headerContainer.visibility = if (showHeader) View.VISIBLE else View.GONE
                binding.imgSender.visibility = if (showHeader) View.VISIBLE else View.GONE
                updateMarginsAndPadding(binding.root, showHeader)
                if (showHeader) bindHeader(message, binding.lblUsername, binding.lblTime, binding.imgSender, userClickListener)
                binding.lblMessage.text = message.messageText
            }
        }

        class ReceivedViewHolder(private val binding: MsgItemMessageReceivedBinding) : MessageViewHolder(binding.root) {
            override fun bind(message: Message, previous: Message?, userClickListener: OnUserClickListener) {
                val showHeader = shouldShowHeader(message, previous)
                binding.headerContainer.visibility = if (showHeader) View.VISIBLE else View.GONE
                binding.imgSender.visibility = if (showHeader) View.VISIBLE else View.GONE
                updateMarginsAndPadding(binding.root, showHeader)
                if (showHeader) bindHeader(message, binding.lblUsername, binding.lblTime, binding.imgSender, userClickListener)
                binding.lblMessage.text = message.messageText
            }
        }

        class ListingViewHolder(private val binding: MsgItemMessageListingBinding) : MessageViewHolder(binding.root) {
            override fun bind(message: Message, previous: Message?, userClickListener: OnUserClickListener) {
                val showHeader = shouldShowHeader(message, previous)
                binding.headerContainer.visibility = if (showHeader) View.VISIBLE else View.GONE
                binding.imgSender.visibility = if (showHeader) View.VISIBLE else View.GONE
                updateMarginsAndPadding(binding.root, showHeader)
                if (showHeader) bindHeader(message, binding.lblUsername, binding.lblTime, binding.imgSender, userClickListener)
                
                val listingCard = binding.root.findViewById<View>(R.id.listingCard)
                listingCard?.visibility = View.VISIBLE
                listingCard?.findViewById<android.widget.TextView>(R.id.lblCommonName)?.text = message.listingCommonName
                listingCard?.findViewById<android.widget.TextView>(R.id.lblScientificName)?.text = message.listingScientificName
                listingCard?.findViewById<android.widget.TextView>(R.id.lblPrice)?.text = message.listingPrice
                listingCard?.findViewById<android.widget.TextView>(R.id.lblDescription)?.text = ""
                listingCard?.findViewById<android.widget.ImageView>(R.id.imgCoverImage)?.let { 
                    Helpers.loadImage(message.listingImageUrl, it) 
                }
                listingCard?.findViewById<View>(R.id.lblSoldBadge)?.visibility = View.GONE
            }
        }

        class MediaViewHolder(private val binding: MsgItemMessageMediaBinding) : MessageViewHolder(binding.root) {
            override fun bind(message: Message, previous: Message?, userClickListener: OnUserClickListener) {
                val showHeader = shouldShowHeader(message, previous)
                binding.headerContainer.visibility = if (showHeader) View.VISIBLE else View.GONE
                binding.imgSender.visibility = if (showHeader) View.VISIBLE else View.GONE
                updateMarginsAndPadding(binding.root, showHeader)
                if (showHeader) bindHeader(message, binding.lblUsername, binding.lblTime, binding.imgSender, userClickListener)

                val url = message.attachmentUrl
                Helpers.loadImage(url, binding.imgContent)

                binding.imgContent.setOnClickListener {
                    val dialog = android.app.Dialog(itemView.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                    val iv = android.widget.ImageView(itemView.context)
                    iv.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    Helpers.loadImage(url, iv)
                    dialog.setContentView(iv)
                    dialog.show()
                }
            }
        }
    }

    companion object {
        const val TYPE_SENT = 1
        const val TYPE_RECEIVED = 2
        const val TYPE_LISTING = 3
        const val TYPE_IMAGE = 4

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
                return oldItem == newItem
            }
        }
    }
}
