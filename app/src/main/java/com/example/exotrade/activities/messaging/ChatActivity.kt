package com.example.exotrade.activities.messaging

import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import com.example.exotrade.activities.BaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.exotrade.Adapters.MessageAdapter
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.models.Message
import com.example.exotrade.R
import com.example.exotrade.databinding.MsgActivityChatBinding
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.utils.Helpers
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int

/**
 * Activity for an individual E2EE chat thread.
 * Handles real-time message polling, encryption, decryption, and optimistic UI updates.
 */
class ChatActivity : BaseActivity(), MessageAdapter.OnUserClickListener {

    private lateinit var binding: MsgActivityChatBinding
    private lateinit var session: SessionRepository
    private var conversationId: String? = null
    private var otherPublicKey: String? = null
    private lateinit var adapter: MessageAdapter
    private var messages = mutableListOf<Message>()
    private var pollJob: Job? = null
    private var lastId = "0"

    // Staged listing info
    private var stagedLId: String? = null
    private var stagedLCommon: String? = null
    private var stagedLScientific: String? = null
    private var stagedLPrice: String? = null
    private var stagedLImage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MsgActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = ExoTradeApplication.container.sessionRepository
        
        val otherUsername = intent.getStringExtra("other_username")
        val otherProfilePic = intent.getStringExtra("other_profile_pic")
        conversationId = intent.getStringExtra("conversation_id")
        otherPublicKey = intent.getStringExtra("other_public_key")

        otherUsername?.let { binding.lblToolbarName.text = it }
        Helpers.loadImage(otherProfilePic, binding.imgToolbarProfile, R.drawable.ic_person_24)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvMessages.layoutManager = layoutManager

        adapter = MessageAdapter(session.getUserUUID() ?: "", this)
        binding.rvMessages.adapter = adapter

        binding.btnSend.setOnClickListener { sendMessage() }

        // Hide bottom nav for chat thread
        findViewById<View>(R.id.bottomNavigation)?.visibility = View.GONE

        if (intent.getBooleanExtra("is_from_listing", false)) {
            stagedLId = intent.getStringExtra("listing_id")
            stagedLCommon = intent.getStringExtra("listing_name")
            stagedLScientific = intent.getStringExtra("listing_scientific")
            stagedLPrice = intent.getStringExtra("listing_price")
            stagedLImage = intent.getStringExtra("listing_image")
            
            showStagingUI()
            binding.etMessage.setText("Interested")
        }

        fetchMessages(false)
    }

    override fun onResume() {
        super.onResume()
        startPolling()
        markAsRead()
    }

    override fun onPause() {
        super.onPause()
        stopPolling()
    }

    private fun startPolling() {
        pollJob = lifecycleScope.launch {
            while (isActive) {
                delay(3000)
                fetchMessages(true)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
    }

    private fun fetchMessages(isPolling: Boolean) {
        val cid = conversationId ?: return

        val params = session.authParams().toMutableMap()
        params["conversation_id"] = cid
        if (isPolling) {
            params["since_id"] = lastId
        }

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.get("messaging/get_messages", params)
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    val arr = json["messages"]?.jsonArray
                    
                    if (arr != null && arr.isNotEmpty()) {
                        val newMessages = mutableListOf<Message>()
                        
                        val encryptionManager = ExoTradeApplication.container.encryptionManager
                        val myPrivKeyBase64 = session.getIdentityPrivateKey()
                        val myPrivKey = if (myPrivKeyBase64 != null) Base64.decode(myPrivKeyBase64, Base64.NO_WRAP) else null

                        arr.forEach { element ->
                            val m = element.jsonObject
                            var body = m["body"]?.jsonPrimitive?.content ?: ""
                            val isEncrypted = m["is_encrypted"]?.jsonPrimitive?.boolean ?: true
                            val nonce = m["nonce"]?.jsonPrimitive?.content ?: ""

                            if (isEncrypted && body.isNotEmpty() && nonce.isNotEmpty() && myPrivKey != null) {
                                val decrypted = encryptionManager.decryptMessage(body, nonce, otherPublicKey ?: "", myPrivKey)
                                body = decrypted ?: "[Decryption Failed]"
                            }

                            val msg = Message(
                                id = m["id"]?.jsonPrimitive?.content,
                                conversationId = m["conversation_id"]?.jsonPrimitive?.content,
                                senderId = m["sender_id"]?.jsonPrimitive?.content,
                                messageText = body,
                                sentAt = m["sent_at"]?.jsonPrimitive?.content,
                                senderProfilePic = m["sender_profile_pic"]?.jsonPrimitive?.content,
                                senderUsername = m["sender_username"]?.jsonPrimitive?.content,
                                senderSubscriptionTier = m["sender_subscription_tier"]?.jsonPrimitive?.int ?: 0
                            )
                            newMessages.add(msg)
                            msg.id?.let { lastId = it }
                        }

                        if (isPolling) {
                            for (nm in newMessages) {
                                if (messages.none { it.id == nm.id }) {
                                    messages.add(nm)
                                }
                            }
                            adapter.submitList(ArrayList(messages))
                            scrollToBottomIfNear()
                        } else {
                            messages = newMessages
                            adapter.submitList(ArrayList(messages))
                            binding.rvMessages.scrollToPosition(messages.size - 1)
                            checkStagedDuplicate()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatActivity", "fetchMessages failed", e)
                // Handle error
            }
        }
    }

    private fun checkStagedDuplicate() {
        val sid = stagedLId ?: return
        if (messages.any { it.isListingRef && sid == it.listingId }) {
            hideStagingUI()
        }
    }

    private fun showStagingUI() {
        val layoutStaging = binding.layoutStaging ?: return
        layoutStaging.visibility = View.VISIBLE
        Helpers.loadImage(stagedLImage, binding.imgStaged)
        
        val title = if (!stagedLCommon.isNullOrBlank() && stagedLCommon != "null") stagedLCommon else stagedLScientific
        binding.lblStagedTitle.text = title
        
        val priceDisplay = if (!stagedLPrice.isNullOrBlank() && stagedLPrice != "null") stagedLPrice else "Contact for price"
        binding.lblStagedPrice.text = priceDisplay
        
        binding.btnRemoveStaged.setOnClickListener { hideStagingUI() }
    }

    private fun hideStagingUI() {
        stagedLId = null
        binding.layoutStaging?.visibility = View.GONE
        
        if (binding.etMessage.text.toString().trim() == "Interested") {
            binding.etMessage.setText("")
        }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty()) return

        val cid = conversationId ?: run {
            Toast.makeText(this, "Cannot send message", Toast.LENGTH_SHORT).show()
            return
        }

        if (stagedLId != null) {
            val alreadySent = messages.any { it.isListingRef && stagedLId == it.listingId }

            if (!alreadySent) {
                sendListingRef(stagedLId!!, stagedLCommon, stagedLScientific, stagedLPrice, stagedLImage)
            } else if (text == "Interested") {
                hideStagingUI()
                return
            }
            hideStagingUI()
        }

        val optimisticMsg = Message(
            "temp_${System.currentTimeMillis()}",
            cid,
            session.getUserUUID(),
            text,
            "Just now",
            session.getProfilePic(),
            session.getUsername(),
            false
        ).apply {
            isSending = true
        }
        
        messages.add(optimisticMsg)
        adapter.submitList(ArrayList(messages))
        binding.rvMessages.smoothScrollToPosition(messages.size - 1)
        binding.etMessage.setText("")

        val params = session.authParams().toMutableMap()
        params["conversation_id"] = cid

        lifecycleScope.launch {
            try {
                val encryptionManager = ExoTradeApplication.container.encryptionManager
                val myPrivKeyBase64 = session.getIdentityPrivateKey()
                if (myPrivKeyBase64 == null) {
                    Toast.makeText(this@ChatActivity, "Security error", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val myPrivKey = Base64.decode(myPrivKeyBase64, Base64.NO_WRAP)
                
                val result = encryptionManager.encryptMessage(text, otherPublicKey ?: "", myPrivKey)
                params["body"] = result.ciphertext
                params["nonce"] = result.nonce

                val response: String = ExoTradeApplication.container.apiService.postForm("messaging/send_message", params)
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    optimisticMsg.isSending = false
                    optimisticMsg.id = json["message_id"]?.jsonPrimitive?.content
                    optimisticMsg.sentAt = json["sent_at"]?.jsonPrimitive?.content
                    optimisticMsg.id?.let { lastId = it }
                    adapter.notifyItemChanged(messages.indexOf(optimisticMsg))
                } else {
                    messages.remove(optimisticMsg)
                    adapter.submitList(ArrayList(messages))
                    Toast.makeText(this@ChatActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                    binding.etMessage.setText(text)
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatActivity", "sendMessage failed", e)
                messages.remove(optimisticMsg)
                adapter.submitList(ArrayList(messages))
            }
        }
    }

    private fun sendListingRef(id: String, common: String?, scientific: String?, price: String?, imageUrl: String?) {
        val jsonRef = Message.createListingRef(id, common, scientific, price, imageUrl)
        if (jsonRef.isEmpty()) return

        val params = session.authParams().toMutableMap()
        params["conversation_id"] = conversationId ?: return

        lifecycleScope.launch {
            try {
                val encryptionManager = ExoTradeApplication.container.encryptionManager
                val myPrivKeyBase64 = session.getIdentityPrivateKey() ?: return@launch
                val myPrivKey = Base64.decode(myPrivKeyBase64, Base64.NO_WRAP)
                
                val result = encryptionManager.encryptMessage(jsonRef, otherPublicKey ?: "", myPrivKey)
                params["body"] = result.ciphertext
                params["nonce"] = result.nonce

                ExoTradeApplication.container.apiService.postForm<String>("messaging/send_message", params)
                fetchMessages(true)
            } catch (ignored: Exception) {}
        }
    }

    private fun markAsRead() {
        val cid = conversationId ?: return
        val params = session.authParams().toMutableMap()
        params["conversation_id"] = cid
        lifecycleScope.launch {
            try {
                ExoTradeApplication.container.apiService.postForm<String>("messaging/mark_read", params)
            } catch (e: Exception) {
                android.util.Log.e("ChatActivity", "markAsRead failed", e)
            }
        }
    }

    private fun scrollToBottomIfNear() {
        val lm = binding.rvMessages.layoutManager as? LinearLayoutManager
        lm?.let {
            val lastVisible = it.findLastVisibleItemPosition()
            if (lastVisible >= messages.size - 3) {
                binding.rvMessages.smoothScrollToPosition(messages.size - 1)
            }
        }
    }

    override fun onUserClick(userId: String) {
        com.example.exotrade.activities.profile.ProfileBottomSheet.newInstance(userId)
            .show(supportFragmentManager, "profile_bottom_sheet")
    }
}
