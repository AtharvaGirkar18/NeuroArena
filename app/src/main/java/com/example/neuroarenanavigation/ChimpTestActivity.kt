package com.example.neuroarenanavigation

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import com.example.neuroarenanavigation.data.local.NeuroArenaRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class ChimpTestActivity : AppCompatActivity() {

    private enum class RoundState {
        SHOWING_NUMBERS,
        MEMORY_PHASE,
        PAUSED,
        GAME_OVER
    }

    private lateinit var board: FrameLayout
    private lateinit var rotatePromptPanel: View
    private lateinit var statusPanel: View
    private lateinit var gameOverPanel: View
    private lateinit var tvNumbers: TextView
    private lateinit var tvStrikes: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvBest: TextView
    private lateinit var btnContinue: Button
    private lateinit var btnTryAgain: Button

    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gameplayUnlocked = false

    private var level = 4
    private var strikes = 0
    private var expectedNumber = 1
    private var currentTiles = mutableMapOf<Int, AppCompatButton>()
    private var state: RoundState = RoundState.SHOWING_NUMBERS

    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (gameplayUnlocked) return
            if (isLandscapeFromAccelerometer(event.values[0], event.values[1])) {
                unlockGameplayInLandscape()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chimp_test)

        supportActionBar?.hide()
        window.statusBarColor = Color.parseColor("#2D89C8")

        board = findViewById(R.id.chimpBoard)
        rotatePromptPanel = findViewById(R.id.chimpRotatePromptPanel)
        statusPanel = findViewById(R.id.chimpStatusPanel)
        gameOverPanel = findViewById(R.id.chimpGameOverPanel)
        tvNumbers = findViewById(R.id.tvChimpNumbers)
        tvStrikes = findViewById(R.id.tvChimpStrikes)
        tvScore = findViewById(R.id.tvChimpScore)
        tvBest = findViewById(R.id.tvChimpBest)
        btnContinue = findViewById(R.id.btnChimpContinue)
        btnTryAgain = findViewById(R.id.btnChimpTryAgain)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        btnContinue.setOnClickListener {
            statusPanel.visibility = View.GONE
            startRound()
        }

        btnTryAgain.setOnClickListener {
            resetGame()
            startRound()
        }

        showRotatePrompt()

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            unlockGameplayInLandscape()
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(accelListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        if (!gameplayUnlocked && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            unlockGameplayInLandscape()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(accelListener)
        uiHandler.removeCallbacksAndMessages(null)
        syncCurrentUserSnapshotToDb()
    }

    private fun showRotatePrompt() {
        rotatePromptPanel.visibility = View.VISIBLE
        board.visibility = View.GONE
        statusPanel.visibility = View.GONE
        gameOverPanel.visibility = View.GONE
    }

    private fun unlockGameplayInLandscape() {
        if (gameplayUnlocked) return
        gameplayUnlocked = true

        // Rotate and lock UI to landscape once accelerometer confirms orientation.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        rotatePromptPanel.visibility = View.GONE
        board.visibility = View.VISIBLE

        board.post {
            startRound()
        }
    }

    private fun isLandscapeFromAccelerometer(x: Float, y: Float): Boolean {
        // In landscape, gravity magnitude on X axis dominates Y axis.
        return abs(x) > abs(y) + 1.5f && abs(x) > 6.5f
    }

    private fun startRound() {
        state = RoundState.SHOWING_NUMBERS
        expectedNumber = 1
        gameOverPanel.visibility = View.GONE
        statusPanel.visibility = View.GONE
        drawTiles(level)
    }

    private fun drawTiles(tileCount: Int) {
        board.removeAllViews()
        currentTiles.clear()

        val tileSize = dp(82)
        val edgePadding = dp(28)
        val minGap = dp(18)

        val boardWidth = board.width
        val boardHeight = board.height
        val maxX = max(edgePadding, boardWidth - edgePadding - tileSize)
        val maxY = max(edgePadding, boardHeight - edgePadding - tileSize)

        val occupied = mutableListOf<Rect>()

        fun generatePosition(): Pair<Int, Int> {
            repeat(400) {
                val x = Random.nextInt(edgePadding, maxX + 1)
                val y = Random.nextInt(edgePadding, maxY + 1)
                val rect = Rect(x, y, x + tileSize, y + tileSize)
                val overlaps = occupied.any { existing: Rect ->
                    Rect(
                        existing.left - minGap,
                        existing.top - minGap,
                        existing.right + minGap,
                        existing.bottom + minGap
                    ).intersect(rect)
                }
                if (!overlaps) {
                    occupied.add(rect)
                    return x to y
                }
            }

            // Fallback for crowded levels: evenly spread by simple packing.
            val index = occupied.size
            val cols = max(1, (boardWidth - 2 * edgePadding) / (tileSize + minGap))
            val row = index / cols
            val col = index % cols
            val packedX = edgePadding + col * (tileSize + minGap)
            val packedY = edgePadding + row * (tileSize + minGap)
            val x = min(maxX, packedX)
            val y = min(maxY, packedY)
            occupied.add(Rect(x, y, x + tileSize, y + tileSize))
            return x to y
        }

        for (number in 1..tileCount) {
            val (left, top) = generatePosition()

            val button = AppCompatButton(this).apply {
                text = number.toString()
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                stateListAnimator = null
                setBackgroundResource(R.drawable.bg_chimp_tile_numbered)
            }

            val lp = FrameLayout.LayoutParams(tileSize, tileSize)
            lp.leftMargin = left
            lp.topMargin = top
            board.addView(button, lp)

            currentTiles[number] = button

            button.setOnClickListener {
                onTileTapped(number)
            }
        }
    }

    private fun onTileTapped(number: Int) {
        if (state == RoundState.PAUSED || state == RoundState.GAME_OVER) return

        if (state == RoundState.SHOWING_NUMBERS) {
            state = RoundState.MEMORY_PHASE
            hideAllNumbers()
        }

        if (number != expectedNumber) {
            handleStrike()
            return
        }

        currentTiles[number]?.visibility = View.INVISIBLE
        expectedNumber++

        if (expectedNumber > level) {
            level++
            showRoundPassedPanel()
        }
    }

    private fun hideAllNumbers() {
        currentTiles.values.forEach { button ->
            button.text = ""
            button.setBackgroundResource(R.drawable.bg_chimp_tile)
        }
    }

    private fun handleStrike() {
        strikes++
        if (strikes >= 3) {
            showGameOver()
            return
        }

        showStrikePanel()
    }

    private fun showRoundPassedPanel() {
        state = RoundState.PAUSED
        board.removeAllViews()
        tvNumbers.text = level.toString()
        tvStrikes.text = "$strikes of 3"
        statusPanel.visibility = View.VISIBLE
    }

    private fun showStrikePanel() {
        state = RoundState.PAUSED
        board.removeAllViews()
        tvNumbers.text = level.toString()
        tvStrikes.text = "$strikes of 3"
        statusPanel.visibility = View.VISIBLE
    }

    private fun showGameOver() {
        state = RoundState.GAME_OVER
        board.removeAllViews()

        val score = level - 1
        tvScore.text = score.toString()

        val bestScore = PrefsManager.getChimpBestScore(this)
        val updatedBest = max(bestScore, score)
        if (updatedBest > bestScore) {
            PrefsManager.saveChimpBestScore(this, updatedBest)
        }

        PrefsManager.onGameSessionCompleted(this, "Chimp Test")
        syncCurrentUserSnapshotToDb()

        tvBest.text = "Personal Best: ${max(bestScore, score)}"
        gameOverPanel.visibility = View.VISIBLE
    }

    private fun resetGame() {
        uiHandler.removeCallbacksAndMessages(null)
        level = 4
        strikes = 0
        expectedNumber = 1
        state = RoundState.SHOWING_NUMBERS
        board.removeAllViews()
        statusPanel.visibility = View.GONE
        gameOverPanel.visibility = View.GONE
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
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
