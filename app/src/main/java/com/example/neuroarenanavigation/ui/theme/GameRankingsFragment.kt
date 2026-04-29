package com.example.neuroarenanavigation.ui.theme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import androidx.cardview.widget.CardView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.neuroarenanavigation.PrefsManager
import com.example.neuroarenanavigation.R
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class GameRankingsFragment : Fragment() {

    private var gameType: String = ""
    private var userId: String = ""

    private lateinit var tvTitle: TextView
    private lateinit var tvReactionChip: TextView
    private lateinit var tvSequenceChip: TextView
    private lateinit var tvVerbalChip: TextView
    private lateinit var tvNumberChip: TextView
    private lateinit var tvVisualChip: TextView
    private lateinit var tvChimpChip: TextView
    private lateinit var tvName1: TextView
    private lateinit var tvName2: TextView
    private lateinit var tvName3: TextView
    private lateinit var tvScore1: TextView
    private lateinit var tvScore2: TextView
    private lateinit var tvScore3: TextView
    private lateinit var tvYourName: TextView
    private lateinit var tvYourScore: TextView
    private lateinit var tvApiStatus: TextView
    private lateinit var pbLoading: ProgressBar

    private val httpClient = OkHttpClient()

    companion object {
        fun newInstance(gameType: String, userId: String): GameRankingsFragment {
            val fragment = GameRankingsFragment()
            val args = Bundle()
            args.putString("GAME_TYPE", gameType)
            args.putString("USER_ID", userId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retrieve arguments
        arguments?.let {
            gameType = it.getString("GAME_TYPE") ?: "Reaction Time"
            userId = it.getString("USER_ID") ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_game_rankings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTitle = view.findViewById(R.id.tvTitle)
        tvReactionChip = view.findViewById(R.id.tvReactionChip)
        tvSequenceChip = view.findViewById(R.id.tvSequenceChip)
        tvVerbalChip = view.findViewById(R.id.tvVerbalChip)
        tvNumberChip = view.findViewById(R.id.tvNumberChip)
        tvVisualChip = view.findViewById(R.id.tvVisualChip)
        tvChimpChip = view.findViewById(R.id.tvChimpChip)
        tvName1 = view.findViewById(R.id.tvName1)
        tvName2 = view.findViewById(R.id.tvName2)
        tvName3 = view.findViewById(R.id.tvName3)
        tvScore1 = view.findViewById(R.id.tvScore1)
        tvScore2 = view.findViewById(R.id.tvScore2)
        tvScore3 = view.findViewById(R.id.tvScore3)
        tvYourName = view.findViewById(R.id.tvYourName)
        tvYourScore = view.findViewById(R.id.tvYourScore)
        tvApiStatus = view.findViewById(R.id.tvApiStatus)
        pbLoading = view.findViewById(R.id.pbLoading)

        applyThemeToRankings(view)

        tvReactionChip.setOnClickListener {
            updateRankingsForGame("Reaction Time")
        }

        tvSequenceChip.setOnClickListener {
            updateRankingsForGame("Sequence Memory")
        }

        tvVerbalChip.setOnClickListener {
            updateRankingsForGame("Verbal Memory")
        }

        tvNumberChip.setOnClickListener {
            updateRankingsForGame("Number Memory")
        }

        tvVisualChip.setOnClickListener {
            updateRankingsForGame("Visual Memory")
        }

        tvChimpChip.setOnClickListener {
            updateRankingsForGame("Chimp Test")
        }

        // Show user-friendly identity instead of raw uid.
        val friendlyName = PrefsManager.getUsername(requireContext()).ifEmpty { "Player" }
        tvYourName.text = "You ($friendlyName)"

        updateRankingsForGame(gameType)
    }

    private fun applyThemeToRankings(root: View) {
        val isDark = PrefsManager.isDarkModeEnabled(requireContext())
        if (!isDark) return

        root.setBackgroundColor(Color.parseColor("#121212"))
        tvTitle.setTextColor(Color.parseColor("#BB86FC"))
        root.findViewById<TextView>(R.id.tvSubtitle).setTextColor(Color.parseColor("#D0D0D0"))
        root.findViewById<TextView>(R.id.tvWorldRecord).setTextColor(Color.parseColor("#FFD54F"))
        root.findViewById<TextView>(R.id.tvYourRank).setTextColor(Color.parseColor("#BB86FC"))
        root.findViewById<TextView>(R.id.tvApiStatus).setTextColor(Color.parseColor("#9B9B9B"))

        val darkCard = Color.parseColor("#1F1F1F")
        root.findViewById<CardView>(R.id.cvRank1).setCardBackgroundColor(darkCard)
        root.findViewById<CardView>(R.id.cvRank2).setCardBackgroundColor(darkCard)
        root.findViewById<CardView>(R.id.cvRank3).setCardBackgroundColor(darkCard)
        root.findViewById<CardView>(R.id.cvYourRank).setCardBackgroundColor(Color.parseColor("#272727"))
        root.findViewById<View>(R.id.clYourRankContainer).setBackgroundColor(Color.parseColor("#272727"))

        root.findViewById<TextView>(R.id.tvName1).setTextColor(Color.parseColor("#E8E8E8"))
        root.findViewById<TextView>(R.id.tvName2).setTextColor(Color.parseColor("#DADADA"))
        root.findViewById<TextView>(R.id.tvName3).setTextColor(Color.parseColor("#DADADA"))
        root.findViewById<TextView>(R.id.tvYourName).setTextColor(Color.parseColor("#E0E0E0"))

        root.findViewById<TextView>(R.id.tvReactionChip).setBackgroundColor(Color.parseColor("#2D2D2D"))
        root.findViewById<TextView>(R.id.tvSequenceChip).setBackgroundColor(Color.parseColor("#2D2D2D"))
        root.findViewById<TextView>(R.id.tvVerbalChip).setBackgroundColor(Color.parseColor("#2D2D2D"))
        root.findViewById<TextView>(R.id.tvNumberChip).setBackgroundColor(Color.parseColor("#2D2D2D"))
        root.findViewById<TextView>(R.id.tvVisualChip).setBackgroundColor(Color.parseColor("#2D2D2D"))
        root.findViewById<TextView>(R.id.tvChimpChip).setBackgroundColor(Color.parseColor("#2D2D2D"))
    }

    private fun updateRankingsForGame(game: String) {
        gameType = game
        PrefsManager.saveSelectedGame(requireContext(), game)
        tvTitle.text = "$game Rankings"
        applyChipStyles(selectedGame = game)

        fetchLeaderboardFromApi(game)
    }

    private fun fetchLeaderboardFromApi(game: String) {
        setLoading(true)
        val skip = when (game) {
            "Reaction Time" -> 0
            "Sequence Memory" -> 3
            "Verbal Memory" -> 6
            "Number Memory" -> 9
            "Visual Memory" -> 12
            else -> 15
        }

        val request = Request.Builder()
            .url("https://dummyjson.com/users?limit=3&skip=$skip")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiIfAttached {
                    setLoading(false)
                    tvApiStatus.text = "Offline mode: API unavailable, showing cached demo data"
                    applyRankingData(game, defaultNamesForGame(game))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnUiIfAttached {
                        setLoading(false)
                        tvApiStatus.text = "API error ${response.code}, showing fallback data"
                        applyRankingData(game, defaultNamesForGame(game))
                    }
                    response.close()
                    return
                }

                val body = response.body?.string()
                response.close()

                if (body.isNullOrBlank()) {
                    runOnUiIfAttached {
                        setLoading(false)
                        tvApiStatus.text = "Empty API response, showing fallback data"
                        applyRankingData(game, defaultNamesForGame(game))
                    }
                    return
                }

                try {
                    val json = JSONObject(body)
                    val users = json.getJSONArray("users")

                    val names = mutableListOf<String>()
                    for (index in 0 until users.length()) {
                        val user = users.getJSONObject(index)
                        val first = user.optString("firstName", "Player")
                        val last = user.optString("lastName", "")
                        names.add("$first $last".trim())
                    }

                    while (names.size < 3) {
                        names.add("Player ${names.size + 1}")
                    }

                    runOnUiIfAttached {
                        setLoading(false)
                        tvApiStatus.text = "Live data source: dummyjson.com/users"
                        applyRankingData(game, names)
                    }
                } catch (_: Exception) {
                    runOnUiIfAttached {
                        setLoading(false)
                        tvApiStatus.text = "Invalid API response, showing fallback data"
                        applyRankingData(game, defaultNamesForGame(game))
                    }
                }
            }
        })
    }

    private fun applyRankingData(game: String, names: List<String>) {
        tvName1.text = names[0]
        tvName2.text = names[1]
        tvName3.text = names[2]

        when (game) {
            "Reaction Time" -> {
                tvScore1.text = "146 ms avg"
                tvScore2.text = "153 ms avg"
                tvScore3.text = "161 ms avg"

                val bestAvg = PrefsManager.getReactionTimeBestAvgMs(requireContext())
                tvYourScore.text = if (bestAvg == 0) "--" else "$bestAvg ms avg"
            }

            "Sequence Memory" -> {
                tvScore1.text = "Level 47"
                tvScore2.text = "Level 44"
                tvScore3.text = "Level 42"
                val bestLevel = PrefsManager.getSequenceMemoryBestLevel(requireContext())
                tvYourScore.text = if (bestLevel == 0) "--" else "Level $bestLevel"
            }

            "Verbal Memory" -> {
                tvScore1.text = "82 Words"
                tvScore2.text = "77 Words"
                tvScore3.text = "73 Words"
                val bestWords = PrefsManager.getVerbalMemoryBestScore(requireContext())
                tvYourScore.text = if (bestWords == 0) "--" else "$bestWords Words"
            }

            "Number Memory" -> {
                tvScore1.text = "17 Digits"
                tvScore2.text = "15 Digits"
                tvScore3.text = "14 Digits"
                val bestLevel = PrefsManager.getNumberMemoryBestLevel(requireContext())
                tvYourScore.text = if (bestLevel == 0) "--" else "Level $bestLevel"
            }

            "Visual Memory" -> {
                tvScore1.text = "Level 16"
                tvScore2.text = "Level 15"
                tvScore3.text = "Level 13"
                val bestLevel = PrefsManager.getVisualMemoryBestLevel(requireContext())
                tvYourScore.text = if (bestLevel == 0) "--" else "Level $bestLevel"
            }

            "Chimp Test" -> {
                tvScore1.text = "Score 26"
                tvScore2.text = "Score 24"
                tvScore3.text = "Score 23"
                tvYourScore.text = "Score 18"
            }
        }

        Toast.makeText(requireContext(), "Showing $game rankings", Toast.LENGTH_SHORT).show()
    }

    private fun defaultNamesForGame(game: String): List<String> {
        return when (game) {
            "Reaction Time" -> listOf("Alex Johnson", "Sarah Miller", "Chen Wei")
            "Sequence Memory" -> listOf("Neha Patel", "Luis Gomez", "Rina Kato")
            "Verbal Memory" -> listOf("Liam Carter", "Nora Khan", "Eva Rossi")
            "Number Memory" -> listOf("Ishan Mehta", "Sofia Lin", "Noah Reed")
            "Visual Memory" -> listOf("Mila Brown", "Ravi Das", "Yuna Park")
            else -> listOf("Ibrahim Noor", "Mia Chen", "Aarav Singh")
        }
    }

    private fun setLoading(isLoading: Boolean) {
        pbLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun runOnUiIfAttached(action: () -> Unit) {
        activity?.runOnUiThread {
            if (isAdded && view != null) {
                action()
            }
        }
    }

    private fun applyChipStyles(selectedGame: String) {
        val isDark = PrefsManager.isDarkModeEnabled(requireContext())
        val chips = listOf(tvReactionChip, tvSequenceChip, tvVerbalChip, tvNumberChip, tvVisualChip, tvChimpChip)
        chips.forEach { chip ->
            val baseBg = if (isDark) "#2D2D2D" else "#E0E0E0"
            val baseText = if (isDark) "#D6D6D6" else "#424242"
            chip.setBackgroundColor(android.graphics.Color.parseColor(baseBg))
            chip.setTextColor(android.graphics.Color.parseColor(baseText))
            chip.textSize = 14f
            chip.scaleX = 1f
            chip.scaleY = 1f
            chip.alpha = 0.95f
        }

        val selectedChip = when (selectedGame) {
            "Reaction Time" -> tvReactionChip
            "Sequence Memory" -> tvSequenceChip
            "Verbal Memory" -> tvVerbalChip
            "Number Memory" -> tvNumberChip
            "Visual Memory" -> tvVisualChip
            else -> tvChimpChip
        }

        selectedChip.setBackgroundColor(android.graphics.Color.parseColor("#6200EE"))
        selectedChip.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
        selectedChip.textSize = 15f
        selectedChip.scaleX = 1.08f
        selectedChip.scaleY = 1.08f
        selectedChip.alpha = 1f
    }

    // Lifecycle methods
    override fun onStart() {
        super.onStart()
        println("GameRankingsFragment: onStart")
    }

    override fun onResume() {
        super.onResume()
        println("GameRankingsFragment: onResume")
    }

    override fun onPause() {
        super.onPause()
        println("GameRankingsFragment: onPause")
    }

    override fun onStop() {
        super.onStop()
        println("GameRankingsFragment: onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        println("GameRankingsFragment: onDestroy")
    }
}