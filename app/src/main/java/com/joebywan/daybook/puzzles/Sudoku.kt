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
import com.joebywan.daybook.ui.boardSp

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
        "Fill every square with a digit from 1 to 9.",
        "No digit may repeat within a row, a column or a 3x3 box.",
        "Tap a square, then tap a digit. Tap the digit again to clear it.",
        "Tap the pencil to take notes: while it is lit, a digit is pencilled small in the square " +
            "instead of placed. Placing a digit clears it from the notes of the squares it sees.",
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
    fun isSolved(cells: List<Int>): Boolean =
        cells.size == 81 && cells.all { it in 1..9 } && SUDOKU_UNITS.all { cells.holdsOneToNine(it) }

    /** Bits 1..9 set. */
    private const val ALL_DIGITS = 0b11_1111_1110

    /**
     * A tier's gate: digging stops short of any board a walk by the teacher's ladder up to [ceiling] cannot finish, and a
     * dug board ships only if that walk needed [needs] or deeper ([SudokuTeacher.TECHNIQUES] order). Standard: singles in
     * a unit; Hard: a naked single, nothing past it; Expert: an elimination (locked candidates or a pair) or a what-if,
     * never the fallback. So the tiers cannot overlap: each has a depth of its own.
     */
    private class Gate(val ceiling: String, val needs: String)

    private fun gate(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> Gate(SudokuTeacher.HIDDEN_SINGLE_LINE, SudokuTeacher.FULL_HOUSE)
        Difficulty.HARD -> Gate(SudokuTeacher.NAKED_SINGLE, SudokuTeacher.NAKED_SINGLE)
        Difficulty.EXPERT -> Gate(SudokuTeacher.WHAT_IF, SudokuTeacher.LOCKED)
    }

    /** Fresh grids [generateVerified] tries before giving up. */
    internal const val ATTEMPTS = 100

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        generateVerified(seed, difficulty) ?: lastResort(difficulty)

    /**
     * The board when it was proved, null otherwise. What `FallbackTest` asserts on. Each attempt fills a grid from
     * `Rng(seed + attempt)` and digs it in 180-degree pairs (the centre alone), in a random order, keeping each removal
     * only while [countSolutions] (exhaustive) still finds one answer and the tier's ladder still finishes the board:
     * the givens are minimal for that ladder, symmetric, and the clue count is whatever that leaves. The dug board then
     * ships only if its walk needed the tier's technique ([Gate.needs]); otherwise the next grid is tried.
     */
    internal fun generateVerified(seed: Long, difficulty: Difficulty, attempts: Int = ATTEMPTS): SudokuState? {
        val gate = gate(difficulty)
        val needs = SudokuTeacher.TECHNIQUES.indexOf(gate.needs)
        for (attempt in 0 until attempts) {
            val rng = Rng(seed + attempt)
            val solution = fullGrid(rng)
            val puzzle = solution.toMutableList()
            for (i in rng.shuffled((0..40).toList())) {
                puzzle[i] = 0
                puzzle[80 - i] = 0
                if (countSolutions(puzzle) == 1 && finishes(puzzle, gate.ceiling)) continue
                puzzle[i] = solution[i]
                puzzle[80 - i] = solution[80 - i]
            }
            val depth = SudokuTeacher.depth(puzzle, gate.ceiling) ?: continue
            if (depth < needs) continue
            return SudokuState(givens = puzzle.map { it != 0 }, cells = puzzle.toList(), solution = solution)
        }
        return null
    }

    /**
     * Whether the teacher's walk up to [ceiling] finishes [puzzle], the same verdict as `SudokuTeacher.depth(...) != null`
     * but cheaper, since the dig asks it 41 times a grid. Singles are settled here first with bitmasks: they never
     * stop being singles as digits go in, so every order of taking them reaches the same grid, the one the teacher's walk
     * (singles always first) reaches before it needs anything deeper. Only there, and only when [ceiling] allows more
     * than singles, is the teacher asked for its next step, and then the singles are settled again. `SudokuRulesTest`
     * checks the two agree.
     */
    internal fun finishes(puzzle: List<Int>, ceiling: String): Boolean {
        val grid = puzzle.toMutableList()
        val cand = IntArray(81)
        fun place(i: Int, d: Int) {
            grid[i] = d
            cand[i] = 0
            for (p in peerTable[i]) cand[p] = cand[p] and (1 shl d).inv()
        }
        val naked = ceiling != SudokuTeacher.HIDDEN_SINGLE_LINE
        while (true) {
            for (i in 0 until 81) {
                cand[i] = 0
                if (grid[i] == 0) {
                    var mask = ALL_DIGITS
                    for (p in peerTable[i]) mask = mask and (1 shl grid[p]).inv()
                    cand[i] = mask
                }
            }
            var changed = true
            while (changed) {
                changed = false
                for (unit in SUDOKU_UNITS) for (d in 1..9) {
                    var at = -1
                    var count = 0
                    for (i in unit) if (cand[i] and (1 shl d) != 0) { at = i; count++ }
                    if (count == 1) { place(at, d); changed = true }
                }
                if (naked) for (i in 0 until 81) {
                    if (cand[i].countOneBits() == 1) { place(i, cand[i].countTrailingZeroBits()); changed = true }
                }
            }
            if (0 !in grid) return true
            if (ceiling != SudokuTeacher.WHAT_IF) return false
            // No single is left, so the teacher's next step is its first elimination or what-if, exactly as on its walk.
            val step = SudokuTeacher.deduce(grid, ceiling) ?: return false
            grid[step.cell] = step.digit
        }
    }

    /** The seed [lastResort] draws from. */
    private const val LAST_RESORT_SEED = 20261025L

    /**
     * What ships for a seed [generateVerified] did not prove: the board of one fixed seed, itself proved (a test asserts
     * it on every tier, so this cannot throw once that test is green). A year of daily seeds never reaches it.
     */
    internal fun lastResort(difficulty: Difficulty): SudokuState =
        generateVerified(LAST_RESORT_SEED, difficulty) ?: error("the fixed last-resort seed no longer proves a board")

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

    /**
     * How many answers [puzzle] has, counted up to two. Exhaustive (no node budget, so 1 means one answer): a digit mask
     * per row, column and box, and the most constrained empty cell split first.
     */
    internal fun countSolutions(puzzle: List<Int>): Int {
        val grid = puzzle.toIntArray()
        val rows = IntArray(9)
        val cols = IntArray(9)
        val boxes = IntArray(9)
        fun box(i: Int) = i / 27 * 3 + i % 9 / 3
        for (i in 0 until 81) if (grid[i] != 0) {
            val bit = 1 shl grid[i]
            if ((rows[i / 9] or cols[i % 9] or boxes[box(i)]) and bit != 0) return 0
            rows[i / 9] = rows[i / 9] or bit
            cols[i % 9] = cols[i % 9] or bit
            boxes[box(i)] = boxes[box(i)] or bit
        }
        fun count(found: Int): Int {
            var best = -1
            var bestFree = 0
            var bestSize = 10
            for (i in 0 until 81) {
                if (grid[i] != 0) continue
                val free = ALL_DIGITS and (rows[i / 9] or cols[i % 9] or boxes[box(i)]).inv()
                val size = free.countOneBits()
                if (size == 0) return found
                if (size < bestSize) {
                    best = i
                    bestFree = free
                    bestSize = size
                    if (size == 1) break
                }
            }
            if (best < 0) return found + 1
            var total = found
            for (d in 1..9) {
                val bit = 1 shl d
                if (bestFree and bit == 0) continue
                grid[best] = d
                rows[best / 9] = rows[best / 9] xor bit
                cols[best % 9] = cols[best % 9] xor bit
                boxes[box(best)] = boxes[box(best)] xor bit
                total = count(total)
                grid[best] = 0
                rows[best / 9] = rows[best / 9] xor bit
                cols[best % 9] = cols[best % 9] xor bit
                boxes[box(best)] = boxes[box(best)] xor bit
                if (total >= 2) return total
            }
            return total
        }
        return count(0)
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
                                fontSize = (cell * 0.52f).boardSp(), lineHeight = (cell * 0.52f).boardSp() * 1.2f,
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
