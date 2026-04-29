package com.example.neuroarenanavigation

import android.graphics.Paint
import android.os.Bundle
import android.os.CountDownTimer
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.neuroarenanavigation.data.local.NeuroArenaRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class NumberMemoryActivity : AppCompatActivity() {

    private lateinit var panelIntro: View
    private lateinit var panelMemorize: View
    private lateinit var panelInput: View
    private lateinit var panelResult: View

    private lateinit var tvMemorizeNumber: TextView
    private lateinit var pbMemorizeTimer: ProgressBar
    private lateinit var etAnswer: EditText
    private lateinit var tvResultNumber: TextView
    private lateinit var tvResultAnswer: TextView
    private lateinit var tvResultLevel: TextView
    private lateinit var tvResultBest: TextView
    private lateinit var btnResultAction: Button

    private var level = 3
    private var currentNumber = ""
    private var wasLastAnswerCorrect = false
    private var memorizeTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_number_memory)

        supportActionBar?.hide()

        panelIntro = findViewById(R.id.panelIntro)
        panelMemorize = findViewById(R.id.panelMemorize)
        panelInput = findViewById(R.id.panelInput)
        panelResult = findViewById(R.id.panelResult)

        tvMemorizeNumber = findViewById(R.id.tvMemorizeNumber)
        pbMemorizeTimer = findViewById(R.id.pbMemorizeTimer)
        etAnswer = findViewById(R.id.etAnswer)
        tvResultNumber = findViewById(R.id.tvResultNumber)
        tvResultAnswer = findViewById(R.id.tvResultAnswer)
        tvResultLevel = findViewById(R.id.tvResultLevel)
        tvResultBest = findViewById(R.id.tvResultBest)
        btnResultAction = findViewById(R.id.btnResultAction)

        findViewById<Button>(R.id.btnStartNumberMemory).setOnClickListener {
            startRound()
        }

        findViewById<Button>(R.id.btnSubmitAnswer).setOnClickListener {
            submitAnswer()
        }

        etAnswer.setOnEditorActionListener { _, actionId, event ->
            val imeDone = actionId == EditorInfo.IME_ACTION_DONE
            val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (imeDone || enterPressed) {
                submitAnswer()
                true
            } else {
                false
            }
        }

        btnResultAction.setOnClickListener {
            if (wasLastAnswerCorrect) {
                level++
            } else {
                level = 3
            }
            startRound()
        }

        showIntro()
    }

    override fun onPause() {
        super.onPause()
        memorizeTimer?.cancel()
        syncCurrentUserSnapshotToDb()
    }

    private fun showIntro() {
        showPanel(panelIntro)
    }

    private fun startRound() {
        currentNumber = generateNumber(level)
        tvMemorizeNumber.text = currentNumber
        etAnswer.setText("")
        runMemorizePhase(level)
    }

    private fun runMemorizePhase(digits: Int) {
        showPanel(panelMemorize)

        val totalMs = memorizeDurationMs(digits)
        pbMemorizeTimer.max = totalMs.toInt()
        pbMemorizeTimer.progress = totalMs.toInt()

        memorizeTimer?.cancel()
        memorizeTimer = object : CountDownTimer(totalMs, 25L) {
            override fun onTick(millisUntilFinished: Long) {
                pbMemorizeTimer.progress = millisUntilFinished.toInt().coerceAtLeast(0)
            }

            override fun onFinish() {
                pbMemorizeTimer.progress = 0
                showInputPhase()
            }
        }.start()
    }

    private fun showInputPhase() {
        showPanel(panelInput)
        etAnswer.requestFocus()
    }

    private fun submitAnswer() {
        val answer = etAnswer.text.toString().trim()
        if (answer.isEmpty()) return

        wasLastAnswerCorrect = answer == currentNumber
        showResult(answer)
    }

    private fun showResult(answer: String) {
        showPanel(panelResult)

        tvResultNumber.text = currentNumber
        tvResultAnswer.text = answer
        tvResultAnswer.paintFlags = if (wasLastAnswerCorrect) {
            tvResultAnswer.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        } else {
            tvResultAnswer.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }

        tvResultLevel.text = "Level $level"

        val best = PrefsManager.getNumberMemoryBestLevel(this)
        val updatedBest = if (wasLastAnswerCorrect) maxOf(best, level) else best
        if (updatedBest != best) {
            PrefsManager.saveNumberMemoryBestLevel(this, updatedBest)
        }

        if (!wasLastAnswerCorrect) {
            PrefsManager.onGameSessionCompleted(this, "Number Memory")
            syncCurrentUserSnapshotToDb()
        }
        tvResultBest.text = if (updatedBest > 0) "Best: Level $updatedBest" else "Best: Level --"

        btnResultAction.text = if (wasLastAnswerCorrect) "Next" else "Try again"
    }

    private fun memorizeDurationMs(digits: Int): Long {
        // Give more memorize time for longer sequences, similar to benchmark behavior.
        return (1400L + digits * 350L).coerceAtMost(7000L)
    }

    private fun generateNumber(digits: Int): String {
        if (digits <= 1) return Random.nextInt(0, 10).toString()

        val first = Random.nextInt(1, 10)
        val rest = buildString {
            repeat(digits - 1) {
                append(Random.nextInt(0, 10))
            }
        }
        return "$first$rest"
    }

    private fun showPanel(panel: View) {
        panelIntro.visibility = if (panel == panelIntro) View.VISIBLE else View.GONE
        panelMemorize.visibility = if (panel == panelMemorize) View.VISIBLE else View.GONE
        panelInput.visibility = if (panel == panelInput) View.VISIBLE else View.GONE
        panelResult.visibility = if (panel == panelResult) View.VISIBLE else View.GONE
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
