package com.example.neuroarenanavigation.ui.theme


import android.os.Bundle
import android.graphics.Color
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.widget.NestedScrollView
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.neuroarenanavigation.DashboardActivity
import com.example.neuroarenanavigation.PrefsManager
import com.example.neuroarenanavigation.ReactionTimeActivity
import com.example.neuroarenanavigation.ChimpTestActivity
import com.example.neuroarenanavigation.NumberMemoryActivity
import com.example.neuroarenanavigation.SequenceMemoryActivity
import com.example.neuroarenanavigation.VerbalMemoryActivity
import com.example.neuroarenanavigation.VisualMemoryActivity
import com.example.neuroarenanavigation.data.local.NeuroArenaRepository
import com.example.neuroarenanavigation.R
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

class HomeFragment : Fragment() {

    private var userName: String = ""
    private var userId: String = ""
    private var selectedGame: String = "Reaction Time"
    private lateinit var tvBenchmark: TextView
    private lateinit var tvReactionBest: TextView
    private lateinit var tvVerbalBest: TextView
    private lateinit var tvNumberBest: TextView
    private lateinit var tvVisualBest: TextView
    private lateinit var tvSequenceBest: TextView
    private lateinit var tvChimpBest: TextView
    private lateinit var svHomeRoot: NestedScrollView
    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileOverallScore: TextView
    private lateinit var ivProfileAvatar: ImageView
    private lateinit var pvProfileBackground: PlayerView
    private lateinit var vProfileOverlay: View
    private lateinit var allCards: List<CardView>
    private lateinit var cardByGame: Map<String, CardView>
    private var profilePlayer: ExoPlayer? = null

    private val gameOrder = listOf(
        "Reaction Time", "Sequence Memory", "Verbal Memory",
        "Number Memory", "Visual Memory", "Chimp Test"
    )
    private val gameDescriptions = mapOf(
        "Reaction Time" to "Measure your visual reflex speed.",
        "Sequence Memory" to "Train pattern retention and recall.",
        "Verbal Memory" to "Remember as many words as possible.",
        "Number Memory" to "Recall increasingly long number sequences.",
        "Visual Memory" to "Memorize grid squares across levels.",
        "Chimp Test" to "Beat a chimpanzee at number sequencing."
    )

    private val pickProfileImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val resolver = requireContext().contentResolver
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Some providers do not grant persistable permissions.
            }

            PrefsManager.saveProfileImageUri(requireContext(), uri.toString())
            refreshProfileHeader()
            persistCurrentUserSnapshotToDb()
            Toast.makeText(requireContext(), "Profile photo updated", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickProfileVideoLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val resolver = requireContext().contentResolver
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Some providers do not grant persistable permissions.
            }

            PrefsManager.saveProfileVideoUri(requireContext(), uri.toString())
            refreshProfileHeader()
            persistCurrentUserSnapshotToDb()
            Toast.makeText(requireContext(), "Profile card animation updated", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun newInstance(userName: String, userId: String): HomeFragment {
            val fragment = HomeFragment()
            val args = Bundle()
            args.putString("USER_NAME", userName)
            args.putString("USER_ID", userId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retrieve arguments
        arguments?.let {
            userName = it.getString("USER_NAME") ?: "Player"
            userId = it.getString("USER_ID") ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyThemeToHome(view)

        // Update welcome text with user name
        val tvWelcome = view.findViewById<TextView>(R.id.tvWelcome)
        tvWelcome.text = "Welcome, $userName!"

        tvProfileName = view.findViewById(R.id.tvProfileName)
        tvProfileOverallScore = view.findViewById(R.id.tvProfileOverallScore)
        ivProfileAvatar = view.findViewById(R.id.ivProfileAvatar)
        pvProfileBackground = view.findViewById(R.id.pvProfileBackground)
        vProfileOverlay = view.findViewById(R.id.vProfileOverlay)
        val btnEditProfile = view.findViewById<View>(R.id.btnEditProfile)

        refreshProfileHeader()

        ivProfileAvatar.setOnClickListener {
            showAvatarPickerDialog()
        }
        btnEditProfile.setOnClickListener {
            showEditProfileDialog(tvWelcome)
        }

        // Gesture toggle for theme: long-press welcome title
        tvWelcome.setOnLongClickListener {
            val newDarkMode = !PrefsManager.isDarkModeEnabled(requireContext())
            PrefsManager.saveDarkModeEnabled(requireContext(), newDarkMode)
            AppCompatDelegate.setDefaultNightMode(
                if (newDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            requireActivity().recreate()
            true
        }

        tvBenchmark = view.findViewById(R.id.tvBenchmark)
        tvReactionBest = view.findViewById(R.id.tvGame1Best)
        tvSequenceBest = view.findViewById(R.id.tvGame2Best)
        tvVerbalBest = view.findViewById(R.id.tvGame3Best)
        tvNumberBest = view.findViewById(R.id.tvGame4Best)
        tvVisualBest = view.findViewById(R.id.tvGame5Best)
        tvChimpBest = view.findViewById(R.id.tvGame6Best)
        svHomeRoot = view.findViewById(R.id.svHomeRoot)
        updateReactionBestLabel()
        updateSequenceBestLabel()
        updateVerbalBestLabel()
        updateNumberBestLabel()
        updateVisualBestLabel()
        updateChimpBestLabel()

        val reactionCard   = view.findViewById<CardView>(R.id.cvReactionTime)
        val sequenceCard  = view.findViewById<CardView>(R.id.cvSequenceMemory)
        val verbalCard    = view.findViewById<CardView>(R.id.cvVerbalMemory)
        val numberCard    = view.findViewById<CardView>(R.id.cvNumberMemory)
        val visualCard    = view.findViewById<CardView>(R.id.cvVisualMemory)
        val chimpCard     = view.findViewById<CardView>(R.id.cvChimpTest)

        allCards = listOf(reactionCard, sequenceCard, verbalCard, numberCard, visualCard, chimpCard)
        cardByGame = mapOf(
            "Reaction Time"  to reactionCard,
            "Sequence Memory" to sequenceCard,
            "Verbal Memory"  to verbalCard,
            "Number Memory"  to numberCard,
            "Visual Memory"  to visualCard,
            "Chimp Test"     to chimpCard
        )

        selectGame(PrefsManager.getSelectedGame(requireContext()), showToast = false)

        attachGameInteractions(card = reactionCard,  gameName = "Reaction Time")
        attachGameInteractions(card = sequenceCard,  gameName = "Sequence Memory")
        attachGameInteractions(card = verbalCard,    gameName = "Verbal Memory")
        attachGameInteractions(card = numberCard,    gameName = "Number Memory")
        attachGameInteractions(card = visualCard,    gameName = "Visual Memory")
        attachGameInteractions(card = chimpCard,     gameName = "Chimp Test")
    }

    private fun applyThemeToHome(view: View) {
        val isDark = PrefsManager.isDarkModeEnabled(requireContext())
        val root = view.findViewById<View>(R.id.svHomeRoot)
        val tvWelcome = view.findViewById<TextView>(R.id.tvWelcome)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvSubtitle)
        val tvBenchmarkView = view.findViewById<TextView>(R.id.tvBenchmark)
        val cvProfileHeader = view.findViewById<CardView>(R.id.cvProfileHeader)
        val llProfileContent = view.findViewById<LinearLayout>(R.id.llProfileContent)
        val tvProfileNameView = view.findViewById<TextView>(R.id.tvProfileName)
        val tvProfileScoreView = view.findViewById<TextView>(R.id.tvProfileOverallScore)
        val overlay = view.findViewById<View>(R.id.vProfileOverlay)

        if (isDark) {
            root.setBackgroundColor(Color.parseColor("#121212"))
            tvWelcome.setTextColor(Color.parseColor("#FFFFFF"))
            tvSubtitle.setTextColor(Color.parseColor("#FFFFFF"))
            tvBenchmarkView.setTextColor(Color.parseColor("#AAAAAA"))
            cvProfileHeader.setCardBackgroundColor(Color.parseColor("#1F1F1F"))
            llProfileContent.setBackgroundResource(R.drawable.bg_profile_glass_dark)
            tvProfileNameView.setTextColor(Color.parseColor("#F3F3F3"))
            tvProfileScoreView.setTextColor(Color.parseColor("#BB86FC"))
            overlay.setBackgroundColor(Color.parseColor("#33000000"))
        } else {
            root.setBackgroundColor(Color.parseColor("#FFFFFF"))
            tvWelcome.setTextColor(Color.parseColor("#111111"))
            tvSubtitle.setTextColor(Color.parseColor("#111111"))
            tvBenchmarkView.setTextColor(Color.parseColor("#5F6368"))
            cvProfileHeader.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
            llProfileContent.setBackgroundResource(R.drawable.bg_profile_glass_light)
            tvProfileNameView.setTextColor(Color.parseColor("#1C1C1C"))
            tvProfileScoreView.setTextColor(Color.parseColor("#5D2ED7"))
            overlay.setBackgroundColor(Color.parseColor("#26000000"))
        }
    }

    private fun refreshProfileHeader() {
        val name = userName.ifEmpty {
            PrefsManager.getUsername(requireContext()).ifEmpty { "Player" }
        }
        val score = PrefsManager.getOverallRankingScore(requireContext())
        val avatarKey = PrefsManager.getProfileAvatar(requireContext())
        val imageUri = PrefsManager.getProfileImageUri(requireContext())
        val videoUri = PrefsManager.getProfileVideoUri(requireContext())

        tvProfileName.text = name
        tvProfileOverallScore.text = "Overall Score: $score"
        if (imageUri.isNotBlank()) {
            try {
                ivProfileAvatar.setImageURI(Uri.parse(imageUri))
            } catch (_: Exception) {
                ivProfileAvatar.setImageResource(avatarResForKey(avatarKey))
            }
        } else {
            ivProfileAvatar.setImageResource(avatarResForKey(avatarKey))
        }

        if (videoUri.isNotBlank()) {
            vProfileOverlay.visibility = View.VISIBLE
            pvProfileBackground.visibility = View.VISIBLE
            playProfileBackgroundVideo(Uri.parse(videoUri))
        } else {
            stopProfileBackgroundVideo()
        }
    }

    private fun showAvatarPickerDialog() {
        val avatarOptions = listOf(
            "Upload from device", "Bolt", "Star", "Camera", "Compass", "User"
        )
        val avatarKeys = listOf(
            "upload", "bolt", "star", "camera", "compass", "user"
        )
        val currentIndex = avatarKeys.indexOf(PrefsManager.getProfileAvatar(requireContext())).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Choose Profile Image")
            .setSingleChoiceItems(avatarOptions.toTypedArray(), currentIndex) { dialog, which ->
                if (which == 0) {
                    pickProfileImageLauncher.launch(arrayOf("image/*"))
                } else {
                    PrefsManager.clearProfileImageUri(requireContext())
                    PrefsManager.saveProfileAvatar(requireContext(), avatarKeys[which])
                    refreshProfileHeader()
                    persistCurrentUserSnapshotToDb()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditProfileDialog(tvWelcome: TextView) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val etName = dialogView.findViewById<EditText>(R.id.etDialogUsername)
        val etPassword = dialogView.findViewById<EditText>(R.id.etDialogPassword)
        val tvManageAccountAction = dialogView.findViewById<TextView>(R.id.tvManageAccountAction)

        etName.setText(userName.ifEmpty { PrefsManager.getUsername(requireContext()) })

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setNeutralButton("Card Video") { _, _ ->
                showProfileVideoDialog()
            }
            .setPositiveButton("Save") { _, _ ->
                val newName = etName.text.toString().trim()
                val newPassword = etPassword.text.toString()

                if (newName.isNotEmpty()) {
                    userName = newName
                    PrefsManager.saveUsername(requireContext(), newName)
                    tvWelcome.text = "Welcome, $newName!"
                }
                if (newPassword.isNotBlank()) {
                    PrefsManager.savePassword(requireContext(), newPassword)
                }

                refreshProfileHeader()
                persistCurrentUserSnapshotToDb()
                Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        tvManageAccountAction.setOnClickListener {
            showAccountActionsDialog(dialog)
        }

        dialog.show()
    }

    private fun showAccountActionsDialog(parentDialog: AlertDialog) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Account Options")
            .setMessage("Manage sensitive account actions.")
            .setPositiveButton("Delete Account") { _, _ ->
                parentDialog.dismiss()
                val host = activity as? DashboardActivity
                if (host != null) {
                    host.requestDeleteAccountFromProfile()
                } else {
                    Toast.makeText(requireContext(), "Unable to open delete account flow", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showProfileVideoDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_profile_card_animation, null)

        if (PrefsManager.isDarkModeEnabled(requireContext())) {
            dialogView.setBackgroundColor(Color.parseColor("#24252F"))
            dialogView.findViewById<TextView>(R.id.tvVideoDialogTitle).setTextColor(Color.parseColor("#EFEFF4"))
            dialogView.findViewById<TextView>(R.id.tvVideoDialogSubtitle).setTextColor(Color.parseColor("#B8BBC7"))

            dialogView.findViewById<MaterialCardView>(R.id.cardUploadVideo).apply {
                setCardBackgroundColor(Color.parseColor("#2D2F3D"))
                strokeColor = Color.parseColor("#5B4B8A")
            }
            dialogView.findViewById<TextView>(R.id.tvUploadVideo).setTextColor(Color.parseColor("#E6E7ED"))

            dialogView.findViewById<MaterialCardView>(R.id.cardRemoveVideo).apply {
                setCardBackgroundColor(Color.parseColor("#3A2A2A"))
                strokeColor = Color.parseColor("#7A4C4C")
            }
            dialogView.findViewById<TextView>(R.id.tvRemoveVideo).setTextColor(Color.parseColor("#FFD9D7"))

            dialogView.findViewById<MaterialButton>(R.id.btnVideoDialogClose).apply {
                setTextColor(Color.parseColor("#C6AAFF"))
                strokeColor = ColorStateList.valueOf(Color.parseColor("#7A63A8"))
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.cardUploadVideo).setOnClickListener {
            dialog.dismiss()
            pickProfileVideoLauncher.launch(arrayOf("video/*"))
        }

        dialogView.findViewById<View>(R.id.cardRemoveVideo).setOnClickListener {
            PrefsManager.clearProfileVideoUri(requireContext())
            refreshProfileHeader()
            persistCurrentUserSnapshotToDb()
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btnVideoDialogClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun playProfileBackgroundVideo(uri: Uri) {
        val context = context ?: return
        val player = profilePlayer ?: ExoPlayer.Builder(context).build().also {
            profilePlayer = it
            pvProfileBackground.player = it
            pvProfileBackground.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            it.repeatMode = Player.REPEAT_MODE_ALL
            it.volume = 0f
            it.playWhenReady = true
        }

        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true
    }

    private fun stopProfileBackgroundVideo() {
        profilePlayer?.stop()
        pvProfileBackground.visibility = View.GONE
        vProfileOverlay.visibility = View.GONE
    }

    private fun persistCurrentUserSnapshotToDb() {
        val context = context ?: return
        val effectiveUid = userId.ifBlank { FirebaseAuth.getInstance().currentUser?.uid ?: "local_guest" }
        val email = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
        val repository = NeuroArenaRepository(context)

        lifecycleScope.launch(Dispatchers.IO) {
            repository.syncCurrentPrefsToUid(
                uid = effectiveUid,
                email = email,
                fallbackName = userName.ifBlank { "Player" }
            )
        }
    }

    private fun avatarResForKey(key: String): Int {
        return when (key) {
            "star" -> android.R.drawable.btn_star_big_on
            "camera" -> android.R.drawable.ic_menu_camera
            "compass" -> android.R.drawable.ic_menu_compass
            "user" -> android.R.drawable.ic_menu_myplaces
            else -> android.R.drawable.ic_menu_manage
        }
    }

    private fun attachGameInteractions(
        card: CardView,
        gameName: String
    ) {
        card.setOnClickListener {
            selectGame(gameName)
        }

        card.setOnLongClickListener {
            showQuickActionDialog(gameName)
            true
        }

        val doubleTapDetector = GestureDetectorCompat(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Route single-tap through performClick so accessibility and keyboard behavior stay intact.
                card.performClick()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                card.performLongClick()
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                selectGame(gameName, showToast = false)
                openRankingsForSelectedGame()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                val swipeThreshold = 120

                if (abs(diffX) > abs(diffY) && diffX < -swipeThreshold && selectedGame == gameName) {
                    launchGameForSelectedCard(gameName)
                    return true
                }

                if (abs(diffY) > abs(diffX) && abs(diffY) > swipeThreshold) {
                    // Swipe up -> move down the list, swipe down -> move up the list.
                    if (diffY < 0) {
                        moveSelection(1)
                    } else {
                        moveSelection(-1)
                    }
                    return true
                }

                return false
            }
        })

        card.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
            }

            // Consume touch here so double-tap is not lost to competing click/scroll handlers.
            doubleTapDetector.onTouchEvent(event)
        }
    }

    private fun moveSelection(direction: Int) {
        val currentIndex = gameOrder.indexOf(selectedGame)
        if (currentIndex == -1) return

        val newIndex = (currentIndex + direction + gameOrder.size) % gameOrder.size
        selectGame(gameOrder[newIndex], showToast = false)
    }

    private fun selectGame(gameName: String, showToast: Boolean = true) {
        selectedGame = gameName
        tvBenchmark.text = gameDescriptions[gameName] ?: ""
        PrefsManager.saveSelectedGame(requireContext(), gameName)

        val selectedCard = cardByGame[gameName] ?: return
        applySelectedStyle(allCards, selectedCard)
        smoothCenterCard(selectedCard)

        if (showToast) {
            Toast.makeText(requireContext(), "$gameName selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun smoothCenterCard(card: CardView) {
        svHomeRoot.post {
            val targetY = card.top - (svHomeRoot.height - card.height) / 2
            svHomeRoot.smoothScrollTo(0, max(0, targetY))
        }
    }

    private fun openRankingsForSelectedGame() {
        (activity as? DashboardActivity)?.openGameRankingsFromHome(selectedGame)
            ?: parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, GameRankingsFragment.newInstance(selectedGame, userId))
                .addToBackStack(null)
                .commit()

        Toast.makeText(requireContext(), "Opening $selectedGame Rankings", Toast.LENGTH_SHORT).show()
    }

    private fun launchGameForSelectedCard(gameName: String) {
        when (gameName) {
            "Reaction Time" -> {
                startActivity(Intent(requireContext(), ReactionTimeActivity::class.java))
            }
            "Sequence Memory" -> {
                startActivity(Intent(requireContext(), SequenceMemoryActivity::class.java))
            }
            "Chimp Test" -> {
                startActivity(Intent(requireContext(), ChimpTestActivity::class.java))
            }
            "Verbal Memory" -> {
                startActivity(Intent(requireContext(), VerbalMemoryActivity::class.java))
            }
            "Number Memory" -> {
                startActivity(Intent(requireContext(), NumberMemoryActivity::class.java))
            }
            "Visual Memory" -> {
                startActivity(Intent(requireContext(), VisualMemoryActivity::class.java))
            }
            else -> {
                Toast.makeText(requireContext(), "$gameName game coming soon", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateReactionBestLabel() {
        val bestAvg = PrefsManager.getReactionTimeBestAvgMs(requireContext())
        tvReactionBest.text = if (bestAvg == 0) "BEST: --" else "BEST: ${bestAvg}ms avg"
    }

    private fun updateVerbalBestLabel() {
        val bestWords = PrefsManager.getVerbalMemoryBestScore(requireContext())
        tvVerbalBest.text = if (bestWords == 0) "BEST: --" else "BEST: ${bestWords} Words"
    }

    private fun updateSequenceBestLabel() {
        val bestLevel = PrefsManager.getSequenceMemoryBestLevel(requireContext())
        tvSequenceBest.text = if (bestLevel == 0) "BEST: --" else "BEST: Level $bestLevel"
    }

    private fun updateNumberBestLabel() {
        val bestLevel = PrefsManager.getNumberMemoryBestLevel(requireContext())
        tvNumberBest.text = if (bestLevel == 0) "BEST: --" else "BEST: Level $bestLevel"
    }

    private fun updateVisualBestLabel() {
        val bestLevel = PrefsManager.getVisualMemoryBestLevel(requireContext())
        tvVisualBest.text = if (bestLevel == 0) "BEST: --" else "BEST: Level $bestLevel"
    }

    private fun updateChimpBestLabel() {
        val bestScore = PrefsManager.getChimpBestScore(requireContext())
        tvChimpBest.text = if (bestScore == 0) "BEST: --" else "BEST: $bestScore Numbers"
    }

    private fun applySelectedStyle(allCards: List<CardView>, selectedCard: CardView) {
        // Smooth animated transitions so swiping feels fluid.
        allCards.forEach {
            it.alpha = 1.0f
            it.cardElevation = 6f
            it.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .translationY(0f)
                .setDuration(220)
                .start()
        }

        selectedCard.cardElevation = 14f
        selectedCard.animate()
            .scaleX(1.04f)
            .scaleY(1.04f)
            .translationY(-4f)
            .setDuration(260)
            .start()
    }

    private fun showQuickActionDialog(gameName: String) {
        val quickInfo = quickInfoForGame(gameName)
        val dialogView = layoutInflater.inflate(R.layout.dialog_game_quick_info, null)

        dialogView.findViewById<TextView>(R.id.tvQuickGameTitle).text = gameName
        dialogView.findViewById<TextView>(R.id.tvQuickGameSubtitle).text = quickInfo.description
        dialogView.findViewById<TextView>(R.id.tvQuickWorldBestValue).text = quickInfo.globalBest
        dialogView.findViewById<TextView>(R.id.tvQuickYourBestValue).text = quickInfo.yourBest
        dialogView.findViewById<TextView>(R.id.tvQuickGameTips).text = quickInfo.tip

        if (PrefsManager.isDarkModeEnabled(requireContext())) {
            dialogView.findViewById<LinearLayout>(R.id.quickInfoRoot).setBackgroundColor(Color.parseColor("#22232E"))
            dialogView.findViewById<TextView>(R.id.tvQuickGameTitle).setTextColor(Color.parseColor("#ECECEC"))
            dialogView.findViewById<TextView>(R.id.tvQuickGameSubtitle).setTextColor(Color.parseColor("#C5C5C5"))
            dialogView.findViewById<TextView>(R.id.tvQuickGameTips).setTextColor(Color.parseColor("#B5B5B5"))

            dialogView.findViewById<CardView>(R.id.cvQuickWorldBest).setCardBackgroundColor(Color.parseColor("#2A2B38"))
            dialogView.findViewById<LinearLayout>(R.id.llQuickWorldBest).setBackgroundColor(Color.parseColor("#2A2B38"))
            dialogView.findViewById<CardView>(R.id.cvQuickYourBest).setCardBackgroundColor(Color.parseColor("#243241"))
            dialogView.findViewById<LinearLayout>(R.id.llQuickYourBest).setBackgroundColor(Color.parseColor("#243241"))

            dialogView.findViewById<TextView>(R.id.tvQuickWorldBestValue).setTextColor(Color.parseColor("#ECECEC"))
            dialogView.findViewById<TextView>(R.id.tvQuickYourBestValue).setTextColor(Color.parseColor("#ECECEC"))
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Play Now") { _, _ ->
                selectGame(gameName, showToast = false)
                launchGameForSelectedCard(gameName)
            }
            .setNeutralButton("Open Rankings") { _, _ ->
                selectGame(gameName, showToast = false)
                openRankingsForSelectedGame()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private data class QuickInfo(
        val description: String,
        val globalBest: String,
        val yourBest: String,
        val tip: String
    )

    private fun quickInfoForGame(gameName: String): QuickInfo {
        return when (gameName) {
            "Reaction Time" -> {
                val best = PrefsManager.getReactionTimeBestAvgMs(requireContext())
                QuickInfo(
                    description = "React as fast as possible after the green signal appears. Lower average time indicates stronger visual reflexes.",
                    globalBest = "95 ms avg (top players)",
                    yourBest = if (best == 0) "--" else "$best ms avg",
                    tip = "Tip: Focus on the center and tap only when color fully changes to avoid false starts."
                )
            }
            "Sequence Memory" -> {
                val best = PrefsManager.getSequenceMemoryBestLevel(requireContext())
                QuickInfo(
                    description = "Watch the tile sequence and repeat it in exact order. Sequence length increases each round.",
                    globalBest = "Level 62",
                    yourBest = if (best == 0) "--" else "Level $best",
                    tip = "Tip: Chunk the sequence into mini-patterns instead of remembering each tile separately."
                )
            }
            "Verbal Memory" -> {
                val best = PrefsManager.getVerbalMemoryBestScore(requireContext())
                QuickInfo(
                    description = "Identify whether each word is new or seen before. Longer streaks demand stronger recognition memory.",
                    globalBest = "236 words",
                    yourBest = if (best == 0) "--" else "$best words",
                    tip = "Tip: Build quick associations for unusual words to improve recall under pressure."
                )
            }
            "Number Memory" -> {
                val best = PrefsManager.getNumberMemoryBestLevel(requireContext())
                QuickInfo(
                    description = "Memorize and reproduce increasingly long digit strings. Higher levels reflect stronger working memory.",
                    globalBest = "Level 41",
                    yourBest = if (best == 0) "--" else "Level $best",
                    tip = "Tip: Read digits in pairs or rhythm groups to increase retention span."
                )
            }
            "Visual Memory" -> {
                val best = PrefsManager.getVisualMemoryBestLevel(requireContext())
                QuickInfo(
                    description = "Memorize highlighted squares in a grid and reproduce their positions before lives run out.",
                    globalBest = "Level 34",
                    yourBest = if (best == 0) "--" else "Level $best",
                    tip = "Tip: Mentally map tiles into corners and center zones for faster recall."
                )
            }
            else -> {
                val best = PrefsManager.getChimpBestScore(requireContext())
                QuickInfo(
                    description = "Tap numbered tiles in ascending order after they briefly appear. Speed and precision both matter.",
                    globalBest = "Score 40",
                    yourBest = if (best == 0) "--" else "$best numbers",
                    tip = "Tip: Track outermost numbers first, then fill middle tiles to reduce path confusion."
                )
            }
        }
    }

    // Lifecycle methods
    override fun onStart() {
        super.onStart()
        println("HomeFragment: onStart")
    }

    override fun onResume() {
        super.onResume()
        if (::tvReactionBest.isInitialized) {
            updateReactionBestLabel()
        }
        if (::tvVerbalBest.isInitialized) {
            updateVerbalBestLabel()
        }
        if (::tvNumberBest.isInitialized) {
            updateNumberBestLabel()
        }
        if (::tvVisualBest.isInitialized) {
            updateVisualBestLabel()
        }
        if (::tvSequenceBest.isInitialized) {
            updateSequenceBestLabel()
        }
        if (::tvChimpBest.isInitialized) {
            updateChimpBestLabel()
        }
        if (::tvProfileName.isInitialized) {
            refreshProfileHeader()
        }
        println("HomeFragment: onResume")
    }

    override fun onPause() {
        if (::pvProfileBackground.isInitialized && pvProfileBackground.visibility == View.VISIBLE) {
            profilePlayer?.pause()
        }
        super.onPause()
        println("HomeFragment: onPause")
    }

    override fun onStop() {
        profilePlayer?.pause()
        super.onStop()
        println("HomeFragment: onStop")
    }

    override fun onDestroyView() {
        pvProfileBackground.player = null
        profilePlayer?.release()
        profilePlayer = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        println("HomeFragment: onDestroy")
    }
}