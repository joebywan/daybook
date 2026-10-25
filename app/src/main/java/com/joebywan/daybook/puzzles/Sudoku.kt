package com.joebywan.daybook.puzzles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialPath
import com.joebywan.daybook.core.placementDeduction
import com.joebywan.daybook.ui.theme.BoardHues
import kotlinx.serialization.Serializable

@Serializable
data class SudokuState(
    val givens: List<Boolean>,
    val cells: List<Int>,
    val solution: List<Int>,
    val selected: Int? = null,
    override val moves: Int = 0,
    /**
     * The player's pencil marks: one 9-bit mask per cell, bit `d - 1` set when digit `d` is noted
     * there. Defaulted and, being equal to its default on a game without notes, never written, so a
     * save made before notes existed still decodes, with none. Every mark is drawn: a mark the
     * player makes is never refused or hidden, even for a digit a peer already holds (a wrong
     * judgement is theirs to make, and the conflict display catches it once a digit goes in). A cell
     * holding a digit carries no notes. Marks are tidied only when a digit is *placed*, in the same
     * state ([withCell]); taking that digit back does not bring the cleared marks back, except by
     * undo, which restores the whole earlier state.
     */
    val notes: List<Int> = NO_NOTES,
) : PuzzleState {

    /**
     * The rules, not a comparison with [solution]: every cell filled and no digit repeated in any
     * row, column or box. The stored answer is for hints only. A given can't be changed (see
     * [withCell]), so it needs no check of its own.
     */
    override val solved: Boolean get() = Sudoku.isSolved(cells)

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

    /**
     * Sets a digit, or clears the cell with 0. The cell's own notes go either way: a placed digit
     * makes them moot, and clearing an empty cell is how its notes are rubbed out. Placing a digit
     * also strikes it from the notes of every peer, in this same state, so one undo restores the
     * digit and those notes together. Erasing a digit does not put them back. (Not derived at
     * render time: a note hidden by a peer is indistinguishable from a tap that was refused.)
     */
    fun withCell(index: Int, value: Int): SudokuState {
        if (givens[index]) return this
        val bit = if (value == 0) 0 else 1 shl (value - 1)
        val next = notes.toMutableList()
        next[index] = 0
        if (bit != 0) for (p in Sudoku.peers(index)) next[p] = next[p] and bit.inv()
        return copy(
            cells = cells.toMutableList().also { it[index] = value },
            notes = if (next == notes) notes else next,
            moves = moves + 1,
        )
    }

    /** The notes drawn on [index]: none on a filled cell. */
    fun visibleNotes(index: Int): Int = if (cells[index] != 0) 0 else notes[index]

    /** Whether a note for [digit] is drawn on [index]. */
    fun hasNote(index: Int, digit: Int): Boolean = visibleNotes(index) and (1 shl (digit - 1)) != 0

    /** Whether notes can be made on [index] at all: an empty square that is not a given. */
    fun canNote(index: Int): Boolean = !givens[index] && cells[index] == 0

    /** Adds the note for [digit] on [index], or takes it off. Always, for any digit, on an open square. */
    fun toggleNote(index: Int, digit: Int): SudokuState =
        if (!canNote(index)) this
        else copy(
            notes = notes.toMutableList().also { it[index] = it[index] xor (1 shl (digit - 1)) },
            moves = moves + 1,
        )

    fun select(index: Int): SudokuState = copy(selected = index)
}

/** Every cell without notes: the default, and so what a save from before notes existed decodes to. */
private val NO_NOTES: List<Int> = List(81) { 0 }

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
        "Tap the pencil to take notes: while it is lit, a digit is pencilled small in the cell " +
            "instead of placed. Placing a digit clears it from the notes of the cells it sees.",
        "Clashing digits are shown in red as you go.",
    )

    override val keyboardHelp = listOf(
        "Arrows move, 1-9 place a digit (or a note in pencil mode), Backspace clears.",
    )

    /** The 20 cells that share a row, column or box with [index]. Precomputed once. */
    private val peerTable: Array<IntArray> = Array(81) { i ->
        val r = i / 9
        val c = i % 9
        val br = r / 3 * 3
        val bc = c / 3 * 3
        // Ascending, the order the java.util.TreeSet this used to be walked them in. Only
        // membership reaches the generator today, but a fixed order keeps it that way on every
        // platform. Not sortedSetOf, which is JVM-only and would keep this file off the web.
        val peers = BooleanArray(81)
        for (k in 0 until 9) {
            peers[r * 9 + k] = true
            peers[k * 9 + c] = true
            peers[(br + k / 3) * 9 + (bc + k % 3)] = true
        }
        peers[i] = false
        (0 until 81).filter { peers[it] }.toIntArray()
    }

    fun peers(index: Int): IntArray = peerTable[index]

    /**
     * Whether [cells] is a finished grid: 81 digits of 1..9, each row, column and box holding every
     * digit exactly once. Reads only the grid, never a stored answer, so a legal fill that is not
     * the generator's still wins.
     */
    fun isSolved(cells: List<Int>): Boolean {
        if (cells.size != 81 || cells.any { it !in 1..9 }) return false
        for (k in 0 until 9) {
            var row = 0
            var col = 0
            var box = 0
            for (j in 0 until 9) {
                row = row or (1 shl cells[k * 9 + j])
                col = col or (1 shl cells[j * 9 + k])
                box = box or (1 shl cells[(k / 3 * 3 + j / 3) * 9 + k % 3 * 3 + j % 3])
            }
            if (row != ALL_DIGITS || col != ALL_DIGITS || box != ALL_DIGITS) return false
        }
        return true
    }

    /** Bits 1..9 set. */
    private const val ALL_DIGITS = 0b11_1111_1110

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

    internal fun fullGrid(rng: Rng): List<Int> {
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

    // ---- teaching --------------------------------------------------------------------------

    /**
     * A mistake to take back or a step to reason out — see [SudokuTeacher]. Never null on an unsolved
     * board (the fallback names a digit from [SudokuState.solution]), so `hint()` is never reached.
     * The teacher reasons from the visible digits alone.
     */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as SudokuState
        val step = SudokuTeacher.teach(s) ?: return null
        return placementDeduction(
            s, step,
            mistake = step.technique == SudokuTeacher.MISTAKE,
            fallback = step.technique == SudokuTeacher.FALLBACK,
            valueAt = { t, cell -> t.cells[cell] },
            place = { t, cell, digit -> t.withCell(cell, digit).select(cell) },
        )
    }

    // ---- walkthrough ---------------------------------------------------------------------------

    /**
     * A finished grid with ten cells open, one of them the only gap in row 1. Most people know the
     * rules, so the walkthrough is the rules in a line and then the real gestures: select, place,
     * clear. SudokuTeachingTest proves the open board has only this answer.
     */
    internal val TUTORIAL_SOLUTION = listOf(
        8, 2, 3, 1, 6, 4, 5, 7, 9,
        1, 9, 4, 2, 7, 5, 6, 8, 3,
        5, 6, 7, 9, 8, 3, 1, 4, 2,
        9, 4, 1, 8, 5, 2, 3, 6, 7,
        7, 8, 5, 6, 3, 1, 9, 2, 4,
        6, 3, 2, 4, 9, 7, 8, 5, 1,
        2, 5, 6, 3, 4, 9, 7, 1, 8,
        4, 7, 9, 5, 1, 8, 2, 3, 6,
        3, 1, 8, 7, 2, 6, 4, 9, 5,
    )
    internal val TUTORIAL_OPEN = setOf(4, 10, 24, 30, 38, 42, 52, 58, 63, 80)

    /** Row 1's only gap: a 6. */
    private const val TUTORIAL_FIRST = 4

    /** Where the walkthrough's wrong digit goes: a 9 where the answer is 5. */
    private const val TUTORIAL_WRONG = 38

    private fun tutorialBoard(
        entered: Map<Int, Int> = emptyMap(),
        selected: Int? = null,
        noted: Map<Int, Int> = emptyMap(),
    ): SudokuState {
        val cells = TUTORIAL_SOLUTION.mapIndexed { i, v -> if (i in TUTORIAL_OPEN) entered[i] ?: 0 else v }
        return SudokuState(
            givens = TUTORIAL_SOLUTION.indices.map { it !in TUTORIAL_OPEN },
            cells = cells,
            solution = TUTORIAL_SOLUTION,
            selected = selected,
            notes = List(81) { i -> noted[i]?.let { 1 shl (it - 1) } ?: 0 },
        )
    }

    /** The walkthrough's pencil mark: a 5 in the cell the wrong 9 was in, which is its answer. */
    private const val TUTORIAL_NOTED = 5

    override val tutorial: List<TutorialFrame> by lazy {
        val row1 = (0 until 9).toSet()
        val start = tutorialBoard()
        val placed = mapOf(TUTORIAL_FIRST to 6)
        val done = tutorialBoard(placed)
        // The 9s a 9 in the wrong cell clashes with: one in its column, one in its box.
        val clashes = peers(TUTORIAL_WRONG).filter { done.cells[it] == 9 }.toSet()
        val place = TutorialPath(
            { it is SudokuState && it.cells == start.cells && it.selected == TUTORIAL_FIRST },
            { it is SudokuState && it.cells == done.cells },
        )
        val clash = TutorialPath(
            { it is SudokuState && it.cells == done.cells && it.selected == TUTORIAL_WRONG },
            { it is SudokuState && it.cells == tutorialBoard(placed + (TUTORIAL_WRONG to 9)).cells },
            // Back where it started, but by way of the 9: only a move counted since says so.
            { it is SudokuState && it.cells == done.cells && it.moves > 0 },
        )
        listOf(
            TutorialFrame(
                state = start,
                caption = "Fill every row, column and 3x3 box with the digits 1 to 9, each once. " +
                    "The bold digits are given.",
                highlight = BoardHighlight(strong = row1),
            ),
            TutorialFrame(
                state = start,
                caption = "Row 1 has one empty square left, and it holds every digit but 6. Tap the square, " +
                    "then 6 below.",
                // The square glows until it is selected, then the 6.
                glow = { s ->
                    val strong = if (place.stage(s) < 0) TUTORIAL_FIRST else SudokuTeacher.pad(6)
                    BoardHighlight(strong = setOf(strong), soft = row1)
                },
                steps = place.steps,
                retry = "Tap the glowing square in row 1, then the 6.",
                done = "Row 1 is complete.",
            ),
            TutorialFrame(
                state = tutorialBoard(placed, selected = TUTORIAL_FIRST),
                caption = "A digit that breaks a rule turns red. Put a 9 in the glowing square: it shares a " +
                    "column and a box with a 9. Then tap 9 again to clear it.",
                glow = { s ->
                    when (clash.stage(s)) {
                        -1 -> BoardHighlight(strong = setOf(TUTORIAL_WRONG), soft = clashes)
                        0 -> BoardHighlight(strong = setOf(SudokuTeacher.pad(9)), soft = clashes)
                        else -> BoardHighlight(strong = setOf(TUTORIAL_WRONG, SudokuTeacher.pad(9)), soft = clashes, warning = true)
                    }
                },
                steps = clash.steps,
                retry = "Tap the glowing square, then the 9, then the 9 again.",
                done = "Cleared. Tapping the digit again takes it out.",
            ),
            TutorialFrame(
                state = tutorialBoard(placed, selected = TUTORIAL_WRONG),
                caption = "Not sure of a digit? Tap the pencil, then a digit, to jot it small in the square " +
                    "instead of placing it. Pencil a 5 here.",
                highlight = BoardHighlight(
                    strong = setOf(TUTORIAL_WRONG, SudokuTeacher.NOTES_KEY, SudokuTeacher.pad(TUTORIAL_NOTED)),
                ),
                accepts = { next ->
                    next is SudokuState && next.cells == done.cells &&
                        next.notes == tutorialBoard(placed, noted = mapOf(TUTORIAL_WRONG to TUTORIAL_NOTED)).notes
                },
                retry = "Tap the glowing pencil, then the 5.",
                done = "Penciled. Tap the pencil again to go back to placing digits.",
            ),
            TutorialFrame(
                state = tutorialBoard(placed, selected = TUTORIAL_WRONG, noted = mapOf(TUTORIAL_WRONG to TUTORIAL_NOTED)),
                caption = "Your turn: finish the board. Stuck? Hint shows you why. Placing a digit " +
                    "clears the square's notes.",
                freePlay = true,
                done = "Solved. That's all there is to it.",
            ),
        )
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
        val dark = BoardHues.isDark(scheme)
        val (hueA, hueB) = BoardHues.HUE_PAIRS[0]
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
                                if (i == PREVIEW_SELECTED) BoardHues.mark(hueA).copy(alpha = 0.55f)
                                else scheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (PREVIEW_DIGITS[i] != 0) {
                            Text(
                                text = PREVIEW_DIGITS[i].toString(),
                                fontSize = (cell.value * 0.52f).sp,
                                fontWeight = if (given) FontWeight.Bold else FontWeight.Normal,
                                color = if (given) scheme.onSurface else BoardHues.ink(hueB, dark),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun withoutSelection(state: PuzzleState): PuzzleState =
        (state as SudokuState).let { if (it.selected == null) it else it.copy(selected = null) }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as SudokuState
        SudokuGridBoard(
            s = s,
            conflicts = s.conflicts(),
            hueSeed = s.solution.hashCode(),
            interactive = interactive,
            accent = Color(accent),
            onSelect = { onState(s.select(it)) },
            onEnter = { cell, digit, notesMode ->
                // The keys are dimmed and inert where a note cannot go, so a refusal here is never silent.
                val next = s.enter(cell, digit, notesMode)
                if (next !== s) onState(next)
            },
            onKey = { action, notesMode -> s.applyKey(action, notesMode)?.let(onState) },
        )
    }

}
