package com.example.exotrade

import android.app.Application
import com.example.exotrade.utils.ThemeManager

class ExoTradeApplication : Application() {
    companion object {
        lateinit var container: SharedContainer
        lateinit var themeManager: ThemeManager
    }

    override fun onCreate() {
        super.onCreate()
        container = SharedContainer(this)
        themeManager = ThemeManager(this)
        themeManager.applyTheme()
    }
}
