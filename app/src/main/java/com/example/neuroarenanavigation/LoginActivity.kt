package com.example.neuroarenanavigation

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.example.neuroarenanavigation.data.local.NeuroArenaRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnClearSavedData: Button
    private lateinit var btnGoogle: Button
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvNoAccount: TextView

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                Toast.makeText(this, "Google sign-in failed (missing token)", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            firebaseAuthWithGoogle(idToken)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Google sign-in failed", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(
            if (PrefsManager.isDarkModeEnabled(this)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize views
        etEmail = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnClearSavedData = findViewById(R.id.btnClearSavedData)
        btnGoogle = findViewById(R.id.btnGoogle)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvNoAccount = findViewById(R.id.tvNoAccount)

        configureGoogleSignIn()

        applyThemeToLogin()

        val currentUser = auth.currentUser
        if (currentUser != null) {
            continueWithExistingSession(currentUser)
            return
        }

        // Login button click - Navigate to Dashboard
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            signInWithFirebase(email, password)
        }

        tvNoAccount.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter your email first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, "Password reset link sent to $email", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, e.message ?: "Failed to send reset email", Toast.LENGTH_LONG).show()
                }
        }

        btnClearSavedData.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                NeuroArenaRepository(this@LoginActivity).clearAllLocalData()
                runOnUiThread {
                    PrefsManager.clearAll(this@LoginActivity)
                    auth.signOut()
                    googleSignInClient.signOut()
                    etEmail.text.clear()
                    etPassword.text.clear()
                    Toast.makeText(this@LoginActivity, "Saved user data cleared", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Google button click - Just show toast for demo
        btnGoogle.setOnClickListener {
            // Force account chooser so user can switch Google accounts intentionally.
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }
    }

    private fun configureGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun signInWithFirebase(email: String, password: String) {
        setAuthLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val user = auth.currentUser
                if (user == null) {
                    setAuthLoading(false)
                    Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Email/password users must verify mailbox ownership before app access.
                user.reload().addOnCompleteListener {
                    val refreshedUser = auth.currentUser
                    if (refreshedUser == null) {
                        setAuthLoading(false)
                        Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show()
                        return@addOnCompleteListener
                    }
                    if (!refreshedUser.isEmailVerified) {
                        refreshedUser.sendEmailVerification()
                        auth.signOut()
                        setAuthLoading(false)
                        Toast.makeText(
                            this,
                            "Please verify your email first. A verification link has been sent.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@addOnCompleteListener
                    }

                    completeEmailPasswordLogin(refreshedUser)
                }
            }
            .addOnFailureListener { e ->
                setAuthLoading(false)
                Toast.makeText(this, friendlyAuthError(e), Toast.LENGTH_LONG).show()
            }
    }

    private fun completeEmailPasswordLogin(user: FirebaseUser) {
        val repository = NeuroArenaRepository(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val profile = repository.getProfileByUid(user.uid)
            val resolvedName = profile?.username
                ?: user.displayName
                ?: user.email?.substringBefore("@")
                ?: "Player"

            repository.upsertAuthProfile(
                uid = user.uid,
                email = user.email.orEmpty(),
                username = resolvedName,
                avatarUri = profile?.avatarUri ?: ""
            )
            repository.hydratePrefsForUid(user.uid, user.email.orEmpty(), resolvedName)

            runOnUiThread {
                val hydratedName = PrefsManager.getUsername(this@LoginActivity).ifEmpty { resolvedName }
                openDashboard(user.uid, user.email.orEmpty(), hydratedName)
            }
        }
    }

    private fun continueWithExistingSession(user: FirebaseUser) {
        setAuthLoading(true)
        val repository = NeuroArenaRepository(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val profile = repository.getProfileByUid(user.uid)
            val resolvedName = profile?.username
                ?: user.displayName
                ?: user.email?.substringBefore("@")
                ?: "Player"

            repository.upsertAuthProfile(
                uid = user.uid,
                email = user.email.orEmpty(),
                username = resolvedName,
                avatarUri = profile?.avatarUri ?: ""
            )
            repository.hydratePrefsForUid(user.uid, user.email.orEmpty(), resolvedName)

            runOnUiThread {
                val hydratedName = PrefsManager.getUsername(this@LoginActivity).ifEmpty { resolvedName }
                openDashboard(user.uid, user.email.orEmpty(), hydratedName)
            }
        }
    }

    private fun openDashboard(uid: String, email: String, username: String) {
        setAuthLoading(false)
        val intent = Intent(this, DashboardActivity::class.java)
        intent.putExtra("USER_EMAIL", email)
        intent.putExtra("USER_NAME", username)
        intent.putExtra("USER_ID", uid)
        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
        startActivity(intent)
        finish()
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        setAuthLoading(true)
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(credential)
            .addOnSuccessListener {
                val user = auth.currentUser
                if (user == null) {
                    setAuthLoading(false)
                    Toast.makeText(this, "Google login failed", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val resolvedName = user.displayName
                    ?: user.email?.substringBefore("@")
                    ?: "Player"

                val repository = NeuroArenaRepository(this)
                lifecycleScope.launch(Dispatchers.IO) {
                    val profile = repository.getProfileByUid(user.uid)
                    repository.upsertAuthProfile(
                        uid = user.uid,
                        email = user.email.orEmpty(),
                        username = profile?.username ?: resolvedName,
                        avatarUri = profile?.avatarUri ?: ""
                    )
                    repository.hydratePrefsForUid(user.uid, user.email.orEmpty(), profile?.username ?: resolvedName)

                    runOnUiThread {
                        val hydratedName = PrefsManager.getUsername(this@LoginActivity)
                            .ifEmpty { profile?.username ?: resolvedName }
                        openDashboard(
                            uid = user.uid,
                            email = user.email.orEmpty(),
                            username = hydratedName
                        )
                    }
                }
            }
            .addOnFailureListener { e ->
                setAuthLoading(false)
                Toast.makeText(this, e.message ?: "Google login failed", Toast.LENGTH_LONG).show()
            }
    }

    private fun setAuthLoading(loading: Boolean) {
        btnLogin.isEnabled = !loading
        btnLogin.text = if (loading) "Signing in..." else "Log In"
        btnClearSavedData.isEnabled = !loading
    }

    private fun friendlyAuthError(error: Exception): String {
        return when (error) {
            is FirebaseAuthInvalidUserException -> "No account found with this email"
            is FirebaseAuthInvalidCredentialsException -> "Invalid email or password"
            is FirebaseAuthException -> when (error.errorCode) {
                "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please try again later"
                "ERROR_NETWORK_REQUEST_FAILED" -> "Network error. Check your internet connection"
                else -> error.message ?: "Authentication failed"
            }
            else -> error.message ?: "Authentication failed"
        }
    }

    private fun applyThemeToLogin() {
        val isDark = PrefsManager.isDarkModeEnabled(this)

        val root = findViewById<ConstraintLayout>(R.id.rootLogin)
        val tvWelcome = findViewById<TextView>(R.id.tvWelcome)
        val tvSubtitle = findViewById<TextView>(R.id.tvSubtitle)
        val tvUsernameLabel = findViewById<TextView>(R.id.tvUsername)
        val tvPasswordLabel = findViewById<TextView>(R.id.tvPassword)
        val tvNoAccount = findViewById<TextView>(R.id.tvNoAccount)
        val tvSocialLogin = findViewById<TextView>(R.id.tvSocialLogin)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)

        if (isDark) {
            root.setBackgroundColor(Color.parseColor("#121212"))
            tvWelcome.setTextColor(Color.parseColor("#FFFFFF"))
            tvSubtitle.setTextColor(Color.parseColor("#BEBEBE"))
            tvUsernameLabel.setTextColor(Color.parseColor("#DADADA"))
            tvPasswordLabel.setTextColor(Color.parseColor("#DADADA"))
            tvNoAccount.setTextColor(Color.parseColor("#9E9E9E"))
            tvSocialLogin.setTextColor(Color.parseColor("#9E9E9E"))
            tvForgotPassword.setTextColor(Color.parseColor("#BB86FC"))

            etEmail.setTextColor(Color.parseColor("#FFFFFF"))
            etEmail.setHintTextColor(Color.parseColor("#9E9E9E"))
            etPassword.setTextColor(Color.parseColor("#FFFFFF"))
            etPassword.setHintTextColor(Color.parseColor("#9E9E9E"))

            styleInputField(etEmail, isDark = true)
            styleInputField(etPassword, isDark = true)

            btnLogin.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#7C3AED"))
            btnClearSavedData.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4B5563"))
            btnClearSavedData.setTextColor(Color.parseColor("#F3F4F6"))
            btnGoogle.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DB4437"))
        } else {
            root.setBackgroundColor(Color.parseColor("#FFFFFF"))
            tvWelcome.setTextColor(Color.parseColor("#000000"))
            tvSubtitle.setTextColor(Color.parseColor("#757575"))
            tvUsernameLabel.setTextColor(Color.parseColor("#616161"))
            tvPasswordLabel.setTextColor(Color.parseColor("#616161"))
            tvNoAccount.setTextColor(Color.parseColor("#757575"))
            tvSocialLogin.setTextColor(Color.parseColor("#757575"))
            tvForgotPassword.setTextColor(Color.parseColor("#6200EE"))

            etEmail.setTextColor(Color.parseColor("#111111"))
            etEmail.setHintTextColor(Color.parseColor("#9E9E9E"))
            etPassword.setTextColor(Color.parseColor("#111111"))
            etPassword.setHintTextColor(Color.parseColor("#9E9E9E"))

            styleInputField(etEmail, isDark = false)
            styleInputField(etPassword, isDark = false)

            btnLogin.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6200EE"))
            btnClearSavedData.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BDBDBD"))
            btnClearSavedData.setTextColor(Color.parseColor("#FFFFFF"))
            btnGoogle.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DB4437"))
        }
    }

    private fun styleInputField(editText: EditText, isDark: Boolean) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14f
            if (isDark) {
                setColor(Color.parseColor("#1E1E1E"))
                setStroke(2, Color.parseColor("#5B21B6"))
            } else {
                setColor(Color.parseColor("#FFFFFF"))
                setStroke(2, Color.parseColor("#D1D5DB"))
            }
        }
        editText.background = drawable
    }

    // Lifecycle methods for debugging
    override fun onStart() {
        super.onStart()
        println("LoginActivity: onStart")
    }

    override fun onResume() {
        super.onResume()
        println("LoginActivity: onResume")
    }

    override fun onPause() {
        super.onPause()
        println("LoginActivity: onPause")
    }

    override fun onStop() {
        super.onStop()
        println("LoginActivity: onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        println("LoginActivity: onDestroy")
    }
}