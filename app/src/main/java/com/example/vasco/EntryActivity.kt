// EntryActivity.kt
package com.example.vasco

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class EntryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()
        val prefs = getSharedPreferences("vasco_prefs", MODE_PRIVATE)
        val hasAccount = prefs.getBoolean("has_account", false)
        val chosenMode = prefs.getString("chosen_mode", null)

        when {
            // Já autenticado e já escolheu o modo -> abre sempre o Main correspondente
            auth.currentUser != null && chosenMode != null -> {
                val target = if (chosenMode == "normal")
                    MainNormalActivity::class.java
                else
                    MainSimpleActivity::class.java
                startActivity(Intent(this, target))
            }
            // Já autenticado mas ainda sem escolha -> mostra ChooseActivity
            auth.currentUser != null -> {
                startActivity(Intent(this, ChooseActivity::class.java))
            }
            // Não autenticado mas já registou conta -> login
            hasAccount -> {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            // Nunca registou -> register
            else -> {
                startActivity(Intent(this, RegisterActivity::class.java))
            }
        }

        finish()
    }
}
