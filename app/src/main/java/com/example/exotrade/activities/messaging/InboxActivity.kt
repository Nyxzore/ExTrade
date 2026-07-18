package com.example.exotrade.activities.messaging

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.exotrade.Adapters.ConversationAdapter
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.models.Conversation
import com.example.exotrade.R
import com.example.exotrade.databinding.MsgActivityInboxBinding
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.utils.Helpers
import com.example.exotrade.utils.NavigationHelper
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int

/**
 * Activity displaying the list of all active user conversations.
 * Summarizes the last message, unread status, and other participant's identity.
 */
class InboxActivity : AppCompatActivity() {

    private lateinit var binding: MsgActivityInboxBinding
    private lateinit var session: SessionRepository
    private lateinit var adapter: ConversationAdapter
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MsgActivityInboxBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = ExoTradeApplication.container.sessionRepository
        
        binding.rvConversations.layoutManager = LinearLayoutManager(this)
        adapter = ConversationAdapter(object : ConversationAdapter.OnConversationClickListener {
            override fun onConversationClick(conversation: Conversation) {
                val intent = Intent(this@InboxActivity, ChatActivity::class.java).apply {
                    putExtra("conversation_id", conversation.id)
                    putExtra("other_username", conversation.otherUsername)
                    putExtra("other_profile_pic", conversation.otherProfilePic)
                    putExtra("other_public_key", conversation.otherPublicKey)
                }
                startActivity(intent)
            }
        })
        binding.rvConversations.adapter = adapter

        NavigationHelper.setup(this, binding.bottomNavigation, R.id.nav_messages)
    }

    override fun onResume() {
        super.onResume()
        fetchConversations()
        startPolling()
        Helpers.updateUnreadBadge(binding.bottomNavigation)
    }

    override fun onPause() {
        super.onPause()
        stopPolling()
    }

    private fun startPolling() {
        pollJob = lifecycleScope.launch {
            while (isActive) {
                delay(3000)
                fetchConversations()
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
    }

    private fun fetchConversations() {
        binding.progressBar.visibility = View.VISIBLE
        val params = session.authParams()

        lifecycleScope.launch {
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
                    Toast.makeText(this@InboxActivity, json["message"]?.jsonPrimitive?.content ?: "Failed to load messages", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@InboxActivity, "Error loading conversations", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
