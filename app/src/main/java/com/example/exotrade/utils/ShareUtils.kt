package com.example.exotrade.utils

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.exotrade.R
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * Utility class for generating and sharing listing cards as images.
 * Uses off-screen view inflation and drawing to a Canvas to create high-quality shareable assets.
 */
object ShareUtils {

    /**
     * Generates a graphical card for a listing and opens the Android share sheet.
     * Runs on a background thread to handle image loading and bitmap processing.
     */
    @JvmStatic
    fun shareListingAsImage(
        activity: Activity, listingId: String,
        commonName: String?, scientificName: String?, priceText: String?,
        description: String?, imageUrl: String?, isSold: Boolean, kind: String?,
        whatsapp: String?, facebook: String?, instagram: String?
    ) {
        thread {
            try {
                // Determine path based on kind
                val path = if ("breeding" == kind) "breeding" else "listing"
                val deepLinkUrl = "https://exotrade.co.za/$path/$listingId"

                // Inflate the share card view
                val view = LayoutInflater.from(activity).inflate(R.layout.listing_item_share, null)

                // Populate views
                val lblCommonName = view.findViewById<TextView>(R.id.lblCommonName)
                val lblScientificName = view.findViewById<TextView>(R.id.lblScientificName)
                val lblPrice = view.findViewById<TextView>(R.id.lblPrice)
                val lblDescription = view.findViewById<TextView>(R.id.lblDescription)
                val lblSoldBadge = view.findViewById<TextView>(R.id.lblSoldBadge)
                val lblShareLink = view.findViewById<TextView>(R.id.lblShareLink)
                val imgCoverImage = view.findViewById<ImageView>(R.id.imgCoverImage)

                val layoutSocialLinks = view.findViewById<View>(R.id.layoutSocialLinks)
                val rowSocialWhatsApp = view.findViewById<View>(R.id.rowSocialWhatsApp)
                val lblSocialWhatsApp = view.findViewById<TextView>(R.id.lblSocialWhatsApp)
                val rowSocialFacebook = view.findViewById<View>(R.id.rowSocialFacebook)
                val lblSocialFacebook = view.findViewById<TextView>(R.id.lblSocialFacebook)
                val rowSocialInstagram = view.findViewById<View>(R.id.rowSocialInstagram)
                val lblSocialInstagram = view.findViewById<TextView>(R.id.lblSocialInstagram)

                SocialLinkUtils.bindShareCard(
                    layoutSocialLinks as LinearLayout,
                    rowSocialWhatsApp, lblSocialWhatsApp,
                    rowSocialFacebook, lblSocialFacebook,
                    rowSocialInstagram, lblSocialInstagram,
                    whatsapp, facebook, instagram
                )

                val isNoCommon = commonName == Helpers.NO_COMMON_NAME_PLACEHOLDER || commonName.isNullOrEmpty()
                lblCommonName.text = if (isNoCommon) scientificName else commonName
                if (isNoCommon) {
                    lblScientificName.visibility = View.GONE
                } else {
                    lblScientificName.visibility = View.VISIBLE
                    lblScientificName.text = scientificName
                }
                
                lblPrice.text = priceText
                lblDescription.text = description
                lblSoldBadge.visibility = if (isSold) View.VISIBLE else View.GONE
                lblShareLink.text = "exotrade.co.za/$path/$listingId"

                // Load image synchronously
                if (!imageUrl.isNullOrEmpty() && "null" != imageUrl) {
                    try {
                        val fullUrl = if (imageUrl.startsWith("http")) imageUrl else Helpers.getBaseUrl() + imageUrl
                        val coverBitmap = Glide.with(activity)
                            .asBitmap()
                            .load(fullUrl)
                            .submit(1080, 1080)
                            .get()
                        
                        // Set the bitmap directly since we're off-screen
                        imgCoverImage.setImageBitmap(coverBitmap)
                    } catch (e: Exception) {
                        Log.e("ExTrade_DEBUG", "Glide failed to load share image: $imageUrl", e)
                    }
                }

                // Measure and layout
                val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                view.measure(widthSpec, heightSpec)
                view.layout(0, 0, view.measuredWidth, view.measuredHeight)

                // Create bitmap and draw
                val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                view.draw(canvas)

                // Save to cache
                val cacheDir = File(activity.cacheDir, "shared_listings")
                if (!cacheDir.exists()) {
                    if (!cacheDir.mkdirs()) {
                        Log.e("ExTrade_DEBUG", "Failed to create cache directory: ${cacheDir.absolutePath}")
                    }
                }
                val file = File(cacheDir, "listing_$listingId.png")
                try {
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                } catch (e: Exception) {
                    Log.e("ExTrade_DEBUG", "Failed to save share image PNG", e)
                    throw e
                }

                val uri = FileProvider.getUriForFile(activity, "com.example.exotrade.fileprovider", file)

                val nameToUse = if (commonName.isNullOrEmpty()) scientificName else commonName
                val shareTitle = if (nameToUse.isNullOrEmpty()) "Animal" else nameToUse
                val shareText = SocialLinkUtils.buildShareCaption(
                    shareTitle, deepLinkUrl, whatsapp, facebook, instagram
                )

                activity.runOnUiThread {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    activity.startActivity(Intent.createChooser(shareIntent, "Share Listing"))
                }

            } catch (e: Exception) {
                Log.e("ExTrade_DEBUG", "Error generating share image", e)
                activity.runOnUiThread {
                    Toast.makeText(activity, "Couldn't create share image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
