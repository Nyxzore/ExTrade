package com.example.exotrade.activities.listings

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.exotrade.Adapters.ListingAdapter
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.activities.breeding.BreedingFeed
import com.example.exotrade.activities.profile.Profile
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.databinding.ListingActivityBrowseBinding
import com.example.exotrade.models.Listing
import com.example.exotrade.utils.*
import com.example.exotrade.viewmodels.BrowseListingsViewModel
import com.example.exotrade.viewmodels.ViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main marketplace feed activity.
 * Displays a searchable, filterable, and paginated list of animal listings.
 */
class BrowseListings : AppCompatActivity() {
    private lateinit var binding: ListingActivityBrowseBinding
    private val session: SessionRepository = ExoTradeApplication.container.sessionRepository
    private lateinit var adapter: ListingAdapter
    private lateinit var layoutManager: LinearLayoutManager
    
    private val viewModel: BrowseListingsViewModel by viewModels { ViewModelFactory() }
    
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ListingActivityBrowseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            ExoTradeApplication.container.speciesRepository.preloadCache()
        }

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(400)
                    viewModel.setSearchQuery(query)
                }
            }
        })

        binding.toggleFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && checkedId == R.id.btnFilterBreeding) {
                startActivity(Intent(this, BreedingFeed::class.java))
                finish()
            }
        }

        NavigationHelper.setup(this, binding.bottomNavigation, R.id.nav_home)

        layoutManager = LinearLayoutManager(this)
        binding.rvListings.layoutManager = layoutManager

        adapter = ListingAdapter(ArrayList(), object : ListingAdapter.OnListingListener {
            override fun onListingClick(listing: Listing) {
                val intent = Intent(this@BrowseListings, ListingDetails::class.java)
                intent.putExtra("listing_id", listing.id)
                startActivity(intent)
            }

            override fun onListingLongClick(listing: Listing, view: View, x: Float, y: Float) {
                showListingMenu(listing, view)
            }
        })
        binding.rvListings.adapter = adapter

        binding.rvListings.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 2) {
                    viewModel.loadNextPage()
                }
            }
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.listings.collect { listings ->
                        adapter.setListings(listings)
                        binding.emptyState.visibility = if (listings.isEmpty() && !viewModel.isLoading.value) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                }
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        // Show/hide loading indicator if needed
                    }
                }
                launch {
                    viewModel.isRefreshing.collect { isRefreshing ->
                        binding.swipeRefresh.isRefreshing = isRefreshing
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Helpers.updateUnreadBadge(binding.bottomNavigation)
        Helpers.checkAdminNotifications(this)
    }

    private fun showListingMenu(listing: Listing, view: View) {
        val popup = PopupMenu(this, view)
        val isOwner = session.getUserUUID() != null && session.getUserUUID() == listing.sellerId

        if (isOwner) {
            popup.menu.add("Edit")
            popup.menu.add("Delete")
        } else {
            if (!SocialLinkUtils.isBlank(listing.whatsapp)) {
                popup.menu.add("WhatsApp Seller")
            }
            popup.menu.add("View Seller Profile")
        }
        popup.menu.add("Share")
        if (!isOwner) {
            popup.menu.add("Report")
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Delete" -> listing.id?.let { deleteListing(it) }
                "Edit" -> {
                    val intent = Intent(this, EditListing::class.java)
                    intent.putExtra("listing_id", listing.id)
                    startActivity(intent)
                }
                "WhatsApp Seller" -> SocialLinkUtils.openWhatsApp(this, listing.whatsapp)
                "View Seller Profile" -> {
                    val intent = Intent(this, Profile::class.java)
                    intent.putExtra("user_id", listing.sellerId)
                    startActivity(intent)
                }
                "Share" -> ShareUtils.shareListingAsImage(
                    this, listing.id ?: "", listing.commonName, listing.scientificName,
                    listing.price, listing.description, listing.imageUrl,
                    "sold" == listing.status, "sale", listing.whatsapp,
                    listing.facebook, listing.instagram
                )
                "Report" -> ReportDialog.show(this, "listing", listing.id ?: "", null)
            }
            true
        }
        popup.show()
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
                        if (response.contains("\"status\":\"success\"")) {
                            Toast.makeText(this@BrowseListings, "Deleted", Toast.LENGTH_SHORT).show()
                            viewModel.refresh()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@BrowseListings, "Error deleting listing", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("No", null).show()
    }
}
