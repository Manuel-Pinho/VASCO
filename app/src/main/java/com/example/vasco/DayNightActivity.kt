package com.example.vasco

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class DayNightActivity : Application() {
    override fun onCreate() {
        super.onCreate()
        // Lê a preferência de night mode antes de qualquer Activity
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val nightMode = prefs.getBoolean("night_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (nightMode)
                AppCompatDelegate.MODE_NIGHT_YES
            else
                AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
