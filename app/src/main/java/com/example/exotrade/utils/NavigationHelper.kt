package com.example.exotrade.utils

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.activities.admin.AdminActivity
import com.example.exotrade.activities.listings.BrowseListings
import com.example.exotrade.activities.listings.CreateListing
import com.example.exotrade.activities.messaging.InboxActivity
import com.example.exotrade.activities.profile.Profile
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Utility class to set up {@link BottomNavigationView} and handle tab transitions.
 * Centralizes the logic for switching between main activities and applying snappy transitions.
 */
object NavigationHelper {

    /**
     * Configures the BottomNavigationView with item listeners and visibility logic.
     *
     * @param activity       The calling activity context.
     * @param bottomNav      The navigation view to configure.
     * @param selectedItemId The menu item ID to mark as selected.
     */
    fun setup(activity: AppCompatActivity, bottomNav: BottomNavigationView, selectedItemId: Int) {
        val session = ExoTradeApplication.container.sessionRepository
        bottomNav.menu.findItem(R.id.nav_admin)?.isVisible = session.isAdmin()
        Helpers.prepareBottomNav(bottomNav)

        bottomNav.selectedItemId = selectedItemId
        bottomNav.setOnItemSelectedListener { item ->
            val itemId = item.itemId
            if (itemId == selectedItemId) {
                return@setOnItemSelectedListener true // already on this tab — no-op, do not navigate
            }
            val target: Class<*> = when (itemId) {
                R.id.nav_home -> BrowseListings::class.java
                R.id.nav_messages -> InboxActivity::class.java
                R.id.nav_add -> CreateListing::class.java
                R.id.nav_profile -> Profile::class.java
                R.id.nav_admin -> AdminActivity::class.java
                else -> return@setOnItemSelectedListener false
            }
            
            activity.startActivity(Intent(activity, target))
            activity.overridePendingTransition(0, 0) // Disable transition for snappiness
            activity.finish()
            activity.overridePendingTransition(0, 0) // Disable transition for snappiness

            // Trigger background species sync check when moving between screens
            CoroutineScope(Dispatchers.Main).launch {
                ExoTradeApplication.container.speciesRepository.syncFromServer(false)
            }

            true
        }
    }
}
