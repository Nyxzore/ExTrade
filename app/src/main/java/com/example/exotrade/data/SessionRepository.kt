package com.example.exotrade.data

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SessionRepository(context: Context) {
    private val prefs = context.getSharedPreferences("exotrade_prefs", Context.MODE_PRIVATE)

    private val _logoutEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val logoutEvents = _logoutEvents.asSharedFlow()

    fun isLoggedIn(): Boolean = prefs.getBoolean("is_logged_in", false)
    fun isRememberMe(): Boolean = prefs.getBoolean("remember_me", false)
    fun isAdmin(): Boolean = prefs.getBoolean("is_admin", false)
    fun getSubscriptionTier(): Int = prefs.getInt("subscription_tier", 0)
    fun getUserUUID(): String? = prefs.getString("user_uuid", null)
    fun getUserId(): String? = getUserUUID()
    fun getUsername(): String? = prefs.getString("username", null)
    fun getProfilePic(): String? = prefs.getString("profile_pic", null)
    fun getIdentityPrivateKey(): String? = prefs.getString("identity_private_key", null)
    fun getIdentityPublicKey(): String? = prefs.getString("identity_public_key", null)

    fun authParams(): Map<String, String?> {
        return mapOf(
            "uuid" to getUserUUID(),
            "auth_token" to prefs.getString("auth_token", "")
        )
    }

    fun createLoginSession(uuid: String, authToken: String, username: String, isAdmin: Boolean, tier: Int = 0, rememberMe: Boolean = false) {
        prefs.edit()
            .putString("user_uuid", uuid)
            .putString("auth_token", authToken)
            .putString("username", username)
            .putBoolean("is_admin", isAdmin)
            .putInt("subscription_tier", tier)
            .putBoolean("remember_me", rememberMe)
            .putBoolean("is_logged_in", true)
            .apply()
    }

    fun updateUserInfo(username: String, profilePic: String?, email: String? = null, tier: Int = 0, isAdmin: Boolean = false) {
        prefs.edit()
            .putString("username", username)
            .putString("profile_pic", profilePic)
            .putString("email", email)
            .putInt("subscription_tier", tier)
            .putBoolean("is_admin", isAdmin)
            .apply()
    }

    fun saveIdentityKeys(privateKey: String, publicKey: String? = null) {
        prefs.edit()
            .putString("identity_private_key", privateKey)
            .putString("identity_public_key", publicKey)
            .apply()
    }

    fun clearSession(isExpired: Boolean = false) {
        prefs.edit()
            .remove("user_uuid")
            .remove("auth_token")
            .remove("is_logged_in")
            .remove("identity_private_key")
            .remove("identity_public_key")
            .apply()
        _logoutEvents.tryEmit(isExpired)
    }
}
