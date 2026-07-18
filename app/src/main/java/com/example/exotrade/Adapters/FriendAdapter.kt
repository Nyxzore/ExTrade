package com.example.exotrade.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.exotrade.models.User
import com.example.exotrade.R
import com.example.exotrade.databinding.ProfileItemFriendBinding
import com.example.exotrade.utils.Helpers

/**
 * Adapter for displaying a list of users (friends, requests, search results, or admin view) in a RecyclerView.
 */
class FriendAdapter(
    private var users: List<User>,
    private var mode: Mode,
    private val listener: OnFriendActionListener
) : RecyclerView.Adapter<FriendAdapter.ViewHolder>() {

    /**
     * Enum representing the different modes the adapter can operate in.
     */
    enum class Mode { FRIENDS, REQUESTS, SEARCH, ADMIN }

    /**
     * Interface definition for a callback to be invoked when actions are performed on a user item.
     */
    interface OnFriendActionListener {
        fun onUserClick(user: User)
        fun onActionClick(user: User)
        fun onDeclineClick(user: User)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ProfileItemFriendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user, mode, listener)
    }

    override fun getItemCount(): Int = users.size

    fun setUsers(users: List<User>) {
        this.users = users
        notifyDataSetChanged()
    }

    fun setMode(mode: Mode) {
        this.mode = mode
        notifyDataSetChanged()
    }

    class ViewHolder(private val binding: ProfileItemFriendBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User, mode: Mode, listener: OnFriendActionListener) {
            with(binding) {
                lblUsername.text = user.username
                Helpers.loadImage(user.profilePic, imgProfile, R.drawable.ic_person_24)

                if (user.subscriptionTier >= 1) {
                    imgProfile.strokeWidth = Helpers.dpToPx(itemView.context, 1).toFloat()
                    imgProfile.strokeColor = ContextCompat.getColorStateList(itemView.context, R.color.tier_1_orange)
                } else {
                    imgProfile.strokeWidth = 0f
                }

                root.setOnClickListener { listener.onUserClick(user) }

                btnDecline.visibility = View.GONE
                when (mode) {
                    Mode.FRIENDS -> {
                        btnAction.visibility = View.VISIBLE
                        btnAction.setText(R.string.remove)
                        btnAction.icon = null
                    }
                    Mode.REQUESTS -> {
                        btnAction.visibility = View.VISIBLE
                        btnAction.setText(R.string.accept)
                        btnAction.setIconResource(R.drawable.ic_check)
                        btnDecline.visibility = View.VISIBLE
                    }
                    Mode.ADMIN -> {
                        btnAction.visibility = View.VISIBLE
                        btnAction.text = "Ban"
                        btnAction.setIconResource(android.R.drawable.ic_lock_lock)
                        btnAction.setIconTint(ContextCompat.getColorStateList(itemView.context, R.color.tier_1_orange))
                    }
                    Mode.SEARCH -> {
                        btnAction.visibility = View.GONE
                    }
                }

                btnAction.setOnClickListener { listener.onActionClick(user) }
                btnDecline.setOnClickListener { listener.onDeclineClick(user) }
            }
        }
    }
}
