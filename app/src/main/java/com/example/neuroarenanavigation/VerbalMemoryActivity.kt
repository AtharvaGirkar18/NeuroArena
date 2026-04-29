package com.example.neuroarenanavigation

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.neuroarenanavigation.data.local.NeuroArenaRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class VerbalMemoryActivity : AppCompatActivity() {

    private lateinit var panelIntro: View
    private lateinit var panelGame: View
    private lateinit var panelGameOver: View
    private lateinit var tvStats: TextView
    private lateinit var tvWord: TextView
    private lateinit var tvFinalScore: TextView
    private lateinit var tvBest: TextView

    private val wordPool = listOf(
        "rhapsodically", "expatiate", "candor", "myriad", "harbor", "velocity",
        "fluent", "orchard", "luminous", "serene", "archive", "magnet",
        "spectrum", "cobalt", "journey", "zephyr", "crimson", "vivid",
        "diligent", "ember", "kinetic", "novel", "echo", "paradox",
        "insight", "quartz", "fable", "cascade", "horizon", "nectar",
        "oasis", "pioneer", "ripple", "summit", "thrive", "utopia"
    )

    private var lives = 3
    private var score = 0
    private var currentWord = ""
    private val seenWords = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verbal_memory)

        supportActionBar?.hide()

        panelIntro = findViewById(R.id.panelVerbalIntro)
        panelGame = findViewById(R.id.panelVerbalGame)
        panelGameOver = findViewById(R.id.panelVerbalGameOver)
        tvStats = findViewById(R.id.tvVerbalStats)
        tvWord = findViewById(R.id.tvVerbalWord)
        tvFinalScore = findViewById(R.id.tvVerbalFinalScore)
        tvBest = findViewById(R.id.tvVerbalBest)

        findViewById<Button>(R.id.btnStartVerbal).setOnClickListener {
            startGame()
        }

        findViewById<Button>(R.id.btnSeen).setOnClickListener {
            handleAnswer(answerSeen = true)
        }

        findViewById<Button>(R.id.btnNew).setOnClickListener {
            handleAnswer(answerSeen = false)
        }

        findViewById<Button>(R.id.btnVerbalTryAgain).setOnClickListener {
            startGame()
        }

        showPanel(panelIntro)
    }

    override fun onPause() {
        super.onPause()
        syncCurrentUserSnapshotToDb()
    }

    private fun startGame() {
        lives = 3
        score = 0
        seenWords.clear()
        showPanel(panelGame)
        showNextWord()
    }

    private fun showNextWord() {
        val shouldRepeat = seenWords.isNotEmpty() && Random.nextFloat() < repeatProbability()
        currentWord = if (shouldRepeat) {
            seenWords.random()
        } else {
            wordPool.random()
        }

        tvWord.text = currentWord
        updateStats()
    }

    private fun handleAnswer(answerSeen: Boolean) {
        val actuallySeen = seenWords.contains(currentWord)
        val correct = answerSeen == actuallySeen

        if (correct) {
            score++
        } else {
            lives--
            if (lives <= 0) {
                showGameOver()
                return
            }
        }

        seenWords.add(currentWord)
        showNextWord()
    }

    private fun showGameOver() {
        val best = PrefsManager.getVerbalMemoryBestScore(this)
        val updatedBest = maxOf(best, score)
        if (updatedBest != best) {
            PrefsManager.saveVerbalMemoryBestScore(this, updatedBest)
        }

        PrefsManager.onGameSessionCompleted(this, "Verbal Memory")
        syncCurrentUserSnapshotToDb()

        tvFinalScore.text = "Score: $score"
        tvBest.text = "Best: $updatedBest words"
        showPanel(panelGameOver)
    }

    private fun updateStats() {
        tvStats.text = "Lives | $lives     Score | $score"
    }

    private fun repeatProbability(): Float {
        // As score grows, repeated words appear slightly more often.
        return (0.30f + score * 0.015f).coerceAtMost(0.65f)
    }

    private fun showPanel(panel: View) {
        panelIntro.visibility = if (panel == panelIntro) View.VISIBLE else View.GONE
        panelGame.visibility = if (panel == panelGame) View.VISIBLE else View.GONE
        panelGameOver.visibility = if (panel == panelGameOver) View.VISIBLE else View.GONE
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
}
