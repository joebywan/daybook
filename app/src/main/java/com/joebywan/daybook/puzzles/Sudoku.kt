package com.joebywan.daybook.puzzles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import kotlinx.serialization.Serializable

@Serializable
data class SudokuState(
    val givens: List<Boolean>,
    val cells: List<Int>,
    val solution: List<Int>,
    val selected: Int? = null,
    override val moves: Int = 0,
) : PuzzleState {

    override val solved: Boolean get() = cells == solution

    /** Filled digits that clash with another digit in the same row, column or box. */
    fun conflicts(): Set<Int> {
        val bad = mutableSetOf<Int>()
        for (i in cells.indices) {
            val v = cells[i]
            if (v == 0) continue
            for (j in Sudoku.peers(i)) {
                if (cells[j] == v) {
                    bad += i
                    bad += j
                }
            }
        }
        return bad
    }

    fun withCell(index: Int, value: Int): SudokuState =
        if (givens[index]) this
        else copy(cells = cells.toMutableList().also { it[index] = value }, moves = moves + 1)

    fun select(index: Int): SudokuState = copy(selected = index)
}

/** Sudoku — the classic 9x9. */
object Sudoku : PuzzleType {

    override val id = "sudoku"
    override val displayName = "Sudoku"
    override val tagline = "One to nine, once per row, column and box"
    override val accent = 0xFF4C86D9
    override val rules = listOf(
        "Fill every cell with a digit from 1 to 9.",
        "No digit may repeat within a row, a column or a 3x3 box.",
        "Tap a cell, then tap a digit. Tap the digit again to clear it.",
        "Clashing digits are shown in red as you go.",
    )

    /** The 20 cells that share a row, column or box with [index]. Precomputed once. */
    private val peerTable: Array<IntArray> = Array(81) { i ->
        val r = i / 9
        val c = i % 9
        val br = r / 3 * 3
        val bc = c / 3 * 3
        val set = sortedSetOf<Int>()
        for (k in 0 until 9) {
            set += r * 9 + k
            set += k * 9 + c
            set += (br + k / 3) * 9 + (bc + k % 3)
        }
        set -= i
        set.toIntArray()
    }

    fun peers(index: Int): IntArray = peerTable[index]

    private fun clueTarget(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> 38
        Difficulty.HARD -> 30
        Difficulty.EXPERT -> 24
    }

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState {
        val rng = Rng(seed)
        val solution = fullGrid(rng)
        val puzzle = solution.toMutableList()
        var clues = 81
        val target = clueTarget(difficulty)

        // Dig holes in a random order, keeping only removals that leave the solution unique.
        for (index in rng.shuffled((0 until 81).toList())) {
            if (clues <= target) break
            val saved = puzzle[index]
            puzzle[index] = 0
            if (countSolutions(puzzle.toMutableList(), 0) == 1) {
                clues--
            } else {
                puzzle[index] = saved
            }
        }

        return SudokuState(
            givens = puzzle.map { it != 0 },
            cells = puzzle.toList(),
            solution = solution,
        )
    }

    private fun fullGrid(rng: Rng): List<Int> {
        val grid = MutableList(81) { 0 }
        fun fill(index: Int): Boolean {
            if (index == 81) return true
            for (digit in rng.shuffled((1..9).toList())) {
                if (peerTable[index].none { grid[it] == digit }) {
                    grid[index] = digit
                    if (fill(index + 1)) return true
                    grid[index] = 0
                }
            }
            return false
        }
        fill(0)
        return grid
    }

    /** Counts solutions, stopping at two. Always picks the most-constrained cell first. */
    private fun countSolutions(grid: MutableList<Int>, found: Int): Int {
        var best = -1
        var bestOptions: List<Int>? = null
        for (i in 0 until 81) {
            if (grid[i] != 0) continue
            val used = BooleanArray(10)
            for (p in peerTable[i]) used[grid[p]] = true
            val options = (1..9).filter { !used[it] }
            if (options.isEmpty()) return found
            if (bestOptions == null || options.size < bestOptions.size) {
                best = i
                bestOptions = options
                if (options.size == 1) break
            }
        }
        val options = bestOptions ?: return found + 1
        var total = found
        for (digit in options) {
            grid[best] = digit
            total = countSolutions(grid, total)
            grid[best] = 0
            if (total >= 2) return total
        }
        return total
    }

    override fun hint(state: PuzzleState): PuzzleState? {
        val s = state as SudokuState
        val wrong = s.cells.indices.firstOrNull {
            s.cells[it] != 0 && s.cells[it] != s.solution[it]
        }
        if (wrong != null) return s.withCell(wrong, s.solution[wrong])
        val blank = s.cells.indices.firstOrNull { s.cells[it] == 0 } ?: return null
        return s.withCell(blank, s.solution[blank])
    }

    // ---- home-grid motif ----------------------------------------------------------------------

    /**
     * One hand-picked 3x3 box. Written out rather than taken from a generated board because the
     * motif has to be identical on every device and every day: it is what the tile *is*, so a
     * board that varied would make the home screen look unstable for no gain.
     *
     * Four digits, spread so no row or column of the box is empty and none is full — enough to
     * read as a part-solved box at a glance, few enough that each digit stays large.
     */
    private val PREVIEW_DIGITS = listOf(
        5, 0, 3,
        0, 7, 0,
        0, 0, 2,
    )

    /** Which of [PREVIEW_DIGITS] are printed clues; the rest were "entered" and take the accent. */
    private val PREVIEW_GIVENS = listOf(
        true, false, true,
        false, true, false,
        false, false, false,
    )

    /** The cell drawn as selected, so the tile carries the accent even in the empty half. */
    private const val PREVIEW_SELECTED = 6

    /**
     * One 3x3 box rather than the whole grid: a 9x9 board at tile size is a grey smudge, whereas a
     * single box keeps the digits big enough to be read as digits, which is what says "Sudoku"
     * before the name under the tile is read.
     */
    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        BoxWithConstraints(modifier) {
            // Sized from both constraints, like Mosaic's board: a tile that is ever handed a
            // shorter box than it is wide should shrink rather than draw its bottom row outside.
            val cell = minOf(maxWidth, maxHeight) / 3
            for (r in 0 until 3) {
                for (c in 0 until 3) {
                    val i = r * 3 + c
                    val given = PREVIEW_GIVENS[i]
                    Box(
                        Modifier
                            .padding(start = cell * c, top = cell * r)
                            .size(cell)
                            .padding(1.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (i == PREVIEW_SELECTED) Color(accent).copy(alpha = 0.40f)
                                else scheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (PREVIEW_DIGITS[i] != 0) {
                            Text(
                                text = PREVIEW_DIGITS[i].toString(),
                                fontSize = (cell.value * 0.52f).sp,
                                fontWeight = if (given) FontWeight.Bold else FontWeight.Normal,
                                color = if (given) scheme.onSurface else Color(accent),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as SudokuState
        val scheme = MaterialTheme.colorScheme
        val conflicts = s.conflicts()
        val selectedValue = s.selected?.let { s.cells[it] } ?: 0

        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cell = maxWidth / 9
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    for (r in 0 until 9) {
                        for (c in 0 until 9) {
                            val i = r * 9 + c
                            val isSelected = s.selected == i
                            val sameValue = selectedValue != 0 && s.cells[i] == selectedValue
                            Box(
                                Modifier
                                    .padding(start = cell * c, top = cell * r)
                                    .size(cell)
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    // The selected cell's row, column and box are deliberately
                                    // left alone. Shading them restyled twenty of the eighty-one
                                    // squares on every tap, so the board had to be re-read after
                                    // each selection to find the 3x3 structure again -- the cost
                                    // outweighed the help. Only the selection itself and cells
                                    // holding the same digit react.
                                    .background(
                                        when {
                                            isSelected -> Color(accent).copy(alpha = 0.40f)
                                            sameValue -> Color(accent).copy(alpha = 0.20f)
                                            (r / 3 + c / 3) % 2 == 0 -> scheme.surfaceVariant
                                            else -> scheme.surface
                                        }
                                    )
                                    .clickable(enabled = interactive) { onState(s.select(i)) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (s.cells[i] != 0) {
                                    Text(
                                        text = s.cells[i].toString(),
                                        fontSize = (cell.value * 0.52f).sp,
                                        fontWeight = if (s.givens[i]) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            i in conflicts -> scheme.error
                                            s.givens[i] -> scheme.onSurface
                                            else -> Color(accent)
                                        },
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                (1..9).forEach { digit ->
                    val remaining = 9 - s.cells.count { it == digit }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (remaining == 0) scheme.surfaceVariant else scheme.surface)
                            .clickable(enabled = interactive && s.selected != null) {
                                val at = s.selected ?: return@clickable
                                onState(s.withCell(at, if (s.cells[at] == digit) 0 else digit))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            digit.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (remaining == 0) scheme.outline else scheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
