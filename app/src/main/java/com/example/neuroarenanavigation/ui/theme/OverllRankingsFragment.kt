package com.example.neuroarenanavigation.ui.theme

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.example.neuroarenanavigation.R
import com.example.neuroarenanavigation.PrefsManager
import com.example.neuroarenanavigation.data.remote.OnlineProfilesRepository
import com.example.neuroarenanavigation.data.remote.OnlineUserProfile

class OverallRankingsFragment : Fragment() {

    private var userId: String = ""
    private var userName: String = ""
    private val onlineProfilesRepository = OnlineProfilesRepository()
    private var lastOnlineQuery: String = ""
    private var onlineSearchInFlight: Boolean = false

    private data class PlayerOverall(
        val rank: Int,
        val name: String,
        val points: Int,
        val percentile: String,
        val avatarRes: Int,
        val gameScores: List<Pair<String, String>>
    )

    private val topPlayers = listOf(
        PlayerOverall(
            rank = 1,
            name = "Alex Rivera",
            points = 995,
            percentile = "99.9TH PERCENTILE",
            avatarRes = android.R.drawable.ic_menu_myplaces,
            gameScores = listOf(
                "Reaction Time" to "146 ms avg",
                "Sequence Memory" to "Level 47",
                "Verbal Memory" to "82 Words",
                "Number Memory" to "Level 17",
                "Visual Memory" to "Level 16",
                "Chimp Test" to "Score 26"
            )
        ),
        PlayerOverall(
            rank = 2,
            name = "Sam Chen",
            points = 988,
            percentile = "99.8TH PERCENTILE",
            avatarRes = android.R.drawable.ic_menu_camera,
            gameScores = listOf(
                "Reaction Time" to "153 ms avg",
                "Sequence Memory" to "Level 44",
                "Verbal Memory" to "77 Words",
                "Number Memory" to "Level 15",
                "Visual Memory" to "Level 15",
                "Chimp Test" to "Score 24"
            )
        ),
        PlayerOverall(
            rank = 3,
            name = "Jordan Taylor",
            points = 982,
            percentile = "99.7TH PERCENTILE",
            avatarRes = android.R.drawable.ic_menu_compass,
            gameScores = listOf(
                "Reaction Time" to "161 ms avg",
                "Sequence Memory" to "Level 42",
                "Verbal Memory" to "73 Words",
                "Number Memory" to "Level 14",
                "Visual Memory" to "Level 13",
                "Chimp Test" to "Score 23"
            )
        )
    )

    companion object {
        fun newInstance(userId: String, userName: String): OverallRankingsFragment {
            val fragment = OverallRankingsFragment()
            val args = Bundle()
            args.putString("USER_ID", userId)
            args.putString("USER_NAME", userName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retrieve arguments
        arguments?.let {
            userId = it.getString("USER_ID") ?: ""
            userName = it.getString("USER_NAME") ?: "Player"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_overall_rankings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyThemeToOverall(view)

        userName = PrefsManager.getUsername(requireContext()).ifEmpty { userName }

        val overallScore = PrefsManager.getOverallRankingScore(requireContext())
        val computedRank = if (overallScore <= 0) 9999 else (5000 - (overallScore * 2)).coerceAtLeast(250)

        // Update user info
        val tvYourRank = view.findViewById<TextView>(R.id.tvYourRank)
        val tvYourPercentile = view.findViewById<TextView>(R.id.tvYourPercentile)
        tvYourRank.text = "#${String.format("%,d", computedRank)}"
        tvYourPercentile.text = "Overall Score $overallScore"

        // View Stats button
        val btnViewStats = view.findViewById<Button>(R.id.btnViewStats)
        btnViewStats.setOnClickListener {
            showMyStatsDialog(overallScore, computedRank)
        }

        // Share button - IMPLICIT INTENT
        val btnShare = view.findViewById<Button>(R.id.btnShare)
        btnShare.setOnClickListener {
            shareRanking(computedRank)
        }

        setupRows(view)
        setupSearch(view)
    }

    private fun applyThemeToOverall(root: View) {
        val isDark = PrefsManager.isDarkModeEnabled(requireContext())
        if (!isDark) return

        root.setBackgroundColor(Color.parseColor("#121212"))
        root.findViewById<TextView>(R.id.tvTitle).setTextColor(Color.parseColor("#BB86FC"))
        root.findViewById<CardView>(R.id.cvUserStanding).setCardBackgroundColor(Color.parseColor("#1F1F1F"))
        root.findViewById<TextView>(R.id.tvGlobalHeader).setTextColor(Color.parseColor("#E5E5E5"))
        root.findViewById<TextView>(R.id.tvAllTime).setTextColor(Color.parseColor("#9E9E9E"))
        root.findViewById<TextView>(R.id.tvSearchHint).setTextColor(Color.parseColor("#AFAFAF"))
        root.findViewById<TextView>(R.id.tvNoSearchResults).setTextColor(Color.parseColor("#AFAFAF"))

        val search = root.findViewById<EditText>(R.id.etPlayerSearch)
        search.setTextColor(Color.parseColor("#FFFFFF"))
        search.setHintTextColor(Color.parseColor("#8F8F8F"))
        search.setBackgroundColor(Color.parseColor("#232323"))

        val tableHeader = root.findViewById<View>(R.id.tableHeader)
        tableHeader.setBackgroundColor(Color.parseColor("#252525"))
        root.findViewById<TextView>(R.id.tvHeaderRank).setTextColor(Color.parseColor("#D0D0D0"))
        root.findViewById<TextView>(R.id.tvHeaderName).setTextColor(Color.parseColor("#D0D0D0"))
        root.findViewById<TextView>(R.id.tvHeaderPoints).setTextColor(Color.parseColor("#D0D0D0"))
    }

    private fun setupRows(root: View) {
        bindRow(
            root = root,
            rowId = R.id.rank1,
            rankId = R.id.tvRank1,
            nameId = R.id.tvName1,
            pointsId = R.id.tvPoints1,
            percentileId = R.id.tvPercentile1,
            player = topPlayers[0]
        )
        bindRow(
            root = root,
            rowId = R.id.rank2,
            rankId = R.id.tvRank2,
            nameId = R.id.tvName2,
            pointsId = R.id.tvPoints2,
            percentileId = R.id.tvPercentile2,
            player = topPlayers[1]
        )
        bindRow(
            root = root,
            rowId = R.id.rank3,
            rankId = R.id.tvRank3,
            nameId = R.id.tvName3,
            pointsId = R.id.tvPoints3,
            percentileId = R.id.tvPercentile3,
            player = topPlayers[2]
        )
    }

    private fun bindRow(
        root: View,
        rowId: Int,
        rankId: Int,
        nameId: Int,
        pointsId: Int,
        percentileId: Int,
        player: PlayerOverall
    ) {
        val row = root.findViewById<ConstraintLayout>(rowId)
        row.findViewById<TextView>(rankId).text = player.rank.toString()
        row.findViewById<TextView>(nameId).text = player.name
        row.findViewById<TextView>(pointsId).text = "${player.points} pts"
        row.findViewById<TextView>(percentileId).text = player.percentile

        row.setOnClickListener {
            showPlayerStatsDialog(
                displayName = player.name,
                displayRank = "#${player.rank}",
                points = "${player.points} pts",
                percentile = player.percentile,
                gameScores = player.gameScores,
                avatarRes = player.avatarRes,
                customImageUri = null
            )
        }
    }

    private fun setupSearch(root: View) {
        val etPlayerSearch = root.findViewById<EditText>(R.id.etPlayerSearch)
        val noResults = root.findViewById<TextView>(R.id.tvNoSearchResults)
        val rank1 = root.findViewById<View>(R.id.rank1)
        val rank2 = root.findViewById<View>(R.id.rank2)
        val rank3 = root.findViewById<View>(R.id.rank3)

        val rows = listOf(rank1, rank2, rank3)

        etPlayerSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim()?.lowercase().orEmpty()
                lastOnlineQuery = query
                var visibleCount = 0

                topPlayers.forEachIndexed { index, player ->
                    val visible = query.isEmpty() || player.name.lowercase().contains(query)
                    rows[index].visibility = if (visible) View.VISIBLE else View.GONE
                    if (visible) visibleCount++
                }

                if (query.isEmpty()) {
                    noResults.visibility = View.GONE
                    onlineSearchInFlight = false
                    return
                }

                if (visibleCount > 0) {
                    noResults.visibility = View.GONE
                    return
                }

                noResults.visibility = View.VISIBLE
                noResults.text = "Searching online users..."
                searchOnlineUser(query, noResults)
            }
        })
    }

    private fun searchOnlineUser(query: String, noResultsView: TextView) {
        if (query.length < 2) {
            noResultsView.text = "Type at least 2 letters to search online"
            return
        }
        if (onlineSearchInFlight) return

        onlineSearchInFlight = true
        onlineProfilesRepository.searchByUsernamePrefix(
            prefix = query,
            limit = 1,
            onResult = { users ->
                if (!isAdded) return@searchByUsernamePrefix
                onlineSearchInFlight = false

                if (lastOnlineQuery != query.lowercase()) return@searchByUsernamePrefix

                if (users.isEmpty()) {
                    noResultsView.text = "No local or online users found"
                } else {
                    noResultsView.visibility = View.GONE
                    showOnlineUserStatsDialog(users.first())
                }
            },
            onError = { _ ->
                if (!isAdded) return@searchByUsernamePrefix
                onlineSearchInFlight = false
                noResultsView.text = "Online search unavailable"
            }
        )
    }

    private fun showOnlineUserStatsDialog(profile: OnlineUserProfile) {
        val gameScores = listOf(
            "Reaction Time" to if (profile.reactionTimeBestAvgMs == 0) "--" else "${profile.reactionTimeBestAvgMs} ms avg",
            "Sequence Memory" to if (profile.sequenceBestLevel == 0) "--" else "Level ${profile.sequenceBestLevel}",
            "Verbal Memory" to if (profile.verbalBestScore == 0) "--" else "${profile.verbalBestScore} words",
            "Number Memory" to if (profile.numberBestLevel == 0) "--" else "Level ${profile.numberBestLevel}",
            "Visual Memory" to if (profile.visualBestLevel == 0) "--" else "Level ${profile.visualBestLevel}",
            "Chimp Test" to if (profile.chimpBestScore == 0) "--" else "Score ${profile.chimpBestScore}"
        )

        showPlayerStatsDialog(
            displayName = profile.username.ifBlank { "Player" },
            displayRank = "#--",
            points = "${profile.overallScore} pts",
            percentile = "Online profile",
            gameScores = gameScores,
            avatarRes = avatarResForKey(profile.avatarKey),
            customImageUri = profile.profileImageUri
        )
    }

    private fun showMyStatsDialog(overallScore: Int, computedRank: Int) {
        val bestReaction = PrefsManager.getReactionTimeBestAvgMs(requireContext())
        val bestSequence = PrefsManager.getSequenceMemoryBestLevel(requireContext())
        val bestVerbal = PrefsManager.getVerbalMemoryBestScore(requireContext())
        val bestNumber = PrefsManager.getNumberMemoryBestLevel(requireContext())
        val bestVisual = PrefsManager.getVisualMemoryBestLevel(requireContext())
        val bestChimp = PrefsManager.getChimpBestScore(requireContext())

        val gameScores = listOf(
            "Reaction Time" to if (bestReaction == 0) "--" else "$bestReaction ms avg",
            "Sequence Memory" to if (bestSequence == 0) "--" else "Level $bestSequence",
            "Verbal Memory" to if (bestVerbal == 0) "--" else "$bestVerbal words",
            "Number Memory" to if (bestNumber == 0) "--" else "Level $bestNumber",
            "Visual Memory" to if (bestVisual == 0) "--" else "Level $bestVisual",
            "Chimp Test" to if (bestChimp == 0) "--" else "Score $bestChimp"
        )

        showPlayerStatsDialog(
            displayName = userName,
            displayRank = "#${String.format("%,d", computedRank)}",
            points = "$overallScore pts",
            percentile = "Your current overall score",
            gameScores = gameScores,
            avatarRes = avatarResForKey(PrefsManager.getProfileAvatar(requireContext())),
            customImageUri = PrefsManager.getProfileImageUri(requireContext())
        )
    }

    private fun showPlayerStatsDialog(
        displayName: String,
        displayRank: String,
        points: String,
        percentile: String,
        gameScores: List<Pair<String, String>>,
        avatarRes: Int,
        customImageUri: String?
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_player_profile_stats, null)

        val ivAvatar = dialogView.findViewById<ImageView>(R.id.ivDialogAvatar)
        val tvName = dialogView.findViewById<TextView>(R.id.tvDialogPlayerName)
        val tvRank = dialogView.findViewById<TextView>(R.id.tvDialogRank)
        val tvPoints = dialogView.findViewById<TextView>(R.id.tvDialogPoints)
        val tvPercentile = dialogView.findViewById<TextView>(R.id.tvDialogPercentile)
        val tvReaction = dialogView.findViewById<TextView>(R.id.tvStatReaction)
        val tvSequence = dialogView.findViewById<TextView>(R.id.tvStatSequence)
        val tvVerbal = dialogView.findViewById<TextView>(R.id.tvStatVerbal)
        val tvNumber = dialogView.findViewById<TextView>(R.id.tvStatNumber)
        val tvVisual = dialogView.findViewById<TextView>(R.id.tvStatVisual)
        val tvChimp = dialogView.findViewById<TextView>(R.id.tvStatChimp)

        tvName.text = displayName
        tvRank.text = displayRank
        tvPoints.text = points
        tvPercentile.text = percentile

        val scoreMap = gameScores.toMap()
        tvReaction.text = "Reaction Time: ${scoreMap["Reaction Time"] ?: "--"}"
        tvSequence.text = "Sequence Memory: ${scoreMap["Sequence Memory"] ?: "--"}"
        tvVerbal.text = "Verbal Memory: ${scoreMap["Verbal Memory"] ?: "--"}"
        tvNumber.text = "Number Memory: ${scoreMap["Number Memory"] ?: "--"}"
        tvVisual.text = "Visual Memory: ${scoreMap["Visual Memory"] ?: "--"}"
        tvChimp.text = "Chimp Test: ${scoreMap["Chimp Test"] ?: "--"}"

        if (!customImageUri.isNullOrBlank()) {
            try {
                ivAvatar.setImageURI(Uri.parse(customImageUri))
            } catch (_: Exception) {
                ivAvatar.setImageResource(avatarRes)
            }
        } else {
            ivAvatar.setImageResource(avatarRes)
        }

        if (PrefsManager.isDarkModeEnabled(requireContext())) {
            dialogView.findViewById<LinearLayout>(R.id.llPlayerStatsContent).setBackgroundColor(Color.parseColor("#22232E"))
            dialogView.findViewById<TextView>(R.id.tvDialogPlayerName).setTextColor(Color.parseColor("#ECECEC"))
            dialogView.findViewById<TextView>(R.id.tvGamewiseTitle).setTextColor(Color.parseColor("#E2E2E2"))

            dialogView.findViewById<CardView>(R.id.cvPlayerPoints).setCardBackgroundColor(Color.parseColor("#2A2B38"))
            dialogView.findViewById<LinearLayout>(R.id.llPlayerPoints).setBackgroundColor(Color.parseColor("#2A2B38"))

            val statIds = listOf(
                R.id.tvStatReaction,
                R.id.tvStatSequence,
                R.id.tvStatVerbal,
                R.id.tvStatNumber,
                R.id.tvStatVisual,
                R.id.tvStatChimp
            )
            statIds.forEach { id ->
                val tv = dialogView.findViewById<TextView>(id)
                tv.setBackgroundColor(Color.parseColor("#313342"))
                tv.setTextColor(Color.parseColor("#ECECEC"))
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
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

    private fun shareRanking(rank: Int) {
        // IMPLICIT INTENT - Share
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_TEXT, "I'm ranked #$rank on NeuroArena with strong game-wise scores. Check it out!")

        try {
            startActivity(Intent.createChooser(shareIntent, "Share your ranking"))
            Toast.makeText(requireContext(), "Sharing your score...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No app available to share", Toast.LENGTH_SHORT).show()
        }
    }

    // Lifecycle methods
    override fun onStart() {
        super.onStart()
        println("OverallRankingsFragment: onStart")
    }

    override fun onResume() {
        super.onResume()
        userName = PrefsManager.getUsername(requireContext()).ifEmpty { userName }
        println("OverallRankingsFragment: onResume")
    }

    override fun onPause() {
        super.onPause()
        println("OverallRankingsFragment: onPause")
    }

    override fun onStop() {
        super.onStop()
        println("OverallRankingsFragment: onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        println("OverallRankingsFragment: onDestroy")
    }
}