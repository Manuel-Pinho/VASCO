package com.example.vasco

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*

class UserActivity : ToolbarActivity() {

    private lateinit var etProfileName: EditText
    private lateinit var etProfileEmail: EditText
    private lateinit var tvDataNascimento: TextView
    private lateinit var spinnerGender: Spinner
    private lateinit var btnConfirmSave: Button
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmNewPassword: EditText
    private lateinit var btnConfirmPassword: Button


    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var userDoc: DocumentReference

    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "PT"))

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_settings)

        setupToolbarClicks()

        // Spinner de gênero
        spinnerGender = findViewById(R.id.spinnerGender)
        ArrayAdapter.createFromResource(
            this,
            R.array.gender_array,
            R.layout.spinner_item
        ).also {
            it.setDropDownViewResource(R.layout.spinner_dropdown_item)
            spinnerGender.adapter = it
        }

        // Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        val user = auth.currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        userDoc = db.collection("users").document(user.uid)

        // Views
        etProfileName = findViewById(R.id.etProfileName)
        etProfileEmail = findViewById(R.id.etProfileEmail)
        tvDataNascimento = findViewById(R.id.tvDataNascimento)
        btnConfirmSave = findViewById(R.id.btnConfirm)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword)
        btnConfirmPassword = findViewById(R.id.btnConfirmPassword)


        // Carrega dados do perfil
        etProfileEmail.setText(user.email)
        etProfileEmail.isEnabled = false
        userDoc.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                doc.getString("name")?.let { etProfileName.setText(it) }
                doc.getString("birthDate")?.takeIf { it.isNotBlank() }?.let { birth ->
                    tvDataNascimento.text = birth
                    calendar.time = try {
                        dateFormat.parse(birth)!!
                    } catch (_: Exception) {
                        Date()
                    }
                }
                doc.getString("gender")?.let { gender ->
                    resources.getStringArray(R.array.gender_array)
                        .indexOf(gender).takeIf { it >= 0 }?.let { spinnerGender.setSelection(it) }
                }
            }
        }

        btnConfirmSave.setOnClickListener { saveProfileFields() }

        // DatePicker
        tvDataNascimento.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.datepicker_dob_title))
                .setSelection(calendar.timeInMillis)
                .build()
            picker.addOnPositiveButtonClickListener { selection ->
                calendar.timeInMillis = selection
                tvDataNascimento.text = dateFormat.format(calendar.time)
            }
            picker.show(supportFragmentManager, "DOB_PICKER")
        }

        // SpinnerGender
        spinnerGender.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                // apenas mantém seleção no Spinner
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Atualizar password com reautenticação
        btnConfirmPassword.setOnClickListener {
            val newPass = etNewPassword.text.toString()
            val confirm = etConfirmNewPassword.text.toString()
            when {
                newPass.isEmpty() || confirm.isEmpty() ->
                    Toast.makeText(this, getString(R.string.error_fill_both_fields), Toast.LENGTH_SHORT).show()

                newPass != confirm ->
                    Toast.makeText(this,  getString(R.string.error_passwords_mismatch), Toast.LENGTH_SHORT).show()

                newPass.length < 6 ->
                    Toast.makeText(this, getString(R.string.error_password_min_length), Toast.LENGTH_SHORT)
                        .show()

                else -> promptForCurrentPassword(newPass)
            }
        }

        // Toggle olho para passwords
        val eyeOn = ContextCompat.getDrawable(this, R.drawable.ic_eye)
        val eyeOff = ContextCompat.getDrawable(this, R.drawable.ic_eye_off)
        fun setupPasswordToggle(editText: EditText) {
            editText.setCompoundDrawablesWithIntrinsicBounds(null, null, eyeOff, null)
            editText.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val dr = editText.compoundDrawables[2] ?: return@setOnTouchListener false
                    if (event.rawX >= editText.right - dr.bounds.width()) {
                        if (editText.transformationMethod is PasswordTransformationMethod) {
                            editText.transformationMethod =
                                HideReturnsTransformationMethod.getInstance()
                            editText.setCompoundDrawablesWithIntrinsicBounds(
                                null,
                                null,
                                eyeOn,
                                null
                            )
                        } else {
                            editText.transformationMethod =
                                PasswordTransformationMethod.getInstance()
                            editText.setCompoundDrawablesWithIntrinsicBounds(
                                null,
                                null,
                                eyeOff,
                                null
                            )
                        }
                        editText.setSelection(editText.text?.length ?: 0)
                        return@setOnTouchListener true
                    }
                }
                false
            }
        }
        setupPasswordToggle(etNewPassword)
        setupPasswordToggle(etConfirmNewPassword)
    }

    private fun promptForCurrentPassword(newPass: String) {
        val input = EditText(this).apply {
            hint = getString(R.string.hint_current_password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_confirm_current_password_title))
            .setView(input)
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                val oldPass = input.text.toString()
                when {
                    oldPass.isBlank() ->
                        Toast.makeText(
                            this,
                            getString(R.string.error_enter_current_password),
                            Toast.LENGTH_SHORT
                        ).show()

                    oldPass == newPass ->
                        Toast.makeText(
                            this,
                            getString(R.string.error_old_and_new_password_same),
                            Toast.LENGTH_LONG
                        ).show()

                    else -> reauthenticateAndChangePassword(oldPass, newPass)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun reauthenticateAndChangePassword(oldPass: String, newPass: String) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return
        val credential = EmailAuthProvider.getCredential(email, oldPass)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPass)
                    .addOnSuccessListener {
                        userDoc.set(mapOf("password" to newPass), SetOptions.merge())
                            .addOnSuccessListener {
                                Toast.makeText(
                                    this,
                                    getString(R.string.password_update_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                                etNewPassword.text.clear()
                                etConfirmNewPassword.text.clear()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(
                                    this,
                                    getString(R.string.error_update_password_failed, e.message),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            getString(R.string.error_update_password_failed, e.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this,  getString(R.string.error_current_password_incorrect, e.message), Toast.LENGTH_LONG)
                    .show()
            }
    }

    private fun saveProfileFields() {
        // Prepara campos a guardar apenas quando não estiverem em branco
        val data = mutableMapOf<String, Any>(
            "name" to etProfileName.text.toString().trim(),
            "gender" to spinnerGender.selectedItem.toString()
        )
        // Inclui e-mail
        val emailText = etProfileEmail.text.toString().trim()
        if (emailText.isNotBlank()) {
            data["email"] = emailText
        }
        // Inclui data de nascimento se preenchida
        val birth = tvDataNascimento.text.toString().trim()
        if (birth.isNotBlank()) {
            data["birthDate"] = birth
        }

        userDoc.set(data, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this,   getString(R.string.profile_saved_success), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this,  getString(R.string.error_save_profile, e.message), Toast.LENGTH_LONG)
                    .show()
            }
    }
}
