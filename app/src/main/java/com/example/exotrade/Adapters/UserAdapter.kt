package com.example.exotrade.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.exotrade.models.User
import com.example.exotrade.R
import com.example.exotrade.databinding.MsgItemConversationBinding
import com.example.exotrade.utils.Helpers

/**
 * Adapter for displaying a list of users in a RecyclerView.
 * Reuses the conversation item layout for a consistent UI.
 */
class UserAdapter(
    private var users: List<User>,
    private val listener: OnUserClickListener
) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {

    /**
     * Interface definition for a callback to be invoked when a user is clicked.
     */
    interface OnUserClickListener {
        fun onUserClick(user: User)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = MsgItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user, listener)
    }

    override fun getItemCount(): Int = users.size

    fun setUsers(users: List<User>) {
        this.users = users
        notifyDataSetChanged()
    }

    class ViewHolder(private val binding: MsgItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User, listener: OnUserClickListener) {
            with(binding) {
                lblUsername.text = user.username
                lblLastMessage.visibility = View.GONE
                lblTime.visibility = View.GONE
                imgListingThumbnail.visibility = View.GONE

                Helpers.loadImage(user.profilePic, imgProfile)

                if (user.subscriptionTier >= 1) {
                    imgProfile.strokeWidth = Helpers.dpToPx(itemView.context, 1).toFloat()
                    imgProfile.strokeColor = ContextCompat.getColorStateList(itemView.context, R.color.tier_1_orange)
                } else {
                    imgProfile.strokeWidth = 0f
                }

                root.setOnClickListener { listener.onUserClick(user) }
            }
        }
    }
}
