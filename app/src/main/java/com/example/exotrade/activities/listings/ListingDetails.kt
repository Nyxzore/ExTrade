package com.example.exotrade.activities.listings

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
import com.example.exotrade.activities.profile.UserProfileBottomSheet
import com.example.exotrade.databinding.ListingActivityDetailsBinding
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.utils.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Detailed view of a specific animal listing.
 * Displays full specimen metadata, seller information, and social contact links.
 */
class ListingDetails : AppCompatActivity() {

    private lateinit var binding: ListingActivityDetailsBinding
    private var sellerId: String? = null
    private var sellerName: String? = null
    private var sellerPublicKey: String? = null
    private var listingName: String? = null
    private var currentListingId: String? = null
    private var currentImageUrl: String? = null
    private var currentStatus: String? = null
    private var sellerWhatsApp: String? = null
    private var sellerFacebook: String? = null
    private var sellerInstagram: String? = null
    private lateinit var session: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ListingActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = ExoTradeApplication.container.sessionRepository

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupListeners()

        var listingId = intent.getStringExtra("listing_id")

        // Handle Deep Link
        if (listingId == null && intent.data != null) {
            val data: Uri? = intent.data
            listingId = data?.lastPathSegment
        }

        this.currentListingId = listingId
        listingId?.let { fetchListingDetails(it) }
    }

    private fun setupListeners() {
        binding.lblScientificName.setOnClickListener {
            sellerId?.let { id ->
                UserProfileBottomSheet.newInstance(id).show(supportFragmentManager, "user_profile")
            }
        }

        binding.lblCommonName.setOnClickListener {
            sellerId?.let { id ->
                UserProfileBottomSheet.newInstance(id).show(supportFragmentManager, "user_profile")
            }
        }

        binding.btnContactSeller.setOnClickListener {
            val id = sellerId ?: return@setOnClickListener
            val lid = currentListingId ?: return@setOnClickListener
            
            val params = session.authParams().toMutableMap()
            params["listing_id"] = lid
            params["seller_id"] = id

            lifecycleScope.launch {
                try {
                    val response: String = ExoTradeApplication.container.apiService.postForm("messaging/start_or_get_conversation.php", params)
                    val json = Json.parseToJsonElement(response).jsonObject
                    if ("success" == json["status"]?.jsonPrimitive?.content) {
                        val details = json["listing_details"]?.jsonObject
                        val otherUser = json["other_user"]?.jsonObject
                        
                        val intent = Intent(this@ListingDetails, ChatActivity::class.java).apply {
                            putExtra("conversation_id", json["conversation_id"]?.jsonPrimitive?.content)
                            
                            otherUser?.let {
                                putExtra("other_username", it["username"]?.jsonPrimitive?.content)
                                putExtra("other_profile_pic", it["profile_pic"]?.jsonPrimitive?.content)
                                putExtra("other_public_key", it["public_key"]?.jsonPrimitive?.content)
                            }
                            
                            details?.let {
                                putExtra("is_from_listing", true)
                                putExtra("listing_name", it["common_name"]?.jsonPrimitive?.content ?: listingName)
                                putExtra("listing_id", it["id"]?.jsonPrimitive?.content)
                                putExtra("listing_scientific", it["scientific_name"]?.jsonPrimitive?.content)
                                putExtra("listing_price", it["price"]?.jsonPrimitive?.content)
                                putExtra("listing_image", it["image_url"]?.jsonPrimitive?.content)
                            }
                        }
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@ListingDetails, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ListingDetails, "Error starting conversation", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_listing_details, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_share) {
            ShareUtils.shareListingAsImage(
                this,
                currentListingId ?: "",
                binding.lblCommonName.text.toString(),
                binding.lblScientificName.text.toString(),
                binding.lblPrice.text.toString(),
                binding.lblDescription.text.toString(),
                currentImageUrl,
                "sold" == currentStatus,
                "sale",
                sellerWhatsApp,
                sellerFacebook,
                sellerInstagram
            )
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun fetchListingDetails(id: String) {
        val params = session.authParams().toMutableMap()
        params["id"] = id

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.get("listings/get_listing_details.php", params)
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    displayDetails(json)
                } else {
                    Toast.makeText(this@ListingDetails, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ListingDetails, "Error loading details", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayDetails(json: kotlinx.serialization.json.JsonObject) {
        var common = json["common_name"]?.jsonPrimitive?.content ?: ""
        if (common.isEmpty()) {
            common = "Unknown"
        }
        listingName = common
        var scientific = json["scientific_name"]?.jsonPrimitive?.content ?: ""
        if (scientific.isEmpty()) {
            scientific = "Unknown"
        }

        binding.collapsingToolbar.title = common
        binding.lblCommonName.text = common
        binding.lblScientificName.text = scientific
        
        val status = json["listing_status"]?.jsonPrimitive?.content ?: "active"
        if ("sold" == status) {
            binding.lblPrice.text = "SOLD"
            binding.lblPrice.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
        } else {
            binding.lblPrice.text = json["price"]?.jsonPrimitive?.content ?: "R 0.00"
        }

        binding.lblSex.text = json["sex"]?.jsonPrimitive?.content ?: "Unsexed"

        val size = json["size_in_cm"]?.jsonPrimitive?.content ?: ""
        binding.lblSize.text = if (size.isEmpty()) "Unknown" else "$size cm"

        val age = json["age"]?.jsonPrimitive?.content ?: ""
        binding.lblAge.text = if (age.isEmpty()) "Unknown" else age

        val distribution = json["distribution"]?.jsonPrimitive?.content ?: ""
        binding.lblDistribution.text = if (distribution.isEmpty()) "Unknown" else distribution

        val description = json["description"]?.jsonPrimitive?.content ?: ""
        binding.lblDescription.text = if (description.isEmpty()) "None" else description

        currentStatus = status
        currentImageUrl = json["image_url"]?.jsonPrimitive?.content

        sellerId = json["seller_id"]?.jsonPrimitive?.content
        sellerName = json["seller_name"]?.jsonPrimitive?.content
        sellerPublicKey = json["seller_public_key"]?.jsonPrimitive?.content
        sellerWhatsApp = json["whatsapp"]?.jsonPrimitive?.content
        sellerFacebook = json["facebook"]?.jsonPrimitive?.content
        sellerInstagram = json["instagram"]?.jsonPrimitive?.content

        SocialLinkUtils.bindProfileIcons(
            this,
            binding.layoutSocialSection,
            binding.layoutSocialLinks,
            binding.imgSocialWhatsApp,
            binding.imgSocialFacebook,
            binding.imgSocialInstagram,
            sellerWhatsApp,
            sellerFacebook,
            sellerInstagram
        )

        val isOwner = sellerId == session.getUserUUID()
        
        if (isOwner) {
            binding.btnContactSeller.visibility = View.GONE
            if ("active" == status) {
                binding.btnMarkSold.visibility = View.VISIBLE
                binding.btnMarkSold.setOnClickListener { markAsSold(json["id"]?.jsonPrimitive?.content ?: "") }
            } else {
                binding.btnMarkSold.visibility = View.GONE
            }
        }

        if (session.isAdmin()) {
            binding.btnTakeDown.visibility = View.VISIBLE
            binding.btnTakeDown.setOnClickListener { showTakeDownDialog() }
        }

        val imagePath = json["image_url"]?.jsonPrimitive?.content
        if (!imagePath.isNullOrEmpty() && "null" != imagePath) {
            Helpers.loadImage(imagePath, binding.imgLargePreview)
        }
    }

    private fun markAsSold(listingId: String) {
        val params = session.authParams().toMutableMap()
        params["listing_id"] = listingId
        params["status"] = "sold"

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("listings/update_listing.php", params)
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    Toast.makeText(this@ListingDetails, "Marked as SOLD", Toast.LENGTH_SHORT).show()
                    fetchListingDetails(listingId)
                } else {
                    Toast.makeText(this@ListingDetails, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ListingDetails, "Update failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTakeDownDialog() {
        val etReason = EditText(this).apply {
            hint = "Enter reason for takedown..."
        }
        AlertDialog.Builder(this)
            .setTitle("Take Down Listing")
            .setView(etReason)
            .setPositiveButton("Take Down") { _, _ ->
                var reason = etReason.text.toString().trim()
                if (reason.isEmpty()) reason = "Violation of community guidelines"
                currentListingId?.let { takeDownListing(it, reason) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun takeDownListing(listingId: String, reason: String) {
        val params = session.authParams().toMutableMap()
        params["listing_id"] = listingId
        params["reason"] = reason
        params["kind"] = "sale"

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("admin/take_down_listing.php", params)
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    Toast.makeText(this@ListingDetails, "Listing taken down", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@ListingDetails, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ListingDetails, "Takedown failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
