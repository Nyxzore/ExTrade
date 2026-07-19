package com.example.exotrade.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.activities.auth.Login
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Base activity class that provides common functionality,
 * such as handling global session expiration.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as? ExoTradeApplication)?.let {
            // Theme is already applied in Application.onCreate, 
            // but we ensure consistency if needed.
        }
        super.onCreate(savedInstanceState)
        observeLogoutEvents()
    }

    private fun observeLogoutEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ExoTradeApplication.container.sessionRepository.logoutEvents.collectLatest { isExpired ->
                    handleLogout(isExpired)
                }
            }
        }
    }

    private fun handleLogout(isExpired: Boolean) {
        if (this is Login) return // Don't redirect from login to login
        
        if (isExpired) {
            Toast.makeText(this, "Session expired, please log in again", Toast.LENGTH_SHORT).show()
        }
        val intent = Intent(this, Login::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
