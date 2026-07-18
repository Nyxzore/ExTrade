package com.example.exotrade.utils

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.activities.NoInternetActivity
import com.example.exotrade.activities.UpdateRequiredActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Global utility class for image loading and UI-related helper methods.
 * Delegates networking to the shared [ApiService].
 */
object Helpers {
    private var HOSTED_SERVER = "https://exotrade.co.za/"

    private var cachedNavProfileBitmap: Bitmap? = null
    private var cachedNavProfileUrl: String? = null
    private var cachedNavProfileTier = -1
    private var lastBadgeUpdateTime: Long = 0

    fun getBaseUrl(): String = HOSTED_SERVER

    fun setBaseUrl(url: String) {
        HOSTED_SERVER = url
    }

    /**
     * Loads an image into an ImageView using Glide.
     */
    @JvmOverloads
    fun loadImage(path: String?, imageView: ImageView, placeholder: Int = R.drawable.logo) {
        if (path.isNullOrEmpty() || "null".equals(path, ignoreCase = true)) {
            Glide.with(imageView.context)
                .load(placeholder)
                .placeholder(placeholder)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(imageView)
            return
        }

        val fullUrl = if (path.startsWith("http")) path else HOSTED_SERVER + path

        Glide.with(imageView.context)
            .load(fullUrl)
            .placeholder(placeholder)
            .error(placeholder)
            .fallback(placeholder)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(imageView)
    }

    /**
     * Checks for active internet connectivity.
     */
    fun isNetworkAvailable(context: Context?): Boolean {
        if (context == null) return true
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        if (connectivityManager != null) {
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        return false
    }

    /**
     * Updates the unread message count badge on the BottomNavigationView.
     */
    fun updateUnreadBadge(nav: BottomNavigationView) {
        val session = ExoTradeApplication.container.sessionRepository
        if (!session.isLoggedIn()) return

        updateNavProfileIcon(nav)

        val now = System.currentTimeMillis()
        if (now - lastBadgeUpdateTime < 5000) return
        lastBadgeUpdateTime = now

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm(
                    "messaging/get_conversations.php",
                    session.authParams()
                )
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    val total = json["total_unread"]?.jsonPrimitive?.int ?: 0
                    val badge = nav.getOrCreateBadge(R.id.nav_messages)
                    if (total > 0) {
                        badge.isVisible = true
                        badge.number = total
                        badge.backgroundColor = ContextCompat.getColor(nav.context, R.color.orange_primary)
                        badge.setBadgeTextColor(ContextCompat.getColor(nav.context, R.color.white))
                    } else {
                        badge.isVisible = false
                    }
                }
            } catch (e: Exception) {
                Log.e("ExTrade", "Badge update error", e)
            }
        }
    }

    fun prepareBottomNav(nav: BottomNavigationView?) {
        if (nav == null) return
        nav.itemIconTintList = ContextCompat.getColorStateList(nav.context, R.color.nav_item_tint)
        nav.itemTextColor = ContextCompat.getColorStateList(nav.context, R.color.nav_item_tint)
    }

    /**
     * Replaces the default profile navigation icon with the user's avatar.
     */
    fun updateNavProfileIcon(nav: BottomNavigationView?) {
        if (nav == null) return
        val profileItem = nav.menu.findItem(R.id.nav_profile) ?: return

        val session = ExoTradeApplication.container.sessionRepository
        val profilePic = session.getProfilePic()
        val tier = session.getSubscriptionTier()

        if (profilePic.isNullOrEmpty() || "null".equals(profilePic, ignoreCase = true)) {
            return
        }

        val fullUrl = if (profilePic.startsWith("http")) profilePic else HOSTED_SERVER + profilePic

        if (fullUrl == cachedNavProfileUrl && tier == cachedNavProfileTier && cachedNavProfileBitmap != null) {
            profileItem.icon = BitmapDrawable(nav.resources, cachedNavProfileBitmap)
            return
        }

        Glide.with(nav.context)
            .asBitmap()
            .load(fullUrl)
            .placeholder(R.drawable.ic_person_24)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    try {
                        val borderSize = dpToPx(nav.context, 1)
                        val iconSizePx = dpToPx(nav.context, 24)
                        val bitmapSize = iconSizePx + dpToPx(nav.context, 4)

                        val borderedBitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(borderedBitmap)

                        val center = bitmapSize / 2f
                        val radius = iconSizePx / 2f

                        if (tier >= 1) {
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                            paint.color = nav.context.getColor(R.color.tier_1_orange)
                            canvas.drawCircle(center, center, radius, paint)
                            drawCircularBitmap(canvas, resource, center, center, radius - borderSize)
                        } else {
                            drawCircularBitmap(canvas, resource, center, center, radius)
                        }

                        cachedNavProfileBitmap = borderedBitmap
                        cachedNavProfileUrl = fullUrl
                        cachedNavProfileTier = tier

                        nav.menu.findItem(R.id.nav_profile)?.icon = BitmapDrawable(nav.resources, borderedBitmap)
                    } catch (e: Exception) {
                        Log.e("ExTrade", "Profile Icon Render Error", e)
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {}
                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    private fun drawCircularBitmap(canvas: Canvas, bitmap: Bitmap, cx: Float, cy: Float, radius: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        
        val scale = (radius * 2f) / Math.min(bitmap.width, bitmap.height)
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(cx - (bitmap.width * scale) / 2f, cy - (bitmap.height * scale) / 2f)
        shader.setLocalMatrix(matrix)
        
        paint.shader = shader
        canvas.drawCircle(cx, cy, radius, paint)
    }

    fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun checkAdminNotifications(activity: android.app.Activity) {
        val session = ExoTradeApplication.container.sessionRepository
        if (!session.isLoggedIn()) return

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm(
                    "admin/get_notifications.php",
                    session.authParams()
                )
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    val arr = json["notifications"]?.jsonArray
                    arr?.forEach { n ->
                        androidx.appcompat.app.AlertDialog.Builder(activity)
                            .setTitle("System Notification")
                            .setMessage(n.jsonObject["message"]?.jsonPrimitive?.content)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            } catch (e: Exception) {}
        }
    }
}
