package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.core.highlightAnchor
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialFrame
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
/** The digit pad under the grid, which the grid's size has to leave room for. */
private val PAD_HEIGHT = 48.dp
private val PAD_GAP = 18.dp

/** A note's size as a share of its cell's side, and how strongly it is inked. Tuned by rendering. */
private const val NOTE_SIZE = 0.34f
private const val NOTE_ALPHA = 0.85f

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

    // ---- teaching --------------------------------------------------------------------------

    /**
     * A mistake to take back or a step to reason out — see [SudokuTeacher]. The old [hint] dropped
     * a digit straight out of [SudokuState.solution] and taught nothing; it stays only as the
     * contract's default path. The teacher reasons from the visible digits alone.
     */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as SudokuState
        val step = SudokuTeacher.teach(s) ?: return null
        val cell = step.cell
        val digit = step.digit
        val mistake = step.technique == SudokuTeacher.MISTAKE
        val wrong = s.cells[cell]
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = setOf(cell),
            mistake = mistake,
            fallback = step.technique == SudokuTeacher.FALLBACK,
            // One state, so one undo entry; the cell is selected so the player sees where it went.
            applyTo = { now ->
                val t = now as SudokuState
                if (t.cells[cell] == digit) t else t.withCell(cell, digit).select(cell)
            },
            reachedBy = { now ->
                val t = now as SudokuState
                // A mistake is dealt with once that digit is gone, whatever replaced it.
                if (mistake) t.cells[cell] != wrong else t.cells[cell] == digit
            },
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
        val chosen = tutorialBoard(selected = TUTORIAL_FIRST)
        val placed = mapOf(TUTORIAL_FIRST to 6)
        val wrong = tutorialBoard(placed + (TUTORIAL_WRONG to 9), selected = TUTORIAL_WRONG)
        // The 9s the wrong one clashes with: one in its column, one in its box.
        val clashes = peers(TUTORIAL_WRONG).filter { wrong.cells[it] == 9 }.toSet()
        listOf(
            TutorialFrame(
                state = start,
                caption = "Fill every row, column and 3x3 box with the digits 1 to 9, each once. " +
                    "The bold digits are given.",
                highlight = BoardHighlight(strong = row1),
            ),
            TutorialFrame(
                state = start,
                caption = "Row 1 has one empty cell left. Tap it to select it.",
                highlight = BoardHighlight(strong = setOf(TUTORIAL_FIRST), soft = row1),
                accepts = { next -> next is SudokuState && next.cells == start.cells && next.selected == TUTORIAL_FIRST },
                retry = "Tap the glowing cell in row 1.",
                done = "Selected.",
            ),
            TutorialFrame(
                state = chosen,
                caption = "Row 1 holds every digit but 6, so it's a 6. Tap 6 below.",
                highlight = BoardHighlight(strong = setOf(TUTORIAL_FIRST, SudokuTeacher.pad(6)), soft = row1),
                accepts = { next -> next is SudokuState && next.cells == tutorialBoard(placed).cells },
                retry = "Tap the 6 in the row of digits below.",
                done = "Row 1 is complete.",
            ),
            TutorialFrame(
                state = wrong,
                caption = "A digit that clashes turns red: this 9 shares a column and a box with a 9. " +
                    "It's selected, so tap 9 again to clear it.",
                highlight = BoardHighlight(
                    strong = setOf(TUTORIAL_WRONG, SudokuTeacher.pad(9)),
                    soft = clashes,
                    warning = true,
                ),
                accepts = { next -> next is SudokuState && next.cells == tutorialBoard(placed).cells },
                retry = "Tap the 9 in the row of digits below.",
                done = "Cleared.",
            ),
            TutorialFrame(
                state = tutorialBoard(placed, selected = TUTORIAL_WRONG),
                caption = "Not sure of a digit? Tap the pencil, then a digit, to jot it small in the cell " +
                    "instead of placing it. Pencil a 5 here.",
                highlight = BoardHighlight(
                    strong = setOf(TUTORIAL_WRONG, SudokuTeacher.NOTES_KEY, SudokuTeacher.pad(TUTORIAL_NOTED)),
                ),
                accepts = { next ->
                    next is SudokuState && next.cells == tutorialBoard(placed).cells &&
                        next.notes == tutorialBoard(placed, noted = mapOf(TUTORIAL_WRONG to TUTORIAL_NOTED)).notes
                },
                retry = "Tap the pencil first so it lights up, then the 5.",
                done = "Penciled. Tap the pencil again to go back to placing digits.",
            ),
            TutorialFrame(
                state = tutorialBoard(placed, selected = TUTORIAL_WRONG, noted = mapOf(TUTORIAL_WRONG to TUTORIAL_NOTED)),
                caption = "Your turn: finish the board. Stuck? Hint shows you why. Placing a digit " +
                    "clears the cell's notes.",
                freePlay = true,
                done = "Solved. That's all there is to it.",
            ),
        )
    }

    // ---- drawing ------------------------------------------------------------------------------

    /**
     * What [LocalBoardHighlight] asks of one cell or key. Cells not named step back while anything
     * is highlighted, so the named ones read on a busy 9x9 board; the digit keys glow when named but
     * never dim, because the move a hint asks for is made with them.
     */
    private class Look(val strong: Boolean, val soft: Boolean, val dim: Boolean)

    private fun BoardHighlight.look(index: Int, dims: Boolean = true): Look {
        val strong = index in this.strong
        val soft = !strong && index in this.soft
        return Look(strong, soft, dims && !isEmpty && !strong && !soft)
    }

    /**
     * A ring drawn just inside the element, since the cells sit a dp apart and a ring outside would
     * cross into the next one. Breathing in [glow] for strong, a quiet fixed line for soft; read in
     * the draw phase so the pulse repaints without recomposing.
     */
    private fun Modifier.ring(look: Look, glow: Color, pulse: State<Float>, corner: Float): Modifier =
        if (!look.strong && !look.soft) this
        else drawWithContent {
            drawContent()
            val w = (if (look.strong) 2.5.dp else 1.5.dp).toPx()
            val colour = if (look.strong) glow.copy(alpha = pulse.value) else glow.copy(alpha = 0.5f)
            drawRoundRect(
                colour,
                topLeft = Offset(w / 2, w / 2),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(corner.dp.toPx()),
                style = Stroke(w),
            )
        }

    private fun Modifier.dimmed(look: Look): Modifier = if (look.dim) alpha(0.3f) else this

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

    override fun withoutSelection(state: PuzzleState): PuzzleState =
        (state as SudokuState).let { if (it.selected == null) it else it.copy(selected = null) }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as SudokuState
        val scheme = MaterialTheme.colorScheme
        val conflicts = s.conflicts()
        val selectedValue = s.selected?.let { s.cells[it] } ?: 0
        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        // Which way the digit keys act. Transient UI state, so it lives here and not in the state:
        // PlayScreen would otherwise make every flip of it an undo step.
        var notesMode by rememberSaveable { mutableStateOf(false) }
        val measurer = rememberTextMeasurer()
        // The keyboard (web, or a hardware keyboard on Android): digits, clear and arrows, mapped by
        // sudokuKeyAction and applied through the same state functions the pad and a tap use. See boardKeys.
        // Breathes only while something glows, as on Kings.
        val pulse: State<Float> = if (highlight.strong.isEmpty()) {
            remember { mutableFloatStateOf(1f) }
        } else {
            rememberInfiniteTransition(label = "hint").animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "hint-pulse",
            )
        }

        // Sized from both axes (CLAUDE.md): from width alone, a 693dp-tall screen ran the grid up
        // over the header and down under the hint slot. The pad keeps the full width.
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .boardKeys(interactive) { key, _, repeat ->
                    val action = sudokuKeyAction(key) ?: return@boardKeys false
                    if (!repeat || action is SudokuKeyAction.Move) s.applyKey(action, notesMode)?.let(onState)
                    true
                },
            contentAlignment = Alignment.Center,
        ) {
            val side = if (constraints.hasBoundedHeight) {
                minOf(maxWidth, (maxHeight - PAD_GAP - PAD_HEIGHT).coerceAtLeast(0.dp))
            } else {
                maxWidth
            }
            val cell = side / 9
            val padWidth = maxWidth
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(side).highlightGrid(9, 9)) {
                    for (r in 0 until 9) {
                        for (c in 0 until 9) {
                            val i = r * 9 + c
                            val isSelected = s.selected == i
                            val sameValue = selectedValue != 0 && s.cells[i] == selectedValue
                            val look = highlight.look(i)
                            Box(
                                Modifier
                                    .padding(start = cell * c, top = cell * r)
                                    .size(cell)
                                    .padding(1.dp)
                                    .dimmed(look)
                                    .ring(look, glow, pulse, corner = 4f)
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
                                val noted = s.visibleNotes(i)
                                if (noted != 0) {
                                    val ink = scheme.onSurface.copy(alpha = NOTE_ALPHA)
                                    val style = TextStyle(
                                        fontSize = (cell.value * NOTE_SIZE).sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ink,
                                        textAlign = TextAlign.Center,
                                    )
                                    // Drawn, not composed: nine Text nodes in each of up to eighty-one
                                    // cells would be a lot of layout for a board that redraws on every tap.
                                    Canvas(Modifier.fillMaxSize()) {
                                        val sub = size.width / 3f
                                        for (d in 1..9) {
                                            if (noted and (1 shl (d - 1)) == 0) continue
                                            val laid = measurer.measure(d.toString(), style, maxLines = 1)
                                            val r = (d - 1) / 3
                                            val c = (d - 1) % 3
                                            drawText(
                                                laid,
                                                topLeft = Offset(
                                                    c * sub + (sub - laid.size.width) / 2f,
                                                    r * (size.height / 3f) + (size.height / 3f - laid.size.height) / 2f,
                                                ),
                                            )
                                        }
                                    }
                                }
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

                Spacer(Modifier.height(PAD_GAP))

                Row(
                    Modifier.width(padWidth).keepClear(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    (1..9).forEach { digit ->
                        val remaining = 9 - s.cells.count { it == digit }
                        val at = s.selected
                        // In notes mode a key that is already pencilled on the selected cell is tinted.
                        val pencilled = notesMode && at != null && s.hasNote(at, digit)
                        // Notes cannot go on a filled or given cell: the keys show it rather than do nothing.
                        val inert = notesMode && at != null && !s.canNote(at)
                        Box(
                            Modifier
                                .weight(1f)
                                .alpha(if (inert) 0.35f else 1f)
                                .height(PAD_HEIGHT)
                                .highlightAnchor(SudokuTeacher.pad(digit))
                                .ring(highlight.look(SudokuTeacher.pad(digit), dims = false), glow, pulse, corner = 10f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        pencilled -> Color(accent).copy(alpha = 0.40f)
                                        remaining == 0 -> scheme.surfaceVariant
                                        else -> scheme.surface
                                    }
                                )
                                .clickable(enabled = interactive && at != null && !inert) {
                                    val cell = at ?: return@clickable
                                    // In notes mode the keys are dimmed and inert on a filled or given cell
                                    // (above), so a refusal here is never silent.
                                    val next = s.enter(cell, digit, notesMode)
                                    if (next !== s) onState(next)
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
                    // The mode switch lives with the keys it changes. Not a state: see notesMode.
                    Box(
                        Modifier
                            .weight(1f)
                            .height(PAD_HEIGHT)
                            .highlightAnchor(SudokuTeacher.NOTES_KEY)
                            .ring(highlight.look(SudokuTeacher.NOTES_KEY, dims = false), glow, pulse, corner = 10f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (notesMode) Color(accent) else scheme.surface)
                            .clickable(enabled = interactive) { notesMode = !notesMode }
                            .semantics { contentDescription = if (notesMode) "Notes on" else "Notes off" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = if (notesMode) Color.White else scheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
