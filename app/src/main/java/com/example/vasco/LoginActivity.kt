@file:Suppress("ClickableViewAccessibility")
package com.example.vasco

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Patterns
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
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val RC_LOGIN = 9001
        private const val PREFS = "vasco_prefs"
        private const val KEY_THEME = "theme_mode"
    }

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvGoToRegister: TextView
    private lateinit var btnLogin: Button
    private lateinit var btnGoogle: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        // Aplica o tema salvo antes de inflar layout
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedMode = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_NO)
        AppCompatDelegate.setDefaultNightMode(savedMode)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 1) Views
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvGoToRegister = findViewById(R.id.tvGoToRegister)
        btnLogin = findViewById(R.id.btnLogin)
        btnGoogle = findViewById(R.id.btnGoogle)

        // 2) Firebase Auth
        auth = FirebaseAuth.getInstance()

        // 3) Google Sign-In setup
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        btnGoogle.setOnClickListener {
            // Force chooser
            googleSignInClient.signOut()
            startActivityForResult(googleSignInClient.signInIntent, RC_LOGIN)
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

        // 5) Forgot password
        tvForgotPassword.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, getString(R.string.error_invalid_email), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, getString(R.string.password_recovery_sent), Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, getString(R.string.password_recovery_failed, e.message), Toast.LENGTH_LONG).show()
                }
        }

        // 6) Go to register
        tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        // 7) Email/password login
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString()
            when {
                email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                    Toast.makeText(this,   getString(R.string.error_invalid_email), Toast.LENGTH_SHORT).show()
                pass.isEmpty() ->
                    Toast.makeText(this, getString(R.string.error_empty_password), Toast.LENGTH_SHORT).show()
                else -> {
                    btnLogin.isEnabled = false
                    auth.signInWithEmailAndPassword(email, pass)
                        .addOnCompleteListener { task ->
                            btnLogin.isEnabled = true
                            if (task.isSuccessful) {
                                Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainNormalActivity::class.java))
                                finish()
                            } else {
                                val err = task.exception?.message ?: getString(R.string.error_login)
                                Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                            }
                        }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_LOGIN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)

                auth.signInWithCredential(credential)
                    .addOnCompleteListener { signInTask ->
                        if (signInTask.isSuccessful) {
                            val isNew = signInTask.result
                                ?.additionalUserInfo
                                ?.isNewUser
                                ?: false

                            if (isNew) {
                                // Apaga o utilizador recém-criado
                                auth.currentUser
                                    ?.delete()
                                    ?.addOnCompleteListener { deleteTask ->
                                        auth.signOut()
                                        googleSignInClient.signOut()
                                        Toast.makeText(
                                            this,
                                            getString(R.string.google_account_not_registered),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            } else {
                                Toast.makeText(this, getString(R.string.google_login_success), Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainNormalActivity::class.java))
                                finish()
                            }
                        } else {
                            when (signInTask.exception) {
                                is FirebaseAuthInvalidUserException ->
                                    Toast.makeText(
                                        this,
                                        getString(R.string.google_account_not_registered),
                                        Toast.LENGTH_LONG
                                    ).show()
                                else ->
                                    Toast.makeText(
                                        this,
                                        getString(
                                            R.string.google_auth_failed,
                                            signInTask.exception?.message
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                            }
                        }
                    }
            } catch (e: ApiException) {
                Toast.makeText(this,    getString(R.string.google_login_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }
}

