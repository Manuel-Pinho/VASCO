package com.example.vasco

import android.content.Context
import android.content.Intent
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

// Ative a supressão de advertências se necessário
@Suppress("DEPRECATION")
abstract class ToolbarActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val pos = prefs.getInt("language_position", 0)
        // mapa posição → código ISO
        val code = when (pos) {
            0 -> "pt"   // Português
            1 -> "en"   // Inglês
            2 -> "ru"   // Russo
            3 -> "fr"   // Francês
            4 -> "es"   // Espanhol
            else -> Locale.getDefault().language
        }
        super.attachBaseContext(LocaleHelper.wrap(newBase, code))
    }


    protected fun setupToolbarClicks() {
        // Obtém a preferência de modo principal
        val prefs = getSharedPreferences("vasco_prefs", Context.MODE_PRIVATE)
        val chosenMode = prefs.getString("chosen_mode", "normal")

        // Define qual Activity será aberta ao clicar em Home
        val homeClass = if (chosenMode == "simple") {
            MainSimpleActivity::class.java
        } else {
            MainNormalActivity::class.java
        }

        // Home button listener
        findViewById<ImageButton>(R.id.btnHome)?.setOnClickListener {
            // Evita relançar a mesma Activity
            if (this::class.java != homeClass) {
                startActivity(Intent(this, homeClass))
            }
        }

        // Search button
        findViewById<ImageButton>(R.id.btnSearch)?.setOnClickListener {
            if (this !is SearchActivity) {
                startActivity(Intent(this, SearchActivity::class.java))
            }
        }

        // Chat button
        findViewById<ImageButton>(R.id.btnChat)?.setOnClickListener {
            if (this !is ChatActivity) {
                startActivity(Intent(this, ChatActivity::class.java))
            }
        }

        // Settings button
        findViewById<ImageButton>(R.id.btnSettings)?.setOnClickListener {
            if (this !is SettingsActivity) {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }


        findViewById<ImageButton>(R.id.btnOptions)?.setOnClickListener {
            if (this !is OptionActivity) {
                startActivity(Intent(this, OptionActivity::class.java))
            }
        }
    }
}
