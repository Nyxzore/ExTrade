package com.example.exotrade.activities.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.exotrade.Adapters.ListingAdapter
import com.example.exotrade.models.Listing
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.activities.auth.Login
import com.example.exotrade.activities.listings.EditListing
import com.example.exotrade.activities.listings.ListingDetails
import com.example.exotrade.databinding.ProfileActivityMainBinding
import com.example.exotrade.utils.Helpers
import com.example.exotrade.utils.NavigationHelper
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.utils.SocialLinkUtils
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int

/**
 * Activity for displaying a user's profile, including their bio, social links, and listings.
 * Can be used to view the current user's own profile or another user's profile.
 */
class Profile : AppCompatActivity() {
    private lateinit var binding: ProfileActivityMainBinding
    private lateinit var session: SessionRepository
    private var viewUserId: String? = null
    private val allFetchedListings = mutableListOf<Listing>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ProfileActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = ExoTradeApplication.container.sessionRepository
        viewUserId = intent.getStringExtra("user_id") ?: session.getUserUUID()

        setupUI()
        setupListeners()
        setupNavigation()
    }

    private fun setupUI() {
        binding.rvMyListings.layoutManager = LinearLayoutManager(this)

        val isSelf = viewUserId == session.getUserUUID()
        binding.btnEditProfile.visibility = if (isSelf) View.VISIBLE else View.GONE
        binding.btnLogout.visibility = if (isSelf) View.VISIBLE else View.GONE
        binding.btnAddFriend.visibility = if (isSelf) View.GONE else View.VISIBLE
        binding.btnFriends.visibility = if (isSelf) View.VISIBLE else View.GONE
        binding.btnReportUser.visibility = if (isSelf) View.GONE else View.VISIBLE

        if (!isSelf) {
            binding.lblMyListings.text = "Listings"
        }
    }

    private fun setupListeners() {
        binding.switchShowSold.setOnCheckedChangeListener { _, _ ->
            filterAndDisplayListings()
        }

        binding.btnAddFriend.setOnClickListener { addFriend() }
        binding.btnFriends.setOnClickListener {
            startActivity(Intent(this, FriendsActivity::class.java))
        }
        binding.btnReportUser.setOnClickListener {
            com.example.exotrade.utils.ReportDialog.show(this, "user", viewUserId ?: "", null)
        }

        binding.btnLogout.setOnClickListener {
            session.clearSession()
            val intent = Intent(this, Login::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        binding.btnEditProfile.setOnClickListener {
            startActivity(Intent(this, EditAccount::class.java))
        }
    }

    private fun setupNavigation() {
        val isSelf = viewUserId == session.getUserUUID()
        if (isSelf) {
            NavigationHelper.setup(this, binding.bottomNavigation, R.id.nav_profile)
        } else {
            binding.bottomNavigation.visibility = View.GONE
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onResume() {
        super.onResume()
        fetchProfileData()
        if (binding.bottomNavigation.visibility == View.VISIBLE) {
            Helpers.updateUnreadBadge(binding.bottomNavigation)
        }
    }

    private fun fetchProfileData() {
        val targetId = viewUserId ?: return
        val params = session.authParams().toMutableMap()
        params["target_user_id"] = targetId

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("profile/get_profile", params)
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    val username = json["username"]?.jsonPrimitive?.content ?: ""
                    binding.lblUsername.text = username

                    val picPath = json["profile_picture"]?.jsonPrimitive?.content
                    Helpers.loadImage(picPath, binding.imgProfilePicture, R.drawable.ic_person_24)

                    val tier = json["subscription_tier"]?.jsonPrimitive?.int ?: 0

                    if (viewUserId == session.getUserUUID()) {
                        session.updateUserInfo(
                            username = username,
                            profilePic = picPath ?: "",
                            tier = tier,
                            isAdmin = json["is_admin"]?.jsonPrimitive?.boolean ?: false
                        )
                        Helpers.updateNavProfileIcon(binding.bottomNavigation)
                    }

                    val whatsapp = json["whatsapp"]?.jsonPrimitive?.content
                    val facebook = json["facebook"]?.jsonPrimitive?.content
                    val instagram = json["instagram"]?.jsonPrimitive?.content

                    SocialLinkUtils.bindProfileIcons(
                        this@Profile,
                        binding.layoutSocialLinks,
                        binding.imgSocialWhatsApp,
                        binding.imgSocialFacebook,
                        binding.imgSocialInstagram,
                        whatsapp,
                        facebook,
                        instagram
                    )

                    val imgProfile = binding.imgProfilePicture
                    if (imgProfile is ShapeableImageView) {
                        if (tier >= 1) {
                            imgProfile.strokeWidth = Helpers.dpToPx(this@Profile, 1).toFloat()
                            imgProfile.strokeColor = ContextCompat.getColorStateList(this@Profile, R.color.tier_1_orange)
                        } else {
                            imgProfile.strokeWidth = 0f
                        }
                    }

                    val listingsArray = json["listings"]?.jsonArray
                    allFetchedListings.clear()
                    listingsArray?.forEach { element ->
                        val l = element.jsonObject
                        allFetchedListings.add(
                            Listing(
                                id = l["id"]?.jsonPrimitive?.content ?: "",
                                commonName = l["common_name"]?.jsonPrimitive?.content,
                                scientificName = l["scientific_name"]?.jsonPrimitive?.content,
                                price = l["price"]?.jsonPrimitive?.content,
                                description = l["description"]?.jsonPrimitive?.content,
                                imageUrl = l["image_url"]?.jsonPrimitive?.content,
                                sellerId = viewUserId,
                                listingType = l["kind"]?.jsonPrimitive?.content,
                                sex = l["sex"]?.jsonPrimitive?.content,
                                status = l["status"]?.jsonPrimitive?.content,
                                subscriptionTier = l["subscription_tier"]?.jsonPrimitive?.int ?: 0
                            )
                        )
                    }
                    filterAndDisplayListings()
                } else {
                    Toast.makeText(this@Profile, "Error: ${json["message"]?.jsonPrimitive?.content}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@Profile, "Failed to load profile data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun filterAndDisplayListings() {
        val showSold = binding.switchShowSold.isChecked
        val filtered = allFetchedListings.filter { showSold || "sold" != it.status }

        val adapter = ListingAdapter(filtered.toMutableList(), object : ListingAdapter.OnListingListener {
            override fun onListingClick(listing: Listing) {
                val intent = if ("breeding" == listing.listingType) {
                    Intent(this@Profile, com.example.exotrade.activities.breeding.BreedingListingDetails::class.java)
                } else {
                    Intent(this@Profile, ListingDetails::class.java)
                }
                intent.putExtra("listing_id", listing.id)
                startActivity(intent)
            }

            override fun onListingLongClick(listing: Listing, view: View, x: Float, y: Float) {
                if (viewUserId == session.getUserUUID()) {
                    showListingMenu(listing, view)
                }
            }
        })
        binding.rvMyListings.adapter = adapter
    }

    override fun onSupportNavigateUp(): Boolean {
        if (viewUserId != session.getUserUUID()) {
            finish()
            return true
        }
        return super.onSupportNavigateUp()
    }

    private fun showListingMenu(listing: Listing, view: View) {
        val popup = PopupMenu(this, view)
        val isOwner = viewUserId == session.getUserUUID()

        if (isOwner) {
            popup.menu.add("Edit")
            popup.menu.add("Delete")
        } else {
            if (!SocialLinkUtils.isBlank(listing.whatsapp)) {
                popup.menu.add("WhatsApp Seller")
            }
        }
        popup.menu.add("Share")

        popup.setOnMenuItemClickListener { item ->
            when (item.title?.toString()) {
                "Delete" -> listing.id?.let { deleteListing(it) }
                "Edit" -> {
                    val intent = Intent(this, EditListing::class.java)
                    intent.putExtra("listing_id", listing.id)
                    startActivity(intent)
                }
                "WhatsApp Seller" -> SocialLinkUtils.openWhatsApp(this, listing.whatsapp)
                "Share" -> com.example.exotrade.utils.ShareUtils.shareListingAsImage(
                    this,
                    listing.id ?: "",
                    listing.commonName,
                    listing.scientificName,
                    listing.price,
                    listing.description,
                    listing.imageUrl,
                    "sold" == listing.status,
                    listing.listingType,
                    listing.whatsapp,
                    listing.facebook,
                    listing.instagram
                )
            }
            true
        }
        popup.show()
    }

    private fun addFriend() {
        val params = session.authParams().toMutableMap()
        params["target_user_id"] = viewUserId ?: ""

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("friends/send_friend_request", params)
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    Toast.makeText(this@Profile, "Friend request sent!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@Profile, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@Profile, "Failed to send friend request", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteListing(listingId: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Listing")
            .setMessage("Are you sure you want to delete this listing?")
            .setPositiveButton("Yes") { _, _ ->
                val params = session.authParams().toMutableMap()
                params["listing_id"] = listingId

                lifecycleScope.launch {
                    try {
                        val response: String = ExoTradeApplication.container.apiService.postForm("listings/delete_listing", params)
                        val json = Json.parseToJsonElement(response).jsonObject
                        if ("success" == json["status"]?.jsonPrimitive?.content) {
                            Toast.makeText(this@Profile, "Deleted", Toast.LENGTH_SHORT).show()
                            fetchProfileData()
                        } else {
                            Toast.makeText(this@Profile, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@Profile, "Error deleting", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }
}
