package com.example.exotrade.activities.messaging

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.exotrade.Adapters.ConversationAdapter
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.models.Conversation
import com.example.exotrade.databinding.MsgActivityInboxBinding
import com.example.exotrade.data.SessionRepository
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int

/**
 * Fragment displaying the list of all active user conversations.
 * Summarizes the last message, unread status, and other participant's identity.
 */
class InboxFragment : Fragment() {

    private var _binding: MsgActivityInboxBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var session: SessionRepository
    private lateinit var adapter: ConversationAdapter
    private var pollJob: Job? = null

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
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) {
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
        } else {
            fetchConversations()
            startPolling()
        }
    }

    private fun startPolling() {
        stopPolling() // Ensure only one job is active
        pollJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(3000)
                fetchConversations()
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun fetchConversations() {
        binding.progressBar.visibility = View.VISIBLE
        val params = session.authParams()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("messaging/get_conversations", params)
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

                        list.add(Conversation(
                            id = c["conversation_id"]?.jsonPrimitive?.content ?: "",
                            otherUserId = c["other_user_id"]?.jsonPrimitive?.content ?: "",
                            otherUsername = c["other_username"]?.jsonPrimitive?.content ?: "",
                            otherProfilePic = c["other_profile_pic"]?.jsonPrimitive?.content,
                            otherPublicKey = otherPubKey,
                            lastMessage = lastMsg,
                            lastMessageTime = c["last_message_time"]?.jsonPrimitive?.content,
                            unreadCount = c["unread_count"]?.jsonPrimitive?.int ?: 0,
                            subscriptionTier = c["other_subscription_tier"]?.jsonPrimitive?.int ?: 0
                        ))
                    }
                    adapter.submitList(list)
                    binding.lblEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    Toast.makeText(requireContext(), json["message"]?.jsonPrimitive?.content ?: "Failed to load messages", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error loading conversations", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
