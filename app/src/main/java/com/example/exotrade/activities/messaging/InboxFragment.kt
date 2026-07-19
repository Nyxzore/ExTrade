package com.example.exotrade.activities.messaging

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.exotrade.Adapters.ConversationAdapter
import com.example.exotrade.Adapters.FriendAdapter
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.activities.profile.Profile
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.databinding.MsgActivityInboxBinding
import com.example.exotrade.models.Conversation
import com.example.exotrade.models.User
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Fragment displaying the list of all active user conversations.
 * Summarizes the last message, unread status, and other participant's identity.
 * Search opens a DM with friends, or the profile for non-friends.
 */
class InboxFragment : Fragment() {

    private var _binding: MsgActivityInboxBinding? = null
    private val binding get() = _binding!!

    private lateinit var session: SessionRepository
    private lateinit var adapter: ConversationAdapter
    private lateinit var searchAdapter: FriendAdapter
    private var pollJob: Job? = null
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var isSearching = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MsgActivityInboxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        session = ExoTradeApplication.container.sessionRepository

        binding.rvConversations.layoutManager = LinearLayoutManager(requireContext())
        adapter = ConversationAdapter(object : ConversationAdapter.OnConversationClickListener {
            override fun onConversationClick(conversation: Conversation) {
                val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                    putExtra("conversation_id", conversation.id)
                    putExtra("other_username", conversation.otherUsername)
                    putExtra("other_profile_pic", conversation.otherProfilePic)
                    putExtra("other_public_key", conversation.otherPublicKey)
                }
                startActivity(intent)
            }
        })
        binding.rvConversations.adapter = adapter

        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        searchAdapter = FriendAdapter(ArrayList(), FriendAdapter.Mode.SEARCH, object : FriendAdapter.OnFriendActionListener {
            override fun onUserClick(user: User) = onSearchUserClick(user)
            override fun onActionClick(user: User) {}
            override fun onDeclineClick(user: User) {}
        })
        binding.rvSearchResults.adapter = searchAdapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                if (query.isEmpty()) {
                    showConversationsMode()
                } else {
                    isSearching = true
                    binding.rvConversations.visibility = View.GONE
                    binding.rvSearchResults.visibility = View.VISIBLE
                    searchRunnable = Runnable { searchUsers(query) }
                    searchHandler.postDelayed(searchRunnable!!, 400)
                }
            }
        })
    }

    private fun showConversationsMode() {
        isSearching = false
        binding.rvSearchResults.visibility = View.GONE
        binding.rvConversations.visibility = View.VISIBLE
        fetchConversations()
    }

    private fun onSearchUserClick(user: User) {
        if (user.isFriend || user.friendshipStatus == "friends") {
            openConversationWith(user)
        } else {
            val intent = Intent(requireContext(), Profile::class.java)
            intent.putExtra("user_id", user.id)
            startActivity(intent)
        }
    }

    private fun openConversationWith(user: User) {
        val params = session.authParams().toMutableMap()
        params["target_user_id"] = user.id ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm(
                    "messaging/start_or_get_conversation",
                    params
                )
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    val otherUser = json["other_user"]?.jsonObject
                    val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                        putExtra("conversation_id", json["conversation_id"]?.jsonPrimitive?.content)
                        putExtra("other_username", otherUser?.get("username")?.jsonPrimitive?.content ?: user.username)
                        putExtra("other_profile_pic", otherUser?.get("profile_pic")?.takeUnless { it is JsonNull }?.jsonPrimitive?.content ?: user.profilePic)
                        putExtra("other_public_key", otherUser?.get("public_key")?.takeUnless { it is JsonNull }?.jsonPrimitive?.content)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(
                        requireContext(),
                        json["message"]?.jsonPrimitive?.content ?: "Could not open chat",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Could not open chat", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchUsers(query: String) {
        binding.progressBar.visibility = View.VISIBLE
        val params = session.authParams().toMutableMap()
        params["query"] = query

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("friends/search_users", params)
                if (!isAdded || !isSearching) return@launch
                binding.progressBar.visibility = View.GONE
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    val arr = json["users"]?.jsonArray
                    val list = ArrayList<User>()
                    arr?.forEach { element ->
                        val u = element.jsonObject
                        val friendshipStatus = u["friendship_status"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
                        val picEl = u["profile_pic"]
                        list.add(
                            User(
                                id = u["id"]?.jsonPrimitive?.content,
                                username = u["username"]?.jsonPrimitive?.content,
                                profilePic = if (picEl == null || picEl is JsonNull) null else picEl.jsonPrimitive.content,
                                subscriptionTier = u["subscription_tier"]?.jsonPrimitive?.int ?: 0,
                                friendshipStatus = friendshipStatus,
                                isFriend = u["is_friend"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.boolean
                                    ?: (friendshipStatus == "friends")
                            )
                        )
                    }
                    searchAdapter.setUsers(list)
                    binding.lblEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    if (list.isEmpty()) {
                        binding.lblEmpty.setText(R.string.no_results_found)
                    }
                } else {
                    Toast.makeText(requireContext(), json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (!isAdded) return@launch
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Search failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden && !isSearching) {
            fetchConversations()
            startPolling()
        }
    }

    override fun onPause() {
        super.onPause()
        stopPolling()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            stopPolling()
        } else if (!isSearching) {
            fetchConversations()
            startPolling()
        }
    }

    private fun startPolling() {
        stopPolling()
        pollJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(3000)
                if (!isSearching) fetchConversations()
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun fetchConversations() {
        if (isSearching) return
        binding.progressBar.visibility = View.VISIBLE
        val params = session.authParams()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("messaging/get_conversations", params)
                if (!isAdded || isSearching) return@launch
                binding.progressBar.visibility = View.GONE

                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    val arr = json["conversations"]?.jsonArray
                    val list = mutableListOf<Conversation>()

                    val encryptionManager = ExoTradeApplication.container.encryptionManager
                    val myPrivKeyBase64 = session.getIdentityPrivateKey()
                    val myPrivKey = if (myPrivKeyBase64 != null) Base64.decode(myPrivKeyBase64, Base64.NO_WRAP) else null

                    arr?.forEach { element ->
                        val c = element.jsonObject

                        var lastMsg = c["last_message"]?.jsonPrimitive?.content ?: ""
                        val isEncrypted = c["is_last_message_encrypted"]?.jsonPrimitive?.boolean ?: true
                        val nonce = c["last_message_nonce"]?.jsonPrimitive?.content ?: ""
                        val otherPubKey = c["other_public_key"]?.jsonPrimitive?.content ?: ""

                        if (isEncrypted && lastMsg.isNotEmpty() && nonce.isNotEmpty() && otherPubKey.isNotEmpty() && myPrivKey != null) {
                            val decrypted = encryptionManager.decryptMessage(lastMsg, nonce, otherPubKey, myPrivKey)
                            lastMsg = decrypted ?: "[Encrypted]"
                        }

                        list.add(
                            Conversation(
                                id = c["conversation_id"]?.jsonPrimitive?.content ?: "",
                                otherUserId = c["other_user_id"]?.jsonPrimitive?.content ?: "",
                                otherUsername = c["other_username"]?.jsonPrimitive?.content ?: "",
                                otherProfilePic = c["other_profile_pic"]?.jsonPrimitive?.content,
                                otherPublicKey = otherPubKey,
                                lastMessage = lastMsg,
                                lastMessageTime = c["last_message_time"]?.jsonPrimitive?.content,
                                unreadCount = c["unread_count"]?.jsonPrimitive?.int ?: 0,
                                subscriptionTier = c["other_subscription_tier"]?.jsonPrimitive?.int ?: 0
                            )
                        )
                    }
                    adapter.submitList(list)
                    binding.lblEmpty.text = "No messages yet"
                    binding.lblEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    Toast.makeText(requireContext(), json["message"]?.jsonPrimitive?.content ?: "Failed to load messages", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("InboxFragment", "fetchConversations failed", e)
                if (!isAdded) return@launch
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error loading conversations", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        super.onDestroyView()
        _binding = null
    }
}
