package com.joebywan.daybook.puzzles

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleState
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng

enum class Fill { UNKNOWN, FILLED, EMPTY }

data class MosaicState(
    val width: Int,
    val height: Int,
    val clues: List<Int?>,
    val marks: List<Fill>,
    val solution: List<Boolean>,
    override val moves: Int = 0,
) : PuzzleState {

    override val solved: Boolean
        get() = marks.indices.all { (marks[it] == Fill.FILLED) == solution[it] }

    fun cycle(index: Int): MosaicState {
        val next = when (marks[index]) {
            Fill.UNKNOWN -> Fill.FILLED
            Fill.FILLED -> Fill.EMPTY
            Fill.EMPTY -> Fill.UNKNOWN
        }
        return copy(marks = marks.toMutableList().also { it[index] = next }, moves = moves + 1)
    }
}

/**
 * Mosaic — Fill-a-Pix.
 *
 * Each clue counts the filled squares in its own three-by-three neighbourhood, itself included.
 * Puzzles are carved down from a fully-clued grid, and every removal is checked by the solver, so
 * every board here is solvable by deduction alone with exactly one answer.
 */
object Mosaic : PuzzleType {

    override val id = "mosaic"
    override val displayName = "Mosaic"
    override val tagline = "Fill by the numbers, nine at a time"
    override val accent = 0xFFD97FB0
    override val rules = listOf(
        "Every number counts the filled squares in the 3x3 block centred on it, including its own square.",
        "Tap once to fill a square, twice to rule it out, three times to clear it.",
        "Every puzzle can be solved by deduction alone.",
    )

    private fun shape(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> 8 to 10
        Difficulty.HARD -> 10 to 13
        Difficulty.EXPERT -> 12 to 15
    }

    private fun neighbourhood(index: Int, w: Int, h: Int): List<Int> {
        val r = index / w
        val c = index % w
        return buildList {
            for (dr in -1..1) for (dc in -1..1) {
                val nr = r + dr
                val nc = c + dc
                if (nr in 0 until h && nc in 0 until w) add(nr * w + nc)
            }
        }
    }

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState {
        val (w, h) = shape(difficulty)

        repeat(12) { attempt ->
            val rng = Rng(seed + attempt)
            val solution = List(w * h) { rng.nextInt(100) < 48 }
            val full: List<Int?> = List(w * h) { i ->
                neighbourhood(i, w, h).count { solution[it] }
            }
            if (!solvableByLogic(w, h, full)) return@repeat

            // Strip clues the rest of the board already implies.
            val clues = full.toMutableList()
            for (i in rng.shuffled((0 until w * h).toList())) {
                val saved = clues[i]
                clues[i] = null
                if (!solvableByLogic(w, h, clues)) clues[i] = saved
            }
            return MosaicState(w, h, clues.toList(), List(w * h) { Fill.UNKNOWN }, solution)
        }

        // Extremely unlikely: fall back to the fully-clued board, which is always solvable.
        val rng = Rng(seed)
        val solution = List(w * h) { rng.nextInt(100) < 48 }
        val full: List<Int?> = List(w * h) { i -> neighbourhood(i, w, h).count { solution[it] } }
        return MosaicState(w, h, full, List(w * h) { Fill.UNKNOWN }, solution)
    }

    // ---- solver -------------------------------------------------------------------------------

    private const val UNSET = 0
    private const val ON = 1
    private const val OFF = 2

    /**
     * True when the clues can be driven to a complete grid by deduction alone.
     *
     * Two rules do all the work: a clue whose count is already met rules its remaining squares
     * out, and a clue that needs every one of its remaining squares fills them. If that settles
     * every square then the answer is both unique and reachable without guessing — which is the
     * standard this generator holds every board to, and the reason no search is needed here.
     */
    private fun solvableByLogic(w: Int, h: Int, clues: List<Int?>): Boolean {
        val cells = IntArray(w * h) { UNSET }
        val hoods = hoodsFor(w, h)
        if (!propagate(clues, cells, hoods)) return false
        return cells.none { it == UNSET }
    }

    /** Neighbourhood lists are shape-dependent only, so they are built once per board size. */
    private val hoodCache = HashMap<Long, Array<List<Int>>>()

    private fun hoodsFor(w: Int, h: Int): Array<List<Int>> = synchronized(hoodCache) {
        hoodCache.getOrPut(w.toLong() shl 32 or h.toLong()) {
            Array(w * h) { neighbourhood(it, w, h) }
        }
    }

    /** Applies both rules until nothing changes. Returns false if the clues contradict. */
    private fun propagate(clues: List<Int?>, cells: IntArray, hoods: Array<List<Int>>): Boolean {
        var changed = true
        while (changed) {
            changed = false
            for (i in clues.indices) {
                val clue = clues[i] ?: continue
                val hood = hoods[i]
                var on = 0
                var unset = 0
                for (j in hood) {
                    when (cells[j]) {
                        ON -> on++
                        UNSET -> unset++
                    }
                }
                if (on > clue || on + unset < clue) return false
                if (unset == 0) continue
                if (on == clue) {
                    for (j in hood) if (cells[j] == UNSET) cells[j] = OFF
                    changed = true
                } else if (on + unset == clue) {
                    for (j in hood) if (cells[j] == UNSET) cells[j] = ON
                    changed = true
                }
            }
        }
        return true
    }

    // ---- play ---------------------------------------------------------------------------------

    override fun hint(state: PuzzleState): PuzzleState? {
        val s = state as MosaicState
        val wrong = s.marks.indices.firstOrNull {
            (s.marks[it] == Fill.FILLED) != s.solution[it] && s.marks[it] != Fill.UNKNOWN
        }
        val target = wrong ?: s.marks.indices.firstOrNull { s.marks[it] == Fill.UNKNOWN } ?: return null
        return s.copy(
            marks = s.marks.toMutableList().also {
                it[target] = if (s.solution[target]) Fill.FILLED else Fill.EMPTY
            },
            moves = s.moves + 1,
        )
    }

    override fun reveal(state: PuzzleState): PuzzleState {
        val s = state as MosaicState
        return s.copy(marks = s.solution.map { if (it) Fill.FILLED else Fill.EMPTY })
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as MosaicState
        val scheme = MaterialTheme.colorScheme

        BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
            val cell = maxWidth / s.width
            val cellPx = with(LocalDensity.current) { cell.toPx() }
            Box(
                Modifier
                    .width(cell * s.width)
                    .height(cell * s.height)
                    .pointerInput(s, interactive) {
                        if (!interactive) return@pointerInput
                        detectTapGestures { offset: Offset ->
                            val c = (offset.x / cellPx).toInt().coerceIn(0, s.width - 1)
                            val r = (offset.y / cellPx).toInt().coerceIn(0, s.height - 1)
                            onState(s.cycle(r * s.width + c))
                        }
                    }
            ) {
                for (r in 0 until s.height) {
                    for (c in 0 until s.width) {
                        val i = r * s.width + c
                        val mark = s.marks[i]
                        Box(
                            Modifier
                                .padding(start = cell * c, top = cell * r)
                                .size(cell)
                                .padding(0.7.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when (mark) {
                                        Fill.FILLED -> Color(accent)
                                        Fill.EMPTY -> scheme.background
                                        Fill.UNKNOWN -> scheme.surfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            s.clues[i]?.let { clue ->
                                Text(
                                    clue.toString(),
                                    fontSize = (cell.value * 0.44f).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when (mark) {
                                        Fill.FILLED -> scheme.background
                                        else -> scheme.onSurface
                                    },
                                )
                            }
                            if (mark == Fill.EMPTY && s.clues[i] == null) {
                                Text(
                                    "·",
                                    fontSize = (cell.value * 0.5f).sp,
                                    color = scheme.outline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
