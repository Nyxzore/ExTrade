package com.example.exotrade.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class ThemeManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    companion object {
        const val THEME_KEY = "is_dark_mode"
    }

    fun setDarkMode(isDark: Boolean) {
        prefs.edit().putBoolean(THEME_KEY, isDark).apply()
        applyTheme()
    }

    fun isDarkMode(): Boolean {
        return prefs.getBoolean(THEME_KEY, false)
    }

    fun applyTheme() {
        val isDark = isDarkMode()
        if (isDark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}
