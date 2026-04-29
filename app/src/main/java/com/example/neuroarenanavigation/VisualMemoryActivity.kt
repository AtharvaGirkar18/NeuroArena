package com.example.neuroarenanavigation

import android.os.Bundle
import android.os.CountDownTimer
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.neuroarenanavigation.data.local.NeuroArenaRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VisualMemoryActivity : AppCompatActivity() {

    private lateinit var panelIntro: FrameLayout
    private lateinit var panelGame: FrameLayout
    private lateinit var panelGameOver: FrameLayout

    private lateinit var tvLevel: TextView
    private lateinit var tvLives: TextView
    private lateinit var gridTiles: GridLayout
    private lateinit var btnStart: Button
    private lateinit var btnTryAgain: Button
    private lateinit var tvGameOverLevel: TextView
    private lateinit var tvGameOverBest: TextView
    private lateinit var tvPhaseStatus: TextView
    private lateinit var btnSubmit: Button

    private var level = 1
    private var lives = 3
    private var gridSize = 3
    private var targetSquares = setOf<Int>()
    private var selectedSquares = mutableSetOf<Int>()
    private var phase = "intro" // intro, showing, playing, result, gameover

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_visual_memory)

        panelIntro = findViewById(R.id.panelIntro)
        panelGame = findViewById(R.id.panelGame)
        panelGameOver = findViewById(R.id.panelGameOver)

        tvLevel = findViewById(R.id.tvLevel)
        tvLives = findViewById(R.id.tvLives)
        gridTiles = findViewById(R.id.gridTiles)
        btnStart = findViewById(R.id.btnStart)
        btnTryAgain = findViewById(R.id.btnTryAgain)
        tvGameOverLevel = findViewById(R.id.tvGameOverLevel)
        tvGameOverBest = findViewById(R.id.tvGameOverBest)
        tvPhaseStatus = findViewById(R.id.tvPhaseStatus)
        btnSubmit = findViewById(R.id.btnSubmit)

        btnStart.setOnClickListener {
            startNewLevel()
        }
        btnTryAgain.setOnClickListener { restart() }
        btnSubmit.setOnClickListener { handleSubmit() }

        renderIntro()
    }

    override fun onPause() {
        super.onPause()
        syncCurrentUserSnapshotToDb()
    }

    private fun startNewLevel() {
        selectedSquares.clear()
        generateTargetSquares()
        phase = "showing"
        renderGame()

        // Show target squares for 1 second.
        object : CountDownTimer(1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                if (phase == "showing") {
                    phase = "playing"
                    renderGame()
                }
            }
        }.start()
    }

    private fun generateTargetSquares() {
        val totalSquares = gridSize * gridSize
        // Match Human Benchmark-style scaling from provided levels:
        // L1=3, L2=4, L3=5, ... (increase by 1 every level).
        val highlightCount = minOf(level + 2, totalSquares)
        targetSquares = (0 until totalSquares).shuffled().take(highlightCount).toSet()
    }

    private fun handleSquareClick(index: Int) {
        if (phase != "playing") return

        if (selectedSquares.contains(index)) {
            selectedSquares.remove(index)
        } else {
            selectedSquares.add(index)
        }

        renderGame()
    }

    private fun handleSubmit() {
        if (phase != "playing") return

        phase = "result"
        val isCorrect = selectedSquares == targetSquares

        if (isCorrect) {
            renderGame()

            object : CountDownTimer(900, 900) {
                override fun onTick(millisUntilFinished: Long) {}
                override fun onFinish() {
                    advanceToNextLevel()
                }
            }.start()
        } else {
            lives--
            if (lives <= 0) {
                val currentBest = PrefsManager.getVisualMemoryBestLevel(this@VisualMemoryActivity)
                if (level > currentBest) {
                    PrefsManager.saveVisualMemoryBestLevel(this@VisualMemoryActivity, level)
                }
                PrefsManager.onGameSessionCompleted(this@VisualMemoryActivity, "Visual Memory")
                syncCurrentUserSnapshotToDb()
                phase = "gameover"
                renderGameOver()
            } else {
                object : CountDownTimer(900, 900) {
                    override fun onTick(millisUntilFinished: Long) {}
                    override fun onFinish() {
                        startNewLevel()
                    }
                }.start()
            }
        }
    }

    private fun advanceToNextLevel() {
        val currentBest = PrefsManager.getVisualMemoryBestLevel(this)
        if (level > currentBest) {
            PrefsManager.saveVisualMemoryBestLevel(this, level)
        }

        level++

        if (level == 10 || level == 20 || level == 30) {
            AppNotifications.notifyMilestone(
                this,
                "Visual Memory Milestone",
                "You reached Level $level in Visual Memory."
            )
        }

        // Increase grid size every 3 levels until 6x6.
        if (level % 3 == 0 && gridSize < 6) {
            gridSize++
        }

        startNewLevel()
    }

    private fun restart() {
        level = 1
        lives = 3
        gridSize = 3
        selectedSquares.clear()
        phase = "intro"
        renderIntro()
    }

    private fun generateGridTiles() {
        gridTiles.removeAllViews()
        gridTiles.columnCount = gridSize
        gridTiles.rowCount = gridSize

        val totalSquares = gridSize * gridSize

        for (i in 0 until totalSquares) {
            val tile = VisualTile(this, i)
            tile.setOnClickListener {
                handleSquareClick(i)
            }
            gridTiles.addView(
                tile,
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = 0
                    columnSpec = GridLayout.spec(i % gridSize, 1f)
                    rowSpec = GridLayout.spec(i / gridSize, 1f)
                    setMargins(8, 8, 8, 8)
                }
            )
        }
    }

    private fun updateGridBoardSize() {
        val side = resources.displayMetrics.widthPixels - dpToPx(40)
        val params = gridTiles.layoutParams
        if (params.width != side || params.height != side) {
            params.width = side
            params.height = side
            gridTiles.layoutParams = params
        }

        val lp = gridTiles.layoutParams
        if (lp is LinearLayout.LayoutParams) {
            lp.gravity = Gravity.CENTER_HORIZONTAL
            gridTiles.layoutParams = lp
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun tileBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14f * resources.displayMetrics.density
            setColor(color)
        }
    }

    private fun renderIntro() {
        panelIntro.visibility = FrameLayout.VISIBLE
        panelGame.visibility = FrameLayout.GONE
        panelGameOver.visibility = FrameLayout.GONE
    }

    private fun renderGame() {
        panelIntro.visibility = FrameLayout.GONE
        panelGame.visibility = FrameLayout.VISIBLE
        panelGameOver.visibility = FrameLayout.GONE

        updateGridBoardSize()
        generateGridTiles()

        tvLevel.text = "Level $level"
        tvLives.text = "Lives $lives"

        tvPhaseStatus.text = when (phase) {
            "showing" -> "Memorize the highlighted squares"
            "playing" -> "Select all previously highlighted squares"
            "result" -> "Correct"
            else -> ""
        }

        btnSubmit.visibility = if (phase == "playing") FrameLayout.VISIBLE else FrameLayout.GONE
        btnSubmit.isEnabled = selectedSquares.isNotEmpty()
    }

    private fun renderGameOver() {
        panelIntro.visibility = FrameLayout.GONE
        panelGame.visibility = FrameLayout.GONE
        panelGameOver.visibility = FrameLayout.VISIBLE

        tvGameOverLevel.text = "Level Reached: $level"
        tvGameOverBest.text = "Best: Level ${PrefsManager.getVisualMemoryBestLevel(this)}"
    }

    private inner class VisualTile(context: android.content.Context, val index: Int) :
        FrameLayout(context) {

        init {
            background = tileBackground(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
            updateDisplay()
        }

        fun updateDisplay() {
            val backgroundColor = when {
                phase == "showing" && targetSquares.contains(index) -> {
                    ContextCompat.getColor(context, android.R.color.white)
                }
                phase == "result" && targetSquares.contains(index) -> {
                    ContextCompat.getColor(context, android.R.color.white)
                }
                selectedSquares.contains(index) -> {
                    ContextCompat.getColor(context, android.R.color.white)
                }
                else -> {
                    ContextCompat.getColor(context, android.R.color.holo_blue_dark)
                }
            }
            background = tileBackground(backgroundColor)
        }
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
