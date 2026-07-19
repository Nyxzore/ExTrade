package com.example.exotrade.activities.profile

import android.content.Intent
import android.os.Bundle
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.activities.BaseActivity
import com.example.exotrade.databinding.ActivitySettingsBinding

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupListeners() {
        val themeManager = ExoTradeApplication.themeManager
        val session = ExoTradeApplication.container.sessionRepository

        binding.switchDarkMode.isChecked = themeManager.isDarkMode()
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            themeManager.setDarkMode(isChecked)
        }

        binding.btnEditAccount.setOnClickListener {
            startActivity(Intent(this, EditAccount::class.java))
        }

        binding.btnLogout.setOnClickListener {
            session.clearSession(isExpired = false)
        }
    }
}
