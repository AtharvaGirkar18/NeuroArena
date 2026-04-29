package com.example.neuroarenanavigation

import android.os.Bundle
import android.os.CountDownTimer
import android.graphics.drawable.GradientDrawable
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

class SequenceMemoryActivity : AppCompatActivity() {

    private lateinit var panelIntro: FrameLayout
    private lateinit var panelGame: FrameLayout
    private lateinit var panelGameOver: FrameLayout

    private lateinit var tvLevel: TextView
    private lateinit var tvSequenceLength: TextView
    private lateinit var gridTiles: GridLayout
    private lateinit var btnStart: Button
    private lateinit var btnTryAgain: Button
    private lateinit var tvGameOverLevel: TextView
    private lateinit var tvGameOverBest: TextView
    private lateinit var tvPhaseStatus: TextView

    private var level = 1
    private val gridSize = 3
    private val sequence = mutableListOf<Int>()
    private val selectedThisRound = mutableSetOf<Int>()
    private var playerInputIndex = 0
    private var highlightedIndex = -1
    private var phase = "intro" // intro, showing, playing, result, gameover

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sequence_memory)

        panelIntro = findViewById(R.id.panelIntro)
        panelGame = findViewById(R.id.panelGame)
        panelGameOver = findViewById(R.id.panelGameOver)

        tvLevel = findViewById(R.id.tvLevel)
        tvSequenceLength = findViewById(R.id.tvLives)
        gridTiles = findViewById(R.id.gridTiles)
        btnStart = findViewById(R.id.btnStart)
        btnTryAgain = findViewById(R.id.btnTryAgain)
        tvGameOverLevel = findViewById(R.id.tvGameOverLevel)
        tvGameOverBest = findViewById(R.id.tvGameOverBest)
        tvPhaseStatus = findViewById(R.id.tvPhaseStatus)

        btnStart.setOnClickListener {
            startGame()
        }
        btnTryAgain.setOnClickListener { restart() }

        renderIntro()
    }

    override fun onPause() {
        super.onPause()
        syncCurrentUserSnapshotToDb()
    }

    private fun startGame() {
        level = 1
        sequence.clear()
        sequence.add(randomTileIndex())
        playSequenceRound()
    }

    private fun playSequenceRound() {
        playerInputIndex = 0
        selectedThisRound.clear()
        phase = "showing"
        highlightedIndex = -1
        renderGame()

        var step = 0
        fun showNextStep() {
            if (step >= sequence.size) {
                highlightedIndex = -1
                phase = "playing"
                renderGame()
                return
            }

            highlightedIndex = sequence[step]
            renderGame()

            object : CountDownTimer(500, 500) {
                override fun onTick(millisUntilFinished: Long) {}
                override fun onFinish() {
                    highlightedIndex = -1
                    renderGame()

                    object : CountDownTimer(180, 180) {
                        override fun onTick(millisUntilFinished: Long) {}
                        override fun onFinish() {
                            step++
                            showNextStep()
                        }
                    }.start()
                }
            }.start()
        }

        object : CountDownTimer(350, 350) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                showNextStep()
            }
        }.start()
    }

    private fun randomTileIndex(): Int {
        return (0 until (gridSize * gridSize)).random()
    }

    private fun handleSquareClick(index: Int, tile: SequenceTile) {
        if (phase != "playing") return

        tile.pulseWhite()

        val expected = sequence[playerInputIndex]
        if (index == expected) {
            playerInputIndex++
            selectedThisRound.add(index)
            renderGame()

            if (playerInputIndex == sequence.size) {
                phase = "result"
                renderGame()

                val currentBest = PrefsManager.getSequenceMemoryBestLevel(this)
                if (level > currentBest) {
                    PrefsManager.saveSequenceMemoryBestLevel(this, level)
                }

                object : CountDownTimer(900, 900) {
                    override fun onTick(millisUntilFinished: Long) {}
                    override fun onFinish() {
                        advanceToNextLevel()
                    }
                }.start()
            }
        } else {
            phase = "gameover"
            val currentBest = PrefsManager.getSequenceMemoryBestLevel(this)
            if (level > currentBest) {
                PrefsManager.saveSequenceMemoryBestLevel(this, level)
            }
            PrefsManager.onGameSessionCompleted(this, "Sequence Memory")
            syncCurrentUserSnapshotToDb()
            renderGameOver()
        }
    }

    private fun advanceToNextLevel() {
        level++

        if (level == 10 || level == 20 || level == 30) {
            AppNotifications.notifyMilestone(
                this,
                "Sequence Memory Milestone",
                "You reached Level $level in Sequence Memory."
            )
        }

        sequence.add(randomTileIndex())
        playSequenceRound()
    }

    private fun restart() {
        startGame()
    }

    private fun generateGridTiles() {
        gridTiles.removeAllViews()
        gridTiles.columnCount = gridSize
        gridTiles.rowCount = gridSize

        val totalSquares = gridSize * gridSize

        for (i in 0 until totalSquares) {
            val tile = SequenceTile(this, i)
            tile.setOnClickListener {
                handleSquareClick(i, tile)
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
            lp.gravity = android.view.Gravity.CENTER_HORIZONTAL
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
        tvSequenceLength.text = "Length ${sequence.size}"

        tvPhaseStatus.text = when (phase) {
            "showing" -> "Memorize the pattern"
            "playing" -> "Repeat the sequence by tapping tiles in order"
            "result" -> "Correct"
            else -> ""
        }
    }

    private fun renderGameOver() {
        panelIntro.visibility = FrameLayout.GONE
        panelGame.visibility = FrameLayout.GONE
        panelGameOver.visibility = FrameLayout.VISIBLE

        tvGameOverLevel.text = "Level Reached: $level"
        tvGameOverBest.text = "Best: Level ${PrefsManager.getSequenceMemoryBestLevel(this)}"
    }

    private inner class SequenceTile(context: android.content.Context, val index: Int) :
        FrameLayout(context) {

        init {
            background = tileBackground(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
            updateDisplay()
        }

        fun updateDisplay() {
            val backgroundColor = when {
                phase == "showing" && highlightedIndex == index -> {
                    ContextCompat.getColor(context, android.R.color.white)
                }
                phase == "playing" && selectedThisRound.contains(index) -> {
                    ContextCompat.getColor(context, android.R.color.white)
                }
                phase == "result" && selectedThisRound.contains(index) -> {
                    ContextCompat.getColor(context, android.R.color.white)
                }
                else -> {
                    ContextCompat.getColor(context, android.R.color.holo_blue_dark)
                }
            }
            background = tileBackground(backgroundColor)
        }

        fun pulseWhite() {
            animate().cancel()
            background = tileBackground(ContextCompat.getColor(context, android.R.color.white))
            alpha = 0.82f
            animate()
                .alpha(1f)
                .scaleX(1.10f)
                .scaleY(1.10f)
                .setDuration(90)
                .withEndAction {
                    animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(130)
                        .withEndAction { updateDisplay() }
                        .start()
                }
                .start()
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
