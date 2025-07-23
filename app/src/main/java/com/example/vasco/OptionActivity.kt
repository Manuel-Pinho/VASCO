package com.example.vasco

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout

class OptionActivity:ToolbarActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_options)
        setupToolbarClicks()

        val btnFavourites = findViewById<LinearLayout>(R.id.btnFavourites)
        btnFavourites?.setOnClickListener {
            startActivity(Intent(this, FavouritesActivity::class.java))
        }
    }
}