package com.example.exotrade.Adapters

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.exotrade.models.Listing
import com.example.exotrade.R
import com.example.exotrade.databinding.ListingItemBinding
import com.example.exotrade.utils.Helpers

/**
 * Adapter for displaying a list of animal listings in a RecyclerView.
 */
class ListingAdapter(
    private var listings: MutableList<Listing>,
    private val listener: OnListingListener? = null
) : RecyclerView.Adapter<ListingAdapter.ViewHolder>() {

    /**
     * Interface definition for a callback to be invoked when a listing is interacted with.
     */
    interface OnListingListener {
        fun onListingClick(listing: Listing)
        fun onListingLongClick(listing: Listing, view: View, x: Float, y: Float)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListingItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val listing = listings[position]
        holder.bind(listing, listener)
    }

    override fun getItemCount(): Int = listings.size

    fun clear() {
        val size = listings.size
        listings.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun addListings(newListings: List<Listing>) {
        val filteredList = newListings.filter { nl ->
            listings.none { it.id == nl.id }
        }

        if (filteredList.isEmpty()) return

        val startPos = listings.size
        listings.addAll(filteredList)
        notifyItemRangeInserted(startPos, filteredList.size)
    }

    fun setListings(newListings: List<Listing>) {
        listings.clear()
        listings.addAll(newListings)
        notifyDataSetChanged()
    }



    class ViewHolder(private val binding: ListingItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var lastX = 0f
        private var lastY = 0f

        fun bind(listing: Listing, listener: OnListingListener?) {
            with(binding) {
                lblCommonName.text = listing.commonName
                lblScientificName.text = listing.scientificName
                lblPrice.text = listing.price
                lblDescription.text = listing.description

                if (!listing.imageUrl.isNullOrEmpty()) {
                    Helpers.loadImage(listing.imageUrl, imgCoverImage)
                } else {
                    imgCoverImage.setImageResource(R.drawable.logo)
                }

                lblSoldBadge.visibility = if (listing.status == "sold") View.VISIBLE else View.GONE

                if (listing.isUnverifiedScientific || listing.isUnverifiedCommon) {
                    imgUnverified.visibility = View.VISIBLE
                    imgUnverified.setOnClickListener {
                        val msg = if (listing.isUnverifiedScientific) "Unverified scientific name/species" else "Unverified common name"
                        androidx.appcompat.app.AlertDialog.Builder(itemView.context)
                            .setTitle("Verification Status")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                } else {
                    imgUnverified.visibility = View.GONE
                }

                val cardRoot = root as com.google.android.material.card.MaterialCardView
                if (listing.subscriptionTier >= 1) {
                    cardRoot.strokeWidth = Helpers.dpToPx(itemView.context, 1)
                    cardRoot.strokeColor = itemView.context.getColor(R.color.tier_1_orange)
                } else {
                    cardRoot.strokeWidth = 0
                }

                root.setOnClickListener {
                    listener?.onListingClick(listing)
                }

                root.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        lastX = event.x
                        lastY = event.y
                    }
                    if (event.action == MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                    false
                }

                root.setOnLongClickListener {
                    listener?.let {
                        it.onListingLongClick(listing, root, lastX, lastY)
                        true
                    } ?: false
                }
            }
        }
    }
}
