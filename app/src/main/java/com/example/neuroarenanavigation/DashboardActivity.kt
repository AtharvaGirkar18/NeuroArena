package com.example.neuroarenanavigation

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.neuroarenanavigation.ui.theme.HomeFragment
import com.example.neuroarenanavigation.ui.theme.GameRankingsFragment
import com.example.neuroarenanavigation.ui.theme.OverallRankingsFragment
import com.example.neuroarenanavigation.data.local.NeuroArenaRepository
import com.example.neuroarenanavigation.data.remote.OnlineProfilesRepository
import androidx.activity.OnBackPressedCallback
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class DashboardActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var btnTopRadio: android.widget.Button

    // Data received from LoginActivity
    private var userName: String = "Player"
    private var userEmail: String = ""
    private var userId: String = ""
    private var suppressNavigationCallback: Boolean = false
    private var isAccountRemovalInProgress: Boolean = false
    private lateinit var googleReauthClient: GoogleSignInClient
    private val onlineProfilesRepository = OnlineProfilesRepository()

    private val radioStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == ChillPlayRadioService.ACTION_RADIO_STATE_CHANGED) {
                refreshRadioButtonLabel()
            }
        }
    }

    private val googleReauthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                Toast.makeText(this, "Google re-auth failed (missing token)", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }

            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show()
                navigateToLoginAfterAccountRemoval()
                return@registerForActivityResult
            }

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            user.reauthenticate(credential)
                .addOnSuccessListener {
                    retryDeleteAfterReauth(user)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Google re-authentication failed", Toast.LENGTH_LONG).show()
                }
        } catch (_: Exception) {
            Toast.makeText(this, "Google re-authentication cancelled", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(
            if (PrefsManager.isDarkModeEnabled(this)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        AppNotifications.ensureChannels(this)
        NotificationScheduler.schedule(this)
        initializeFcm()
        configureGoogleReauth()

        // Initialize views
        bottomNavigation = findViewById(R.id.bottomNavigation)
        btnTopRadio = findViewById(R.id.btnTopRadio)
        val btnTopLogout = findViewById<android.widget.Button>(R.id.btnTopLogout)
        applyThemeToDashboard()
        refreshRadioButtonLabel()

        btnTopRadio.setOnClickListener {
            if (ChillPlayRadioService.isRunning(this)) {
                ChillPlayRadioService.stopService(this)
            } else {
                ChillPlayRadioService.startService(this)
            }
            refreshRadioButtonLabel()
        }

        btnTopLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN)
                .signOut()
                .addOnCompleteListener {
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
        }

        // RECEIVE DATA USING getIntent() - Getting data passed from LoginActivity
        userName = intent.getStringExtra("USER_NAME") ?: PrefsManager.getUsername(this).ifEmpty { "Player" }
        userEmail = intent.getStringExtra("USER_EMAIL") ?: ""
        userId = intent.getStringExtra("USER_ID") ?: ""

        bootstrapRoomData {
            // Show welcome toast with hydrated name
            Toast.makeText(this, "Welcome $userName!", Toast.LENGTH_SHORT).show()
            syncCurrentProfileToCloud()

            // Load default fragment (Home) only after hydration is complete.
            loadFragment(HomeFragment.newInstance(userName, userId))

            // Bottom navigation item selection
            bottomNavigation.setOnItemSelectedListener { menuItem ->
                if (suppressNavigationCallback) {
                    suppressNavigationCallback = false
                    return@setOnItemSelectedListener true
                }

                when (menuItem.itemId) {
                    R.id.nav_home -> {
                        loadFragment(HomeFragment.newInstance(userName, userId))
                        true
                    }
                    R.id.nav_game_rankings -> {
                        loadFragment(GameRankingsFragment.newInstance(PrefsManager.getSelectedGame(this).ifEmpty { "Reaction Time" }, userId))
                        true
                    }
                    R.id.nav_overall_rankings -> {
                        loadFragment(OverallRankingsFragment.newInstance(userId, userName))
                        true
                    }
                    else -> false
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Add button to go to SecondActivity (for Intent demo)
        // You can add this in XML if needed
    }

    private fun loadFragment(fragment: Fragment) {
        // FRAGMENT TRANSACTION with BackStack
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null) // Adds to backstack for proper back navigation
            .commit()

        // Toast to confirm fragment navigation
        Toast.makeText(this, "Loading ${fragment::class.java.simpleName}", Toast.LENGTH_SHORT).show()
    }

    fun openGameRankingsFromHome(gameName: String) {
        PrefsManager.saveSelectedGame(this, gameName)
        suppressNavigationCallback = true
        bottomNavigation.selectedItemId = R.id.nav_game_rankings
        loadFragment(GameRankingsFragment.newInstance(gameName, userId))
    }

    private fun applyThemeToDashboard() {
        val isDark = PrefsManager.isDarkModeEnabled(this)

        val root = findViewById<ConstraintLayout>(R.id.rootDashboard)
        val topBar = findViewById<CardView>(R.id.topBar)
        val title = findViewById<TextView>(R.id.tvAppTitle)
        val btnRadio = findViewById<android.widget.Button>(R.id.btnTopRadio)
        val btnLogout = findViewById<android.widget.Button>(R.id.btnTopLogout)

        if (isDark) {
            root.setBackgroundColor(Color.parseColor("#121212"))
            topBar.setCardBackgroundColor(Color.parseColor("#1E1E1E"))
            title.setTextColor(Color.parseColor("#FFFFFF"))
            btnRadio.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#5C5C5C"))
            btnLogout.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#5C5C5C"))
            bottomNavigation.setBackgroundColor(Color.parseColor("#1F1F1F"))
            val active = Color.parseColor("#BB86FC")
            val inactive = Color.parseColor("#C4C4C4")
            bottomNavigation.itemIconTintList = ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked)),
                intArrayOf(inactive, active)
            )
            bottomNavigation.itemTextColor = bottomNavigation.itemIconTintList
        } else {
            root.setBackgroundColor(Color.parseColor("#FFFFFF"))
            topBar.setCardBackgroundColor(Color.parseColor("#6200EE"))
            title.setTextColor(Color.parseColor("#FFFFFF"))
            btnRadio.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4A4A4A"))
            btnLogout.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4A4A4A"))
            bottomNavigation.setBackgroundColor(Color.parseColor("#FFFFFF"))
            val active = Color.parseColor("#6200EE")
            val inactive = Color.parseColor("#8E8E8E")
            bottomNavigation.itemIconTintList = ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked)),
                intArrayOf(inactive, active)
            )
            bottomNavigation.itemTextColor = bottomNavigation.itemIconTintList
        }
    }

    private fun refreshRadioButtonLabel() {
        if (!::btnTopRadio.isInitialized) return
        btnTopRadio.text = if (ChillPlayRadioService.isRunning(this)) "Radio Off" else "Radio On"
    }

    // Method to navigate to SecondActivity (can be called from fragments)
    fun navigateToSecondActivity(data: String) {
        val intent = Intent(this, SecondActivity::class.java)
        intent.putExtra("USER_DATA", data)
        intent.putExtra("USER_ID", 123)
        startActivity(intent)
        Toast.makeText(this, "Navigating to SecondActivity", Toast.LENGTH_SHORT).show()
    }

    private fun initializeFcm() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (token.isNotBlank()) {
                    PrefsManager.saveFcmToken(this, token)
                }
            }

        FirebaseMessaging.getInstance().subscribeToTopic("neuroarena_general")
    }

    private fun configureGoogleReauth() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleReauthClient = GoogleSignIn.getClient(this, gso)
    }

    private fun bootstrapRoomData(onComplete: () -> Unit) {
        val effectiveUid = if (userId.isBlank()) "local_guest" else userId
        val repository = NeuroArenaRepository(this)

        lifecycleScope.launch(Dispatchers.IO) {
            repository.hydratePrefsForUid(
                uid = effectiveUid,
                email = userEmail,
                fallbackName = userName
            )

            runOnUiThread {
                userName = PrefsManager.getUsername(this@DashboardActivity).ifEmpty { userName }
                onComplete()
            }
        }
    }

    private fun showDeleteAccountConfirmation() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_account, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDeleteTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvDeleteSubtitle)
        val warningBox = dialogView.findViewById<LinearLayout>(R.id.llWarningBox)
        val tvWarningIcon = dialogView.findViewById<TextView>(R.id.tvWarningIcon)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDeleteMessage)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnDeleteCancel)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnDeleteConfirm)

        val isDark = PrefsManager.isDarkModeEnabled(this)
        if (isDark) {
            dialogView.setBackgroundColor(Color.parseColor("#24252F"))
            tvTitle.setTextColor(Color.parseColor("#EFEFF4"))
            tvSubtitle.setTextColor(Color.parseColor("#B8BBC7"))
            warningBox.setBackgroundColor(Color.parseColor("#3A2A2A"))
            tvWarningIcon.setTextColor(Color.parseColor("#FF8A80"))
            tvMessage.setTextColor(Color.parseColor("#E8D6D6"))
            btnCancel.setTextColor(Color.parseColor("#D1D1D1"))
            btnCancel.strokeColor = ColorStateList.valueOf(Color.parseColor("#6F6F76"))
            btnConfirm.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))
        } else {
            btnCancel.strokeColor = ColorStateList.valueOf(Color.parseColor("#C8C8C8"))
            btnConfirm.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B71C1C"))
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            deleteAccountFlow()
        }

        dialog.show()
    }

    fun requestDeleteAccountFromProfile() {
        showDeleteAccountConfirmation()
    }

    private fun deleteAccountFlow() {
        if (isAccountRemovalInProgress) return
        isAccountRemovalInProgress = true

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            clearLocalDataAndSignOut(showMessage = "Local account data deleted")
            return
        }

        currentUser.delete()
            .addOnSuccessListener {
                clearLocalDataAndSignOut(showMessage = "Account deleted successfully")
            }
            .addOnFailureListener { error ->
                if (error is FirebaseAuthRecentLoginRequiredException) {
                    promptReauthAndDelete(currentUser)
                } else {
                    isAccountRemovalInProgress = false
                    Toast.makeText(this, "Could not delete account: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun promptReauthAndDelete(user: FirebaseUser) {
        val providers = user.providerData.mapNotNull { it.providerId }.toSet()

        if (providers.contains("password")) {
            showPasswordReauthDialog(user)
            return
        }

        if (providers.contains("google.com")) {
            googleReauthClient.signOut().addOnCompleteListener {
                googleReauthLauncher.launch(googleReauthClient.signInIntent)
            }
            return
        }

        isAccountRemovalInProgress = false
        Toast.makeText(this, "Please re-login and try deleting account again.", Toast.LENGTH_LONG).show()
    }

    private fun showPasswordReauthDialog(user: FirebaseUser) {
        val input = EditText(this).apply {
            hint = "Enter password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Confirm Password")
            .setMessage("For security, please enter your password to delete your account.")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Confirm") { _, _ ->
                val password = input.text.toString()
                if (password.isBlank() || user.email.isNullOrBlank()) {
                    isAccountRemovalInProgress = false
                    Toast.makeText(this, "Password is required", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val credential = EmailAuthProvider.getCredential(user.email!!, password)
                user.reauthenticate(credential)
                    .addOnSuccessListener {
                        retryDeleteAfterReauth(user)
                    }
                    .addOnFailureListener {
                        isAccountRemovalInProgress = false
                        Toast.makeText(this, "Re-authentication failed. Wrong password?", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancel") { _, _ ->
                isAccountRemovalInProgress = false
            }
            .show()
    }

    private fun retryDeleteAfterReauth(user: FirebaseUser) {
        user.delete()
            .addOnSuccessListener {
                clearLocalDataAndSignOut(showMessage = "Account deleted successfully")
            }
            .addOnFailureListener {
                isAccountRemovalInProgress = false
                Toast.makeText(this, "Could not delete account after re-authentication.", Toast.LENGTH_LONG).show()
            }
    }

    private fun clearLocalDataAndSignOut(showMessage: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: if (userId.isBlank()) "local_guest" else userId
        val repository = NeuroArenaRepository(this)

        lifecycleScope.launch(Dispatchers.IO) {
            repository.clearLocalDataForUid(uid)

            runOnUiThread {
                PrefsManager.clearAll(this@DashboardActivity)
                val auth = FirebaseAuth.getInstance()
                auth.signOut()
                GoogleSignIn.getClient(this@DashboardActivity, GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .signOut()
                    .addOnCompleteListener {
                        Toast.makeText(this@DashboardActivity, showMessage, Toast.LENGTH_LONG).show()
                        navigateToLoginAfterAccountRemoval()
                    }
            }
        }
    }

    private fun navigateToLoginAfterAccountRemoval() {
        isAccountRemovalInProgress = false
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // Lifecycle methods
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ChillPlayRadioService.ACTION_RADIO_STATE_CHANGED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(radioStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(radioStateReceiver, filter)
        }
        refreshRadioButtonLabel()
        println("DashboardActivity: onStart")
    }

    override fun onResume() {
        super.onResume()
        refreshRadioButtonLabel()
        syncCurrentProfileToCloud()
        println("DashboardActivity: onResume")
    }

    private fun syncCurrentProfileToCloud() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val fallbackName = PrefsManager.getUsername(this).ifEmpty { userName.ifBlank { "Player" } }
        onlineProfilesRepository.syncCurrentUser(this, currentUser.uid, fallbackName)
    }

    override fun onPause() {
        if (isAccountRemovalInProgress) {
            super.onPause()
            println("DashboardActivity: onPause (account removal in progress, skip sync)")
            return
        }

        val effectiveUid = if (userId.isBlank()) "local_guest" else userId
        val repository = NeuroArenaRepository(this)
        lifecycleScope.launch(Dispatchers.IO) {
            repository.syncCurrentPrefsToUid(
                uid = effectiveUid,
                email = userEmail,
                fallbackName = userName
            )
        }
        super.onPause()
        println("DashboardActivity: onPause")
    }

    override fun onStop() {
        runCatching { unregisterReceiver(radioStateReceiver) }
        super.onStop()
        println("DashboardActivity: onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        println("DashboardActivity: onDestroy")
    }

}