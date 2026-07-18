package com.example.exotrade

import android.app.Application

class ExoTradeApplication : Application() {
    companion object {
        lateinit var container: SharedContainer
    }

    override fun onCreate() {
        super.onCreate()
        container = SharedContainer(this)
    }
}
