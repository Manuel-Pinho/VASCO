// ChooseActivity.kt
package com.example.vasco

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class ChooseActivity : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose)

        val prefs = getSharedPreferences("vasco_prefs", MODE_PRIVATE)

        val BtnNormal = findViewById<ImageView>(R.id.BtnNormal)
        val BtnSimple = findViewById<ImageView>(R.id.BtnSimple)

        BtnNormal.setOnClickListener {
            // grava a escolha e abre o MainNormalActivity
            prefs.edit()
                .putString("chosen_mode", "normal")
                .apply()

            startActivity(Intent(this, MainNormalActivity::class.java))
            finish()
        }

        BtnSimple.setOnClickListener {
            // grava a escolha e abre o MainSimpleActivity
            prefs.edit()
                .putString("chosen_mode", "simple")
                .apply()

            startActivity(Intent(this, MainSimpleActivity::class.java))
            finish()
        }
    }
}
