package com.example.exotrade.activities.breeding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.activities.messaging.ChatActivity
import com.example.exotrade.databinding.BreedingActivityDetailsBinding
import com.example.exotrade.utils.Helpers
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.utils.ShareUtils
import com.example.exotrade.utils.SocialLinkUtils
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Detailed view of a breeding-specific listing.
 */
class BreedingListingDetails : AppCompatActivity() {
    private lateinit var binding: BreedingActivityDetailsBinding
    private var listingId: String? = null
    private lateinit var session: SessionRepository
    private var sellerId: String? = null
    private var sellerName: String? = null
    private var sellerPublicKey: String? = null
    private var currentImageUrl: String? = null
    private var sellerWhatsApp: String? = null
    private var sellerFacebook: String? = null
    private var sellerInstagram: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BreedingActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        listingId = intent.getStringExtra("listing_id")

        // Handle Deep Link
        if (listingId == null && intent.data != null) {
            val data: Uri? = intent.data
            listingId = data?.lastPathSegment
        }

        session = ExoTradeApplication.container.sessionRepository

        binding.toolbar.setOnClickListener { finish() }

        fetchDetails()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_listing_details, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                ShareUtils.shareListingAsImage(
                    this,
                    listingId ?: "",
                    binding.lblCommonName.text.toString(),
                    binding.lblScientificName.text.toString(),
                    binding.lblPrice.text.toString(),
                    binding.lblDescription.text.toString(),
                    currentImageUrl,
                    false,
                    "breeding",
                    sellerWhatsApp,
                    sellerFacebook,
                    sellerInstagram
                )
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun fetchDetails() {
        val id = listingId ?: return
        val params = session.authParams().toMutableMap()
        params["id"] = id

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.get("breeding/get_breeding_listing_details.php", params)
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    binding.lblCommonName.text = json["common_name"]?.jsonPrimitive?.content
                    binding.lblScientificName.text = json["scientific_name"]?.jsonPrimitive?.content
                    binding.lblPrice.text = json["price"]?.jsonPrimitive?.content
                    binding.lblSex.text = json["sex"]?.jsonPrimitive?.content
                    
                    val type = json["breeding_type"]?.jsonPrimitive?.content
                    binding.lblBreedingType.text = if (type == "loan") "Willing to Loan" else "Looking for Partner"
                    
                    binding.lblSize.text = "${json["size_in_cm"]?.jsonPrimitive?.content} cm"
                    binding.lblAge.text = json["age"]?.jsonPrimitive?.content
                    binding.lblDescription.text = json["description"]?.jsonPrimitive?.content

                    sellerId = json["seller_id"]?.jsonPrimitive?.content
                    sellerName = json["seller_name"]?.jsonPrimitive?.content
                    sellerPublicKey = json["seller_public_key"]?.jsonPrimitive?.content
                    sellerWhatsApp = json["whatsapp"]?.jsonPrimitive?.content
                    sellerFacebook = json["facebook"]?.jsonPrimitive?.content
                    sellerInstagram = json["instagram"]?.jsonPrimitive?.content

                    SocialLinkUtils.bindProfileIcons(
                        this@BreedingListingDetails,
                        binding.layoutSocialSection,
                        binding.layoutSocialLinks,
                        binding.imgSocialWhatsApp,
                        binding.imgSocialFacebook,
                        binding.imgSocialInstagram,
                        sellerWhatsApp,
                        sellerFacebook,
                        sellerInstagram
                    )

                    currentImageUrl = json["image_url"]?.jsonPrimitive?.content
                    if (!currentImageUrl.isNullOrEmpty()) {
                        Helpers.loadImage(currentImageUrl, binding.imgLargePreview)
                    }

                    setupAction()

                    if (session.isAdmin()) {
                        binding.btnTakeDown.visibility = View.VISIBLE
                        binding.btnTakeDown.setOnClickListener { showTakeDownDialog() }
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun setupAction() {
        if (session.getUserUUID() == sellerId) {
            binding.btnAction.setText(R.string.find_matches)
            binding.btnAction.setOnClickListener {
                val intent = Intent(this, BreedingFeed::class.java)
                intent.putExtra("match_listing_id", listingId)
                startActivity(intent)
            }
        } else {
            binding.btnAction.setText(R.string.contact_seller)
            binding.btnAction.setOnClickListener { startChat() }
        }
    }

    private fun startChat() {
        val params = session.authParams().toMutableMap()
        params["listing_id"] = listingId ?: ""
        params["seller_id"] = sellerId ?: ""
        params["listing_kind"] = "breeding"

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("messaging/start_or_get_conversation.php", params)
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    val details = json["listing_details"]?.jsonObject
                    val otherUser = json["other_user"]?.jsonObject

                    val intent = Intent(this@BreedingListingDetails, ChatActivity::class.java)
                    intent.putExtra("conversation_id", json["conversation_id"]?.jsonPrimitive?.content)
                    
                    otherUser?.let {
                        intent.putExtra("other_username", it["username"]?.jsonPrimitive?.content)
                        intent.putExtra("other_profile_pic", it["profile_pic"]?.jsonPrimitive?.content)
                        intent.putExtra("other_public_key", it["public_key"]?.jsonPrimitive?.content)
                    }
                    
                    details?.let {
                        intent.putExtra("is_from_listing", true)
                        intent.putExtra("listing_name", it["common_name"]?.jsonPrimitive?.content ?: binding.lblCommonName.text.toString())
                        intent.putExtra("listing_id", it["id"]?.jsonPrimitive?.content)
                        intent.putExtra("listing_scientific", it["scientific_name"]?.jsonPrimitive?.content)
                        intent.putExtra("listing_price", it["price"]?.jsonPrimitive?.content)
                        intent.putExtra("listing_image", it["image_url"]?.jsonPrimitive?.content)
                    }

                    startActivity(intent)
                }
            } catch (e: Exception) {}
        }
    }

    private fun showTakeDownDialog() {
        val etReason = EditText(this)
        etReason.hint = "Enter reason for takedown..."
        AlertDialog.Builder(this)
            .setTitle("Take Down Listing")
            .setView(etReason)
            .setPositiveButton("Take Down") { _, _ ->
                val reason = etReason.text.toString().trim().ifEmpty { "Violation of community guidelines" }
                takeDownListing(listingId ?: "", reason)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun takeDownListing(listingId: String, reason: String) {
        val params = session.authParams().toMutableMap()
        params["listing_id"] = listingId
        params["reason"] = reason
        params["kind"] = "breeding"

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("admin/take_down_listing.php", params)
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    Toast.makeText(this@BreedingListingDetails, "Listing taken down", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@BreedingListingDetails, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@BreedingListingDetails, "Takedown failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
