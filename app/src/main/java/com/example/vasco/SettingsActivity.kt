package com.example.vasco

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate


class SettingsActivity : ToolbarActivity() {

    companion object {
        private const val REQUEST_CHANGE_MODE = 1001

        // Dark-mode prefs
        private const val PREFS_NAME_DARK   = "settings"
        private const val KEY_DARK_MODE     = "night_mode"

        // Notifications prefs
        private const val PREFS_NAME_NOTIF  = "vasco_prefs"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
    }

    private lateinit var switchNight: Switch
    private lateinit var switchNotifications: Switch
    private lateinit var spinnerLanguage: Spinner
    private lateinit var btnChange: Button
    private lateinit var tvPerfil: TextView
    private lateinit var btnLogout: Button
    private lateinit var btnDeleteAccount: Button
    private lateinit var auth: com.google.firebase.auth.FirebaseAuth
    private lateinit var db: com.google.firebase.firestore.FirebaseFirestore


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupToolbarClicks()

        val sharedPrefs = getSharedPreferences(PREFS_NAME_DARK, MODE_PRIVATE)

        // 1) Switch Night
        switchNight = findViewById(R.id.switchNight)
        switchNight.isChecked = sharedPrefs.getBoolean(KEY_DARK_MODE, false)
        switchNight.setOnCheckedChangeListener { _, isChecked ->
            val root = window.decorView.rootView as ViewGroup
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).also { c -> root.draw(c) }
            val overlay = ImageView(this).apply {
                setImageBitmap(bitmap)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            root.addView(overlay)

            sharedPrefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
            val mode = if (isChecked)
                AppCompatDelegate.MODE_NIGHT_YES
            else
                AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.setDefaultNightMode(mode)
            delegate.localNightMode = mode

            overlay.animate()
                .alpha(0f)
                .setDuration(250)
                .withEndAction { root.removeView(overlay) }
                .start()
        }

        // 2) Switch Notifications
        switchNotifications = findViewById(R.id.switchNotifications)
        val prefsNotif = getSharedPreferences(PREFS_NAME_NOTIF, Context.MODE_PRIVATE)
        switchNotifications.isChecked = prefsNotif.getBoolean(KEY_NOTIFICATIONS, false)
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefsNotif.edit().putBoolean(KEY_NOTIFICATIONS, isChecked).apply()
            val comp = ComponentName(this, NotificationReceiver::class.java)
            val newState = if (isChecked)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            packageManager.setComponentEnabledSetting(
                comp, newState, PackageManager.DONT_KILL_APP
            )
        }

        // 3) Spinner Language
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        ArrayAdapter.createFromResource(
            this, R.array.language_array, R.layout.spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            spinnerLanguage.adapter = adapter
        }
        val savedLangPos = sharedPrefs.getInt("language_position", 0)
        spinnerLanguage.setSelection(savedLangPos)
        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                if (position != savedLangPos) {
                    sharedPrefs.edit().putInt("language_position", position).apply()
                    recreate()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // 4) Other buttons and Firebase setup
        btnChange = findViewById(R.id.btnChange)
        btnChange.setOnClickListener {
            startActivityForResult(
                Intent(this, ChooseActivity::class.java), REQUEST_CHANGE_MODE
            )
        }
        tvPerfil = findViewById(R.id.tvPerfil)
        tvPerfil.setOnClickListener {
            startActivity(Intent(this, UserActivity::class.java))
            finish()
        }
        auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        db   = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        btnLogout = findViewById(R.id.btnLogout)
        btnLogout.setOnClickListener {
            auth.signOut()
            Toast.makeText(
                this,
                getString(R.string.logout_success),
                Toast.LENGTH_SHORT
            ).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount)
        btnDeleteAccount.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_delete_account_title))
                .setMessage(getString(R.string.dialog_delete_account_message))
                .setPositiveButton(getString(R.string.delete)) { _, _ ->
                    deleteFirebaseAuthAccount()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        delegate.applyDayNight()
    }

    private fun deleteFirebaseAuthAccount() {
        auth.currentUser?.let { user ->
            user.delete().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        getString(R.string.account_deleted_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    auth.signOut()
                    startActivity(Intent(this, RegisterActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(
                        this,
                        getString(
                            R.string.error_delete_account,
                            task.exception?.message ?: ""
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } ?: Toast.makeText(
            this,
            getString(R.string.error_no_user_authenticated),
            Toast.LENGTH_SHORT
        ).show()
    }
}
