package com.example.exotrade.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.activities.admin.AdminActivity
import com.example.exotrade.activities.listings.BrowseListingsFragment
import com.example.exotrade.activities.listings.CreateListingFragment
import com.example.exotrade.activities.messaging.InboxFragment
import com.example.exotrade.activities.profile.ProfileFragment
import com.example.exotrade.databinding.ActivityMainHostBinding
import com.example.exotrade.utils.Helpers

class MainHostActivity : BaseActivity() {

    private lateinit var binding: ActivityMainHostBinding
    
    private val browseListingsFragment = BrowseListingsFragment()
    private val createListingFragment = CreateListingFragment()
    private val inboxFragment = InboxFragment()
    private val profileFragment = ProfileFragment()

    private var currentFragment: Fragment = browseListingsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFragments()
        setupBottomNavigation()
        setupOnBackPressed()
        
        handleIntent(intent)
    }

    private fun setupFragments() {
        val fm = supportFragmentManager
        fm.beginTransaction()
            .add(R.id.fragmentContainer, profileFragment, "tab_profile").hide(profileFragment)
            .add(R.id.fragmentContainer, createListingFragment, "tab_add").hide(createListingFragment)
            .add(R.id.fragmentContainer, inboxFragment, "tab_messages").hide(inboxFragment)
            .add(R.id.fragmentContainer, browseListingsFragment, "tab_home")
            .commit()
    }

    private fun setupBottomNavigation() {
        val session = ExoTradeApplication.container.sessionRepository
        binding.bottomNavigation.menu.findItem(R.id.nav_admin)?.isVisible = session.isAdmin()
        Helpers.prepareBottomNav(binding.bottomNavigation)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    switchToFragment(browseListingsFragment)
                    true
                }
                R.id.nav_messages -> {
                    switchToFragment(inboxFragment)
                    true
                }
                R.id.nav_add -> {
                    switchToFragment(createListingFragment)
                    true
                }
                R.id.nav_profile -> {
                    switchToFragment(profileFragment)
                    true
                }
                R.id.nav_admin -> {
                    startActivity(Intent(this, AdminActivity::class.java))
                    false // Don't select it as a tab
                }
                else -> false
            }
        }
    }

    private fun switchToFragment(target: Fragment) {
        if (currentFragment == target) return
        
        supportFragmentManager.beginTransaction()
            .hide(currentFragment)
            .show(target)
            .commit()
        currentFragment = target
    }

    fun switchTab(itemId: Int) {
        binding.bottomNavigation.selectedItemId = itemId
    }

    private fun setupOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentFragment != browseListingsFragment) {
                    switchTab(R.id.nav_home)
                } else {
                    moveTaskToBack(true)
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val initialTab = intent.getIntExtra("initial_tab", -1)
        if (initialTab != -1) {
            switchTab(initialTab)
        }
    }
    
    override fun onResume() {
        super.onResume()
        Helpers.updateUnreadBadge(binding.bottomNavigation)
        Helpers.updateNavProfileIcon(binding.bottomNavigation)
    }
}
