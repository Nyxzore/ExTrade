package com.example.exotrade.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import com.example.exotrade.R
import java.util.Locale
import java.util.regex.Pattern

/**
 * Utility class for normalizing, validating, and formatting social media links.
 * Supports WhatsApp, Facebook, and Instagram.
 * Handles deep-linking into native apps with fallback to browser.
 */
object SocialLinkUtils {

    /** Regex pattern for validating Instagram handles. */
    private val INSTAGRAM_HANDLE = Pattern.compile("^[A-Za-z0-9._]{1,30}$")
    /** Regex pattern for validating Facebook profile handles. */
    private val FACEBOOK_HANDLE = Pattern.compile("^[A-Za-z0-9.]{1,100}$")

    /** @return true if the string is null, empty, or "null". */
    @JvmStatic
    fun isBlank(value: String?): Boolean {
        return value == null || value.trim().isEmpty() || "null".equals(value.trim(), ignoreCase = true)
    }

    /**
     * Normalizes a phone number for WhatsApp usage.
     */
    @JvmStatic
    fun normalizeWhatsApp(phone: String?): String? {
        if (isBlank(phone)) return ""

        var digits = phone!!.replace("[^0-9]".toRegex(), "")
        if (digits.isEmpty()) return null

        if (digits.startsWith("0") && digits.length == 10) {
            digits = "27" + digits.substring(1)
        }

        if (digits.length < 10 || digits.length > 15) {
            return null
        }

        return digits
    }

    /**
     * Normalizes a Facebook handle or profile ID from an input string or URL.
     */
    @JvmStatic
    fun normalizeFacebook(input: String?): String? {
        if (isBlank(input)) return ""

        val value = input!!.trim()

        if (value.contains("facebook.com") || value.contains("fb.com") || value.contains("fb.me")
            || value.startsWith("http://") || value.startsWith("https://")
        ) {
            try {
                val uri = Uri.parse(if (value.startsWith("http")) value else "https://" + value.replaceFirst("^/+".toRegex(), ""))
                val host = uri.host
                if (host != null && (host.contains("facebook.com") || host.contains("fb.com") || host.contains("fb.me"))) {
                    var path = uri.path
                    if (path != null) {
                        path = path.replace("^/+|/+$".toRegex(), "")
                        if (path.startsWith("profile.php")) {
                            val id = uri.getQueryParameter("id")
                            return if (id != null) "profile.php?id=$id" else null
                        }
                        val parts = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                            if ("people" == parts[0] && parts.size > 1) {
                                return parts[1]
                            }
                            if ("pages" == parts[0] && parts.size > 1) {
                                return parts[1]
                            }
                            return parts[0]
                        }
                    }
                }
            } catch (ignored: Exception) {
                return null
            }
            return null
        }

        val normalizedValue = value.replace("@", "").trim()
        if (!FACEBOOK_HANDLE.matcher(normalizedValue).matches()) {
            return null
        }
        return normalizedValue
    }

    /**
     * Normalizes an Instagram handle from an input string or URL.
     */
    @JvmStatic
    fun normalizeInstagram(input: String?): String? {
        if (isBlank(input)) return ""

        val value = input!!.trim()

        if (value.contains("instagram.com") || value.startsWith("http://") || value.startsWith("https://")) {
            try {
                val uri = Uri.parse(if (value.startsWith("http")) value else "https://" + value.replaceFirst("^/+".toRegex(), ""))
                var path = uri.path
                if (path != null) {
                    path = path.replace("^/+|/+$".toRegex(), "")
                    val parts = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                        val handle = parts[0]
                        if ("p" == handle || "reel" == handle || "stories" == handle) {
                            return null
                        }
                        return if (INSTAGRAM_HANDLE.matcher(handle).matches()) handle else null
                    }
                }
            } catch (ignored: Exception) {
                return null
            }
            return null
        }

        var normalizedValue = value.replace("@", "").trim()
        normalizedValue = normalizedValue.split("[?#/]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
        if (!INSTAGRAM_HANDLE.matcher(normalizedValue).matches()) {
            return null
        }
        return normalizedValue
    }

    /** @return A formatted WhatsApp number for display. */
    @JvmStatic
    fun formatWhatsAppDisplay(normalizedPhone: String?): String {
        if (isBlank(normalizedPhone)) return ""
        if (normalizedPhone!!.startsWith("27") && normalizedPhone.length == 11) {
            val local = normalizedPhone.substring(2)
            return String.format(
                Locale.getDefault(), "+27 %s %s %s",
                local.substring(0, 2),
                local.substring(2, 5),
                local.substring(5)
            )
        }
        return "+$normalizedPhone"
    }

    /** Formats a Facebook handle for display. */
    @JvmStatic
    fun formatFacebookDisplay(handle: String?): String {
        if (isBlank(handle)) return ""
        if (handle!!.startsWith("profile.php")) return "Facebook profile"
        return "@" + handle.replace("@", "")
    }

    /** Formats an Instagram handle for display. */
    @JvmStatic
    fun formatInstagramDisplay(handle: String?): String {
        if (isBlank(handle)) return ""
        return "@" + handle!!.replace("@", "")
    }

    /** Generates a wa.me URL for a given phone number. */
    @JvmStatic
    fun whatsAppUrl(phone: String?): String {
        val normalized = normalizeWhatsApp(phone)
        if (normalized.isNullOrEmpty()) return ""
        return "https://wa.me/$normalized"
    }

    /** Generates a full Facebook profile URL. */
    @JvmStatic
    fun facebookUrl(value: String?): String {
        if (isBlank(value)) return ""
        val handle = normalizeFacebook(value)
        if (handle.isNullOrEmpty()) return ""
        return "https://facebook.com/$handle"
    }

    /** Generates a full Instagram profile URL. */
    @JvmStatic
    fun instagramUrl(value: String?): String {
        if (isBlank(value)) return ""
        val handle = normalizeInstagram(value)
        if (handle.isNullOrEmpty()) return ""
        return "https://instagram.com/$handle"
    }

    /** Opens the WhatsApp application for a given phone number. */
    @JvmStatic
    fun openWhatsApp(activity: Activity, phone: String?) {
        val normalized = normalizeWhatsApp(phone)
        if (normalized.isNullOrEmpty()) return

        val appUri = Uri.parse("https://api.whatsapp.com/send?phone=$normalized")
        if (!tryOpenPackage(activity, appUri, "com.whatsapp")) {
            openUrl(activity, "https://wa.me/$normalized")
        }
    }

    /** Opens the Facebook application or browser for a given handle/ID. */
    @JvmStatic
    fun openFacebook(activity: Activity, value: String?) {
        val url = facebookUrl(value)
        if (url.isEmpty()) return

        val handle = normalizeFacebook(value)
        if (handle != null && !handle.startsWith("profile.php")) {
            val appUri = Uri.parse("fb://facewebmodal/f?href=" + Uri.encode(url))
            if (tryOpenPackage(activity, appUri, "com.facebook.katana")) {
                return
            }
        }
        openUrl(activity, url)
    }

    /** Opens the Instagram application or browser for a given handle. */
    @JvmStatic
    fun openInstagram(activity: Activity, value: String?) {
        val handle = normalizeInstagram(value)
        if (handle.isNullOrEmpty()) return

        val appUri = Uri.parse("http://instagram.com/_u/$handle")
        if (!tryOpenPackage(activity, appUri, "com.instagram.android")) {
            openUrl(activity, "https://instagram.com/$handle")
        }
    }

    private fun tryOpenPackage(activity: Activity, uri: Uri, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage(packageName)
        return try {
            activity.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** Opens a generic URL in the system browser. */
    @JvmStatic
    fun openUrl(activity: Activity, url: String?) {
        if (isBlank(url)) return
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.social_app_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    /** Validates a WhatsApp number and returns an error string resource ID if invalid. */
    @JvmStatic
    @StringRes
    fun validateWhatsApp(phone: String?): Int? {
        if (isBlank(phone)) return null
        val normalized = normalizeWhatsApp(phone)
        return if (normalized == null) R.string.error_invalid_whatsapp else null
    }

    /** Validates a Facebook handle and returns an error string resource ID if invalid. */
    @JvmStatic
    @StringRes
    fun validateFacebook(value: String?): Int? {
        if (isBlank(value)) return null
        val normalized = normalizeFacebook(value)
        return if (normalized == null) R.string.error_invalid_facebook else null
    }

    /** Validates an Instagram handle and returns an error string resource ID if invalid. */
    @JvmStatic
    @StringRes
    fun validateInstagram(value: String?): Int? {
        if (isBlank(value)) return null
        val normalized = normalizeInstagram(value)
        return if (normalized == null) R.string.error_invalid_instagram else null
    }

    /** Binds social media icons to a layout and sets up click listeners. */
    @JvmStatic
    fun bindProfileIcons(
        activity: Activity, layoutSocialLinks: View,
        imgWhatsApp: ImageView, imgFacebook: ImageView, imgInstagram: ImageView,
        whatsapp: String?, facebook: String?, instagram: String?
    ): Boolean {
        return bindProfileIcons(
            activity, null, layoutSocialLinks, imgWhatsApp, imgFacebook, imgInstagram,
            whatsapp, facebook, instagram
        )
    }

    @JvmStatic
    fun bindProfileIcons(
        activity: Activity, layoutSocialSection: View?, layoutSocialLinks: View,
        imgWhatsApp: ImageView, imgFacebook: ImageView, imgInstagram: ImageView,
        whatsapp: String?, facebook: String?, instagram: String?
    ): Boolean {
        var hasSocial = false

        if (!isBlank(whatsapp)) {
            imgWhatsApp.visibility = View.VISIBLE
            imgWhatsApp.setImageResource(R.drawable.social_whatsapp_badge)
            imgWhatsApp.contentDescription = activity.getString(R.string.whatsapp) + ": " +
                    formatWhatsAppDisplay(normalizeWhatsApp(whatsapp))
            imgWhatsApp.setOnClickListener { openWhatsApp(activity, whatsapp) }
            hasSocial = true
        } else {
            imgWhatsApp.visibility = View.GONE
        }

        if (!isBlank(facebook)) {
            imgFacebook.visibility = View.VISIBLE
            imgFacebook.setImageResource(R.drawable.social_facebook_badge)
            imgFacebook.contentDescription = activity.getString(R.string.facebook) + ": " +
                    formatFacebookDisplay(facebook)
            imgFacebook.setOnClickListener { openFacebook(activity, facebook) }
            hasSocial = true
        } else {
            imgFacebook.visibility = View.GONE
        }

        if (!isBlank(instagram)) {
            imgInstagram.visibility = View.VISIBLE
            imgInstagram.setImageResource(R.drawable.instagram_glyph_gradient)
            imgInstagram.contentDescription = activity.getString(R.string.instagram) + ": " +
                    formatInstagramDisplay(instagram)
            imgInstagram.setOnClickListener { openInstagram(activity, instagram) }
            hasSocial = true
        } else {
            imgInstagram.visibility = View.GONE
        }

        layoutSocialLinks.visibility = if (hasSocial) View.VISIBLE else View.GONE
        layoutSocialSection?.visibility = if (hasSocial) View.VISIBLE else View.GONE
        return hasSocial
    }

    /** Binds social media links to a share card layout. */
    @JvmStatic
    fun bindShareCard(
        layoutSocialLinks: LinearLayout,
        rowWhatsApp: View, lblWhatsApp: TextView,
        rowFacebook: View, lblFacebook: TextView,
        rowInstagram: View, lblInstagram: TextView,
        whatsapp: String?, facebook: String?, instagram: String?
    ): Boolean {
        var hasSocial = false

        if (!isBlank(whatsapp)) {
            rowWhatsApp.visibility = View.VISIBLE
            lblWhatsApp.text = whatsAppUrl(whatsapp)
            hasSocial = true
        } else {
            rowWhatsApp.visibility = View.GONE
        }

        if (!isBlank(facebook)) {
            rowFacebook.visibility = View.VISIBLE
            lblFacebook.text = facebookUrl(facebook)
            hasSocial = true
        } else {
            rowFacebook.visibility = View.GONE
        }

        if (!isBlank(instagram)) {
            rowInstagram.visibility = View.VISIBLE
            lblInstagram.text = instagramUrl(instagram)
            hasSocial = true
        } else {
            rowInstagram.visibility = View.GONE
        }

        layoutSocialLinks.visibility = if (hasSocial) View.VISIBLE else View.GONE
        return hasSocial
    }

    /** @return A multi-line string caption suitable for sharing a listing. */
    @JvmStatic
    fun buildShareCaption(
        title: String?, listingUrl: String?,
        whatsapp: String?, facebook: String?, instagram: String?
    ): String {
        val sb = StringBuilder()
        if (!isBlank(title)) {
            sb.append(title)
        }
        if (!isBlank(listingUrl)) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append(listingUrl)
        }

        if (!isBlank(whatsapp)) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append("WhatsApp: ").append(whatsAppUrl(whatsapp))
        }
        if (!isBlank(facebook)) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Facebook: ").append(facebookUrl(facebook))
        }
        if (!isBlank(instagram)) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Instagram: ").append(instagramUrl(instagram))
        }

        return sb.toString()
    }
}
