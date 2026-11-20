package com.mediahub.rebuilt.arcade

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.max
import kotlin.random.Random

/** Offline mini-game collection. All game state is local to this activity. */
class MediaHubArcadeActivity : AppCompatActivity() {
    private val bg = Color.rgb(8, 8, 13)
    private val panel = Color.rgb(23, 23, 31)
    private val accent = Color.rgb(255, 77, 141)
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHub()
    }

    private fun base(title: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(28), dp(18), dp(24))
            setBackgroundColor(bg)
        }
        val scroll = android.widget.ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        root.addView(TextView(this).apply {
            text = "‹   $title"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(18))
            setOnClickListener { if (title != "MEDIAHUB ARCADE") showHub() else finish() }
        })
    }

    private fun showHub() {
        base("MEDIAHUB ARCADE")
        root.addView(TextView(this).apply {
            text = "Play anywhere. No internet required."
            textSize = 15f
            setTextColor(0xFFB9B6C3.toInt())
            setPadding(0, 0, 0, dp(22))
        })
        gameCard("2048", "Merge matching tiles. Reach 2048 and beat your best score.") { start2048() }
        gameCard("Memory Match", "Find all matching pairs using the fewest moves.") { startMemory() }
        root.addView(TextView(this).apply {
            text = "Your scores stay on this device. Games work offline."
            textSize = 12f
            setTextColor(0xFF9693A1.toInt())
            setPadding(0, dp(22), 0, 0)
        })
    }

    private fun gameCard(name: String, description: String, action: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(panel, 0x33FFFFFF)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        card.addView(TextView(this).apply {
            text = name; textSize = 20f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        })
        card.addView(TextView(this).apply {
            text = description; textSize = 13f; setTextColor(0xFFB9B6C3.toInt())
            setPadding(0, dp(6), 0, dp(12))
        })
        card.addView(TextView(this).apply {
            text = "PLAY  →"; textSize = 12f; setTextColor(accent); typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun start2048() {
        base("2048")
        val prefs = getSharedPreferences("arcade_scores", MODE_PRIVATE)
        val board = Array(4) { IntArray(4) }
        var score = 0
        val scoreText = TextView(this).apply { textSize = 16f; setTextColor(Color.WHITE) }
        val grid = GridLayout(this).apply { columnCount = 4; rowCount = 4 }
        val tiles = Array(4) { r -> Array(4) { c ->
            Button(this).apply {
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                isAllCaps = false
                setPadding(0, 0, 0, 0)
                grid.addView(this, GridLayout.LayoutParams().apply {
                    width = dp(76); height = dp(76)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
        } }
        fun spawn(): Boolean {
            val empty = mutableListOf<Pair<Int, Int>>()
            for (r in 0..3) for (c in 0..3) if (board[r][c] == 0) empty.add(r to c)
            if (empty.isEmpty()) return false
            val (r, c) = empty.random()
            board[r][c] = if (Random.nextInt(10) == 0) 4 else 2
            return true
        }
        fun render() {
            scoreText.text = "Score  $score      Best  ${prefs.getInt("best2048", 0)}"
            for (r in 0..3) for (c in 0..3) {
                val v = board[r][c]
                tiles[r][c].text = if (v == 0) "" else v.toString()
                val color = when (v) {
                    0 -> 0xFF252530.toInt(); 2 -> 0xFFECE8E1.toInt(); 4 -> 0xFFE7D9C7.toInt()
                    8 -> 0xFFFFB36B.toInt(); 16 -> 0xFFFF9560.toInt(); 32 -> 0xFFFF786F.toInt()
                    64 -> 0xFFFF536F.toInt(); 128 -> 0xFFFFD86B.toInt(); 256 -> 0xFFFFCF4D.toInt()
                    512 -> 0xFFFFC52E.toInt(); else -> accent
                }
                tiles[r][c].background = rounded(color, 0)
                tiles[r][c].setTextColor(if (v in 2..4) Color.DKGRAY else Color.WHITE)
            }
        }
        fun canMove(): Boolean {
            for (r in 0..3) for (c in 0..3) {
                if (board[r][c] == 0) return true
                if (c < 3 && board[r][c] == board[r][c + 1]) return true
                if (r < 3 && board[r][c] == board[r + 1][c]) return true
            }
            return false
        }
        fun move(direction: Int) {
            var changed = false
            for (line in 0..3) {
                val coords = (0..3).map { i -> when (direction) {
                    0 -> line to i; 1 -> line to 3 - i; 2 -> i to line; else -> 3 - i to line
                } }
                val old = coords.map { board[it.first][it.second] }
                val values = old.filter { it != 0 }.toMutableList()
                val merged = mutableListOf<Int>()
                var i = 0
                while (i < values.size) {
                    if (i + 1 < values.size && values[i] == values[i + 1]) {
                        val v = values[i] * 2; merged.add(v); score += v; i += 2
                    } else { merged.add(values[i]); i++ }
                }
                while (merged.size < 4) merged.add(0)
                coords.forEachIndexed { index, pos ->
                    if (board[pos.first][pos.second] != merged[index]) changed = true
                    board[pos.first][pos.second] = merged[index]
                }
            }
            if (changed) {
                spawn()
                val best = max(score, prefs.getInt("best2048", 0))
                prefs.edit().putInt("best2048", best).apply()
                render()
                if (board.any { row -> row.any { it >= 2048 } }) {
                    AlertDialog.Builder(this).setTitle("2048 reached!")
                        .setMessage("Excellent! Your score is $score.")
                        .setPositiveButton("Keep playing", null).setNegativeButton("New game") { _, _ -> start2048() }.show()
                } else if (!canMove()) {
                    AlertDialog.Builder(this).setTitle("Game over")
                        .setMessage("Final score: $score")
                        .setPositiveButton("Play again") { _, _ -> start2048() }.setNegativeButton("Back") { _, _ -> showHub() }.show()
                }
            }
        }
        spawn(); spawn(); render()
        root.addView(scoreText)
        root.addView(grid, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER; topMargin = dp(16); bottomMargin = dp(12) })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun controlRow(labels: List<Pair<String, Int>>) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
            labels.forEach { (label, dir) ->
                row.addView(Button(this).apply {
                    text = label; isAllCaps = false; setOnClickListener { move(dir) }
                }, LinearLayout.LayoutParams(dp(76), dp(52)).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) })
            }
            controls.addView(row)
        }
        controlRow(listOf("↑" to 2))
        controlRow(listOf("←" to 0, "↓" to 3, "→" to 1))
        root.addView(controls)
        root.addView(Button(this).apply { text = "New game"; setOnClickListener { start2048() } })
    }

    private fun startMemory() {
        base("MEMORY MATCH")
        val icons = listOf("🎵", "🎬", "🎧", "📷", "🎮", "⭐")
        val cards = (icons + icons).shuffled().toMutableList()
        val revealed = mutableSetOf<Int>()
        val matched = mutableSetOf<Int>()
        var first: Int? = null
        var locked = false
        var moves = 0
        val status = TextView(this).apply { textSize = 16f; setTextColor(Color.WHITE); setPadding(0, 0, 0, dp(12)) }
        val grid = GridLayout(this).apply { columnCount = 3; rowCount = 4 }
        lateinit var buttons: Array<Button>
        buttons = Array(12) { index ->
            Button(this).apply {
                text = "?"; textSize = 24f; isAllCaps = false
                setOnClickListener {
                    if (locked || index in matched || index in revealed) return@setOnClickListener
                    revealed.add(index); text = cards[index]; moves++
                    if (first == null) first = index
                    else {
                        val previous = first!!
                        if (cards[previous] == cards[index]) {
                            matched.add(previous); matched.add(index); first = null
                            if (matched.size == 12) {
                                getSharedPreferences("arcade_scores", MODE_PRIVATE).edit()
                                    .putInt("memoryBest", minOf(moves, getSharedPreferences("arcade_scores", MODE_PRIVATE).getInt("memoryBest", Int.MAX_VALUE))).apply()
                                AlertDialog.Builder(this@MediaHubArcadeActivity)
                                    .setTitle("You matched them all!")
                                    .setMessage("Completed in $moves moves.")
                                    .setPositiveButton("Play again") { _, _ -> startMemory() }
                                    .setNegativeButton("Back") { _, _ -> showHub() }.show()
                            }
                        } else {
                            locked = true
                            grid.postDelayed({
                                revealed.remove(previous); revealed.remove(index)
                                buttons[previous].text = "?"; buttons[index].text = "?"
                                first = null; locked = false
                            }, 700)
                        }
                    }
                    status.text = "Moves: $moves    Pairs: ${matched.size / 2}/6"
                }
            }.also { button ->
                grid.addView(button, GridLayout.LayoutParams().apply {
                    width = dp(94); height = dp(88); setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
        }
        status.text = "Moves: 0    Pairs: 0/6"
        root.addView(status)
        root.addView(grid, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER })
        root.addView(Button(this).apply { text = "Shuffle / restart"; setOnClickListener { startMemory() } })
    }

    private fun rounded(color: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(16).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
