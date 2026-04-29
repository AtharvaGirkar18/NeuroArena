package com.example.neuroarenanavigation

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.neuroarenanavigation.data.local.NeuroArenaRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SignupActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnCreateAccount: Button
    private lateinit var tvBackToLogin: TextView

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        etUsername = findViewById(R.id.etSignupUsername)
        etEmail = findViewById(R.id.etSignupEmail)
        etPassword = findViewById(R.id.etSignupPassword)
        etConfirmPassword = findViewById(R.id.etSignupConfirmPassword)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        tvBackToLogin = findViewById(R.id.tvBackToLogin)

        btnCreateAccount.setOnClickListener { createAccount() }
        tvBackToLogin.setOnClickListener { finish() }
    }

    private fun createAccount() {
        val username = etUsername.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        when {
            username.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank() -> {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show()
                return
            }
            password.length < 6 -> {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return
            }
            password != confirmPassword -> {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return
            }
        }

        setSignupLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val currentUser = auth.currentUser
                val updates = UserProfileChangeRequest.Builder()
                    .setDisplayName(username)
                    .build()

                currentUser?.updateProfile(updates)
                    ?.addOnCompleteListener {
                        sendVerificationAndReturnToLogin(
                            uid = currentUser.uid,
                            email = email,
                            username = username,
                            userEmail = currentUser.email ?: email
                        )
                    }
                    ?: run {
                        sendVerificationAndReturnToLogin(
                            uid = currentUser?.uid.orEmpty(),
                            email = email,
                            username = username,
                            userEmail = currentUser?.email ?: email
                        )
                    }
            }
            .addOnFailureListener { e ->
                setSignupLoading(false)
                Toast.makeText(this, friendlySignupError(e), Toast.LENGTH_LONG).show()
            }
    }

    private fun sendVerificationAndReturnToLogin(
        uid: String,
        email: String,
        username: String,
        userEmail: String
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            setSignupLoading(false)
            Toast.makeText(this, "Account created. Please sign in.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val repository = NeuroArenaRepository(this)
        lifecycleScope.launch(Dispatchers.IO) {
            repository.upsertAuthProfile(
                uid = uid,
                email = email,
                username = username,
                avatarUri = ""
            )
        }

        currentUser.sendEmailVerification()
            .addOnCompleteListener {
                setSignupLoading(false)
                auth.signOut()
                if (it.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Verification email sent to $userEmail. Verify before login.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Account created. Could not send verification email now.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                finish()
            }
    }

    private fun setSignupLoading(loading: Boolean) {
        btnCreateAccount.isEnabled = !loading
        btnCreateAccount.text = if (loading) "Creating account..." else "Create Account"
    }

    private fun friendlySignupError(error: Exception): String {
        return when (error) {
            is FirebaseAuthWeakPasswordException -> "Password is too weak (minimum 6 characters)"
            is FirebaseAuthUserCollisionException -> "An account already exists for this email"
            is FirebaseAuthException -> when (error.errorCode) {
                "ERROR_INVALID_EMAIL" -> "Email format is invalid"
                "ERROR_NETWORK_REQUEST_FAILED" -> "Network error. Check your internet connection"
                else -> error.message ?: "Signup failed"
            }
            else -> error.message ?: "Signup failed"
        }
    }
}
