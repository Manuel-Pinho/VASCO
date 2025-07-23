@file:Suppress("ClickableViewAccessibility")
package com.example.vasco

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod

class RegisterActivity : AppCompatActivity() {

    companion object {
        private const val RC_SIGN_IN = 9001
        private const val PREFS = "vasco_prefs"
        private const val KEY_THEME = "theme_mode"
    }

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var tvGoToLogin: TextView
    private lateinit var btnGoogleSignIn: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        // Aplica tema da app antes de inflar layout
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedMode = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_NO)
        AppCompatDelegate.setDefaultNightMode(savedMode)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // 1) Views
        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnRegister = findViewById(R.id.btnRegister)
        tvGoToLogin = findViewById(R.id.tvGoToLogin)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)

        // 2) Firebase Auth & Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 3) Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        btnGoogleSignIn.setOnClickListener {
            // Force account chooser for registration
            googleSignInClient.signOut()
            startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
        }

        tvGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // 4) Password toggle
        val eyeOn = ContextCompat.getDrawable(this, R.drawable.ic_eye)
        val eyeOff = ContextCompat.getDrawable(this, R.drawable.ic_eye_off)
        @SuppressLint("ClickableViewAccessibility")
        fun setupPasswordToggle(editText: EditText) {
            editText.setCompoundDrawablesWithIntrinsicBounds(null, null, eyeOff, null)
            editText.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val dr = editText.compoundDrawables[2] ?: return@setOnTouchListener false
                    if (event.rawX >= editText.right - dr.bounds.width()) {
                        editText.performClick()
                        if (editText.transformationMethod is PasswordTransformationMethod) {
                            editText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                            editText.setCompoundDrawablesWithIntrinsicBounds(null, null, eyeOn, null)
                        } else {
                            editText.transformationMethod = PasswordTransformationMethod.getInstance()
                            editText.setCompoundDrawablesWithIntrinsicBounds(null, null, eyeOff, null)
                        }
                        editText.setSelection(editText.text?.length ?: 0)
                        return@setOnTouchListener true
                    }
                }
                false
            }
        }
        setupPasswordToggle(etPassword)
        setupPasswordToggle(etConfirmPassword)

        // 5) Email/password registration
        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString()
            val confirm = etConfirmPassword.text.toString()

            if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_fill_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pass != confirm) {
                Toast.makeText(this, getString(R.string.error_passwords_mismatch), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pass.length < 6) {
                Toast.makeText(this, getString(R.string.error_password_min_length), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnRegister.isEnabled = false
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    btnRegister.isEnabled = true
                    if (task.isSuccessful) {
                        val uid = auth.currentUser!!.uid
                        val userMap = hashMapOf(
                            "name" to name,
                            "email" to email,
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        db.collection("users").document(uid)
                            .set(userMap, SetOptions.merge())
                            .addOnSuccessListener {
                                prefs.edit().putBoolean("has_account", true).apply()
                                Toast.makeText(this, getString(R.string.register_success), Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, getString(R.string.error_firestore, e.message), Toast.LENGTH_LONG).show()
                            }
                    } else {
                        val err = task.exception?.message ?: "Erro inesperado"
                        Toast.makeText(this,getString(R.string.register_failed, err), Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                Toast.makeText(this, getString(R.string.google_signin_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        val idToken = account.idToken ?: return
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val isNew = task.result?.additionalUserInfo?.isNewUser ?: false
                    if (isNew) {
                        val uid = auth.currentUser!!.uid
                        val userMap = hashMapOf(
                            "name" to (account.displayName ?: ""),
                            "email" to (account.email ?: ""),
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        db.collection("users").document(uid)
                            .set(userMap, SetOptions.merge())
                            .addOnSuccessListener {
                                Toast.makeText(this, getString(R.string.register_google_success), Toast.LENGTH_SHORT).show()
                                auth.signOut()
                                googleSignInClient.signOut()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, getString(R.string.error_register_google, e.message), Toast.LENGTH_LONG).show()
                                auth.signOut()
                                googleSignInClient.signOut()
                            }
                    } else {
                        Toast.makeText(this, getString(R.string.account_exists_use_login), Toast.LENGTH_LONG).show()
                        auth.signOut()
                        googleSignInClient.signOut()
                    }
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.error_google_auth_failed, task.exception?.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}
