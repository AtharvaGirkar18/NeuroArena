package com.example.neuroarenanavigation

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.neuroarenanavigation.data.local.NeuroArenaRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.math.roundToInt

class ReactionTimeActivity : AppCompatActivity() {

    private val totalAttemptsPerSet = 5

    private enum class GameState {
        READY,
        WAITING,
        CAN_TAP,
        RESULT
    }

    private lateinit var root: View
    private lateinit var tvIcon: TextView
    private lateinit var tvGameLabel: TextView
    private lateinit var tvPrimary: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvPersonalBest: TextView
    private lateinit var tvRound: TextView
    private lateinit var tvFooter: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var state: GameState = GameState.READY
    private var greenStartTimestamp: Long = 0L
    private val attempts = mutableListOf<Int>()
    private var hasShownFinalAverage: Boolean = false
    private var lastAverageMs: Int = 0
    private var lastBestAvgMs: Int = 0
    private var isNewBest: Boolean = false
    private val startRunnable = Runnable {
        state = GameState.CAN_TAP
        greenStartTimestamp = SystemClock.elapsedRealtime()
        renderWaitingForTap()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reaction_time)

        supportActionBar?.title = "Reaction Time"

        root = findViewById(R.id.reactionGameRoot)
        tvIcon = findViewById(R.id.tvReactionIcon)
        tvGameLabel = findViewById(R.id.tvReactionGameLabel)
        tvPrimary = findViewById(R.id.tvReactionPrimary)
        tvHint = findViewById(R.id.tvReactionHint)
        tvPersonalBest = findViewById(R.id.tvReactionPersonalBest)
        tvRound = findViewById(R.id.tvReactionRound)
        tvFooter = findViewById(R.id.tvReactionFooter)

        renderReady()

        root.setOnClickListener {
            when (state) {
                GameState.READY -> {
                    beginRound()
                }
                GameState.RESULT -> {
                    if (attempts.size < totalAttemptsPerSet) {
                        // Rounds 1-4: continue to next round.
                        beginRound()
                    } else if (!hasShownFinalAverage) {
                        // After round 5: second click reveals final average + best.
                        hasShownFinalAverage = true
                        renderFinalResult(lastAverageMs, lastBestAvgMs, isNewBest)
                    } else {
                        // Third click after average: start fresh set.
                        attempts.clear()
                        hasShownFinalAverage = false
                        beginRound()
                    }
                }
                GameState.WAITING -> handleTooSoonTap()
                GameState.CAN_TAP -> finishRound()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(startRunnable)
        syncCurrentUserSnapshotToDb()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(startRunnable)
    }

    private fun beginRound() {
        state = GameState.WAITING
        val attemptNumber = attempts.size + 1
        renderWaitingForGreen(attemptNumber)

        val randomDelayMs = Random.nextLong(1800L, 5000L)
        handler.removeCallbacks(startRunnable)
        handler.postDelayed(startRunnable, randomDelayMs)
    }

    private fun handleTooSoonTap() {
        handler.removeCallbacks(startRunnable)
        state = GameState.RESULT
        val currentRound = attempts.size + 1
        renderTooSoon(currentRound)
    }

    private fun finishRound() {
        val reactionMs = (SystemClock.elapsedRealtime() - greenStartTimestamp).toInt()
        attempts.add(reactionMs)

        // Check if this was the last round
        if (attempts.size >= totalAttemptsPerSet) {
            // Calculate average and save data for later display
            val averageMs = attempts.average().roundToInt()
            val bestAvgMs = PrefsManager.getReactionTimeBestAvgMs(this)
            val newBestAvg = if (bestAvgMs == 0 || averageMs < bestAvgMs) averageMs else bestAvgMs
            if (newBestAvg != bestAvgMs) {
                PrefsManager.saveReactionTimeBestAvgMs(this, newBestAvg)
            }

            PrefsManager.onGameSessionCompleted(this, "Reaction Time")
            syncCurrentUserSnapshotToDb()

            // Store the results to show on next click
            lastAverageMs = averageMs
            lastBestAvgMs = newBestAvg
            isNewBest = newBestAvg == averageMs

                // Show only the 5th round score now, average will show on next click
                state = GameState.RESULT
                renderFinalRoundScore(reactionMs)
                return
        }

        // For rounds 1-4, show the result and wait for next click
        state = GameState.RESULT
        renderInterimResult(reactionMs, attempts.size + 1)
    }

    private fun syncCurrentUserSnapshotToDb() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid ?: "local_guest"
        val email = currentUser?.email.orEmpty()
        val fallbackName = currentUser?.displayName
            ?: PrefsManager.getUsername(this).ifEmpty { "Player" }

        val repository = NeuroArenaRepository(this)
        lifecycleScope.launch(Dispatchers.IO) {
            repository.syncCurrentPrefsToUid(uid, email, fallbackName)
        }
    }

    private fun renderReady() {
        root.setBackgroundColor(Color.parseColor("#2D89C8"))
        tvIcon.text = "⚡"
        tvGameLabel.visibility = View.VISIBLE
        tvGameLabel.text = "Reaction Time"
        tvPrimary.text = "Tap"
        tvPrimary.textSize = 64f
        tvHint.text = "Tap to start a 5-try challenge"
        tvPersonalBest.visibility = View.GONE
        tvRound.visibility = View.VISIBLE
        tvRound.text = "Round 1/$totalAttemptsPerSet"
        tvFooter.visibility = View.VISIBLE
    }

    private fun renderWaitingForGreen(attemptNumber: Int) {
        root.setBackgroundColor(Color.parseColor("#C62828"))
        tvIcon.text = "⚡"
        tvGameLabel.visibility = View.VISIBLE
        tvGameLabel.text = "Reaction Time"
        tvPrimary.text = "Wait..."
        tvPrimary.textSize = 62f
        tvHint.text = "Tap only when it turns green"
        tvPersonalBest.visibility = View.GONE
        tvRound.visibility = View.VISIBLE
        tvRound.text = "Round $attemptNumber/$totalAttemptsPerSet"
        tvFooter.visibility = View.VISIBLE
    }

    private fun renderWaitingForTap() {
        root.setBackgroundColor(Color.parseColor("#2E7D32"))
        tvIcon.text = "⚡"
        tvGameLabel.visibility = View.VISIBLE
        tvGameLabel.text = "Reaction Time"
        tvPrimary.text = "TAP!"
        tvPrimary.textSize = 72f
        tvHint.text = "Tap as quickly as you can"
        tvPersonalBest.visibility = View.GONE
        tvRound.visibility = View.VISIBLE
        tvRound.text = "Round ${attempts.size + 1}/$totalAttemptsPerSet"
        tvFooter.visibility = View.VISIBLE
    }

    private fun renderInterimResult(reactionMs: Int, nextRound: Int) {
        root.setBackgroundColor(Color.parseColor("#2D89C8"))
        tvIcon.text = "⏰"
        tvGameLabel.visibility = View.GONE
        tvPrimary.text = "$reactionMs ms"
        tvPrimary.textSize = 66f
        tvHint.text = "Click to keep going"
        tvPersonalBest.visibility = View.GONE
        tvRound.visibility = View.VISIBLE
        tvRound.text = "Next: Round $nextRound/$totalAttemptsPerSet"
        tvFooter.visibility = View.GONE
    }

    private fun renderFinalRoundScore(reactionMs: Int) {
        root.setBackgroundColor(Color.parseColor("#2D89C8"))
        tvIcon.text = "⏰"
        tvGameLabel.visibility = View.VISIBLE
        tvGameLabel.text = "Final Round"
        tvPrimary.text = "$reactionMs ms"
        tvPrimary.textSize = 66f
        tvHint.text = "Click to see your average"
        tvPersonalBest.visibility = View.GONE
        tvRound.visibility = View.VISIBLE
        tvRound.text = "Round $totalAttemptsPerSet/$totalAttemptsPerSet"
        tvFooter.visibility = View.GONE
    }

    private fun renderTooSoon(roundToRetry: Int) {
        root.setBackgroundColor(Color.parseColor("#EF6C00"))
        tvIcon.text = "!"
        tvGameLabel.visibility = View.VISIBLE
        tvGameLabel.text = "Too Soon"
        tvPrimary.text = "Oops"
        tvPrimary.textSize = 66f
        tvHint.text = "Click to retry round $roundToRetry/$totalAttemptsPerSet"
        tvPersonalBest.visibility = View.GONE
        tvRound.visibility = View.VISIBLE
        tvRound.text = "Round $roundToRetry/$totalAttemptsPerSet"
        tvFooter.visibility = View.GONE
    }

    private fun renderFinalResult(averageMs: Int, bestAvgMs: Int, isNewBest: Boolean) {
        root.setBackgroundColor(Color.parseColor("#2D89C8"))
        tvIcon.text = "⚡"
        tvGameLabel.visibility = View.VISIBLE
        tvGameLabel.text = "Reaction Time"
        tvPrimary.text = "$averageMs ms"
        tvPrimary.textSize = 74f
        tvHint.text = if (isNewBest) "New best average! Tap to play again" else "Tap to play again"
        tvPersonalBest.visibility = View.VISIBLE
        tvPersonalBest.text = "Personal Best: $bestAvgMs ms avg"
        tvRound.visibility = View.GONE
        tvFooter.visibility = View.GONE
    }
}
