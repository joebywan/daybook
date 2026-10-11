package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.ui.theme.BoardHues
import kotlinx.serialization.Serializable

/**
 * A Sudoku grid ([grid], which carries the digits, the answer, the selection, the pencil marks and the move count)
 * with cages over it: [cageOf] gives each square its cage and [sums] each cage's total. See [CageLogic].
 */
@Serializable
data class CageState(
    val grid: SudokuState,
    val cageOf: List<Int>,
    val sums: List<Int>,
) : PuzzleState {
    override val moves: Int get() = grid.moves

    /** The rules, not [SudokuState.solution]: a finished Sudoku grid whose cages add up and repeat nothing. */
    override val solved: Boolean get() = CageLogic.isSolved(grid.cells, cageOf, sums)

    val cells: List<Int> get() = grid.cells

    /** Squares the board already shows to be wrong: Sudoku's clashes and the cages' own. */
    fun conflicts(): Set<Int> = grid.conflicts() + CageLogic.cageConflicts(grid.cells, cageOf, sums)

    fun select(index: Int): CageState = copy(grid = grid.select(index))

    fun toggleNote(index: Int, digit: Int): CageState {
        val next = grid.toggleNote(index, digit)
        return if (next === grid) this else copy(grid = next)
    }

    /**
     * As [SudokuState.withCell], and the digit is struck from the pencil marks of the rest of its cage too,
     * in the same state, so one undo puts back the digit and every mark together. `this` if nothing changes.
     */
    fun withCell(index: Int, value: Int): CageState {
        val next = grid.withCell(index, value)
        if (next === grid) return this
        if (value == 0) return copy(grid = next)
        val bit = (1 shl (value - 1)).inv()
        val notes = next.notes.toMutableList()
        for (i in cageOf.indices) if (i != index && cageOf[i] == cageOf[index]) notes[i] = notes[i] and bit
        return copy(grid = if (notes == next.notes) next else next.copy(notes = notes))
    }

    /** What a key or a digit pad key does to [cell]: [SudokuState.enter]'s rule, with the cage's marks struck as well. */
    fun enter(cell: Int, digit: Int, notesMode: Boolean): CageState = when {
        notesMode -> toggleNote(cell, digit)
        else -> withCell(cell, if (grid.cells[cell] == digit) 0 else digit)
    }
}

/** The share of a square's height that a cage's total takes from the top of its first square. */
private const val LABEL_BAND = 0.27f

/**
 * Cage Sudoku — a Sudoku whose dashed cages must add up. The grid, the pad, the notes and the keys are
 * Sudoku's own ([SudokuGridBoard]); this adds the cages over it, their totals, and the logic that makes
 * them a puzzle ([CageLogic], [CageTeacher]). `id = "cages"` is hashed into every seed: never rename it.
 */
object Cages : PuzzleType {

    override val id = "cages"
    override val displayName = "Cage Sudoku"
    override val tagline = "Sudoku where dashed cages must add up"
    override val accent = 0xFF6E66CC
    override val rules = listOf(
        "Fill every cell with a digit from 1 to 9, as in Sudoku: no repeat in a row, a column or a 3x3 box.",
        "The dashed outlines are cages. The digits in a cage add up to the small number in its corner, " +
            "and a cage never repeats a digit.",
        "Tap a cell, then tap a digit. Tap the digit again to clear it.",
        "Tap the pencil to take notes: while it is lit, a digit is pencilled small in the cell " +
            "instead of placed.",
        "Clashing digits, and cages that are over their total or finished wrong, are shown in red.",
        "Every row, column and box adds up to 45. That can tell you what the cages leave out, or spill into.",
    )

    override val keyboardHelp = listOf(
        "Arrows move, 1-9 place a digit (or a note in pencil mode), Backspace clears.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        CageLogic.generateVerified(seed, difficulty) ?: CageLogic.lastResort()

    internal fun generateVerified(seed: Long, difficulty: Difficulty): CageState? =
        CageLogic.generateVerified(seed, difficulty)

    // ---- teaching --------------------------------------------------------------------------

    override fun teach(state: PuzzleState): Deduction? {
        val s = state as CageState
        val step = CageTeacher.teach(s) ?: return null
        val cell = step.cell
        val digit = step.digit
        val mistake = step.technique == CageTeacher.MISTAKE
        val wrong = s.cells[cell]
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = setOf(cell),
            mistake = mistake,
            fallback = step.technique == CageTeacher.FALLBACK,
            // One state, so one undo entry; the cell is selected so the player sees where it went.
            applyTo = { now ->
                val t = now as CageState
                if (t.cells[cell] == digit) t else t.withCell(cell, digit).select(cell)
            },
            reachedBy = { now ->
                val t = now as CageState
                // A mistake is dealt with once that digit is gone, whatever replaced it.
                if (mistake) t.cells[cell] != wrong else t.cells[cell] == digit
            },
        )
    }

    // ---- walkthrough ---------------------------------------------------------------------------

    /** Sudoku's own walkthrough grid and gaps, with a domino cage over every pair of neighbours. */
    internal val TUTORIAL_SOLUTION = Sudoku.TUTORIAL_SOLUTION
    internal val TUTORIAL_OPEN = Sudoku.TUTORIAL_OPEN

    /** The first gap: a cage of 11 beside a 7. */
    internal const val TUTORIAL_FIRST = 63

    /** Where the wrong 5 goes: the cage of 11 beside a 2 needs a 9, and 5 + 2 is only 7 (and a 5 already stands in its column). */
    internal const val TUTORIAL_WRONG = 42
    internal const val TUTORIAL_NOTED = 9

    internal fun tutorialBoard(
        entered: Map<Int, Int> = emptyMap(),
        selected: Int? = null,
        noted: Map<Int, Int> = emptyMap(),
    ): CageState {
        val cageOf = CageLogic.dominoes()
        val cells = TUTORIAL_SOLUTION.mapIndexed { i, v -> if (i in TUTORIAL_OPEN) entered[i] ?: 0 else v }
        return CageState(
            grid = SudokuState(
                givens = TUTORIAL_SOLUTION.indices.map { it !in TUTORIAL_OPEN },
                cells = cells,
                solution = TUTORIAL_SOLUTION,
                selected = selected,
                notes = List(81) { i -> noted[i]?.let { 1 shl (it - 1) } ?: 0 },
            ),
            cageOf = cageOf,
            sums = CageLogic.sumsOf(cageOf, TUTORIAL_SOLUTION),
        )
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val start = tutorialBoard()
        val cage = CageLogic.cageCells(start.cageOf)
        val firstCage = cage[start.cageOf[TUTORIAL_FIRST]].toSet()
        val wrongCage = cage[start.cageOf[TUTORIAL_WRONG]].toSet()
        val placed = mapOf(TUTORIAL_FIRST to 4)
        val wrong = tutorialBoard(placed + (TUTORIAL_WRONG to 5), selected = TUTORIAL_WRONG)
        listOf(
            TutorialFrame(
                state = start,
                caption = "Fill the grid as in Sudoku. The dashed outlines are cages: a cage's digits add up " +
                    "to the small number in its corner, and never repeat.",
                highlight = BoardHighlight(strong = firstCage),
            ),
            TutorialFrame(
                state = start,
                caption = "This cage totals 11 and already holds a 7. Tap the empty square to select it.",
                highlight = BoardHighlight(strong = setOf(TUTORIAL_FIRST), soft = firstCage),
                accepts = { next -> next is CageState && next.cells == start.cells && next.grid.selected == TUTORIAL_FIRST },
                retry = "Tap the glowing square.",
                done = "Selected.",
            ),
            TutorialFrame(
                state = tutorialBoard(selected = TUTORIAL_FIRST),
                caption = "11 take away 7 leaves 4. Tap 4 below.",
                highlight = BoardHighlight(strong = setOf(TUTORIAL_FIRST, SudokuTeacher.pad(4)), soft = firstCage),
                accepts = { next -> next is CageState && next.cells == tutorialBoard(placed).cells },
                retry = "Tap the 4 in the row of digits below.",
                done = "That cage is complete.",
            ),
            TutorialFrame(
                state = wrong,
                caption = "A mistake turns red. This cage totals 11 but 5 + 2 is only 7, and the 5 repeats in " +
                    "its column. It's selected, so tap 5 again to clear it.",
                highlight = BoardHighlight(
                    strong = setOf(TUTORIAL_WRONG, SudokuTeacher.pad(5)),
                    soft = wrongCage,
                    warning = true,
                ),
                accepts = { next -> next is CageState && next.cells == tutorialBoard(placed).cells },
                retry = "Tap the 5 in the row of digits below.",
                done = "Cleared.",
            ),
            TutorialFrame(
                state = tutorialBoard(placed, selected = TUTORIAL_WRONG),
                caption = "Not sure of a digit? Tap the pencil, then a digit, to jot it small. 11 take away the " +
                    "2 is 9: pencil a 9 here.",
                highlight = BoardHighlight(
                    strong = setOf(TUTORIAL_WRONG, SudokuTeacher.NOTES_KEY, SudokuTeacher.pad(TUTORIAL_NOTED)),
                    soft = wrongCage,
                ),
                accepts = { next ->
                    next is CageState && next.cells == tutorialBoard(placed).cells &&
                        next.grid.notes == tutorialBoard(placed, noted = mapOf(TUTORIAL_WRONG to TUTORIAL_NOTED)).grid.notes
                },
                retry = "Tap the pencil first so it lights up, then the 9.",
                done = "Penciled. Tap the pencil again to go back to placing digits.",
            ),
            TutorialFrame(
                state = tutorialBoard(placed, selected = TUTORIAL_WRONG, noted = mapOf(TUTORIAL_WRONG to TUTORIAL_NOTED)),
                caption = "Your turn: finish the board. Stuck? Hint shows you why, cage totals included. " +
                    "Bigger cages take a little more working out.",
                freePlay = true,
                done = "Solved. That's all there is to it.",
            ),
        )
    }

    // ---- drawing ------------------------------------------------------------------------------

    /**
     * Dashed outlines for the cages of an [n] x [n] grid of [cell]-pixel squares, drawn just inside each cage's
     * edge. Each square draws the sides that face outside its cage; at a corner a line stops short (a bend of the
     * cage), carries on past the edge (a notch in it) or runs straight into the next square's, so the outline of an
     * L or a bar closes up without a gap.
     */
    internal fun DrawScope.drawCages(cageOf: List<Int>, n: Int, cell: Float, ink: Color, stroke: Float) {
        val inset = cell * 0.09f
        val dash = PathEffect.dashPathEffect(floatArrayOf(cell * 0.13f, cell * 0.09f))
        fun inCage(r: Int, c: Int, k: Int) = r in 0 until n && c in 0 until n && cageOf[r * n + c] == k
        fun line(x0: Float, y0: Float, x1: Float, y1: Float) =
            drawLine(ink, Offset(x0, y0), Offset(x1, y1), strokeWidth = stroke, pathEffect = dash)
        for (r in 0 until n) for (c in 0 until n) {
            val k = cageOf[r * n + c]
            val left = c * cell
            val top = r * cell
            val right = left + cell
            val bottom = top + cell
            // How far a side's end moves past the square's edge: in if the cage bends, out if it notches, 0 if it runs on.
            fun ext(neighbour: Boolean, diagonal: Boolean) = if (!neighbour) -inset else if (diagonal) inset else 0f
            if (!inCage(r - 1, c, k)) {
                val a = left - ext(inCage(r, c - 1, k), inCage(r - 1, c - 1, k))
                val b = right + ext(inCage(r, c + 1, k), inCage(r - 1, c + 1, k))
                line(a, top + inset, b, top + inset)
            }
            if (!inCage(r + 1, c, k)) {
                val a = left - ext(inCage(r, c - 1, k), inCage(r + 1, c - 1, k))
                val b = right + ext(inCage(r, c + 1, k), inCage(r + 1, c + 1, k))
                line(a, bottom - inset, b, bottom - inset)
            }
            if (!inCage(r, c - 1, k)) {
                val a = top - ext(inCage(r - 1, c, k), inCage(r - 1, c - 1, k))
                val b = bottom + ext(inCage(r + 1, c, k), inCage(r + 1, c - 1, k))
                line(left + inset, a, left + inset, b)
            }
            if (!inCage(r, c + 1, k)) {
                val a = top - ext(inCage(r - 1, c, k), inCage(r - 1, c + 1, k))
                val b = bottom + ext(inCage(r + 1, c, k), inCage(r + 1, c + 1, k))
                line(right - inset, a, right - inset, b)
            }
        }
    }

    /** Each cage's total in the top-left corner of its first square. */
    private fun DrawScope.drawTotals(
        cageOf: List<Int>, sums: List<Int>, n: Int, cell: Float, measurer: TextMeasurer, style: TextStyle,
    ) {
        val seen = BooleanArray(sums.size)
        for (i in cageOf.indices) {
            val k = cageOf[i]
            if (seen[k]) continue
            seen[k] = true
            val laid = measurer.measure(sums[k].toString(), style, maxLines = 1)
            drawText(laid, topLeft = Offset(i % n * cell + cell * 0.13f, i / n * cell + cell * 0.10f))
        }
    }

    // ---- home-grid motif ----------------------------------------------------------------------

    /**
     * One hand-picked 3x3 box, as Sudoku's tile is, with its squares in dashed cages: 5+3, 4+2, 6+7, 1+9 and
     * a lone 8. A legal crop of a board (the box holds 1-9 once). Fixed, so the tile never changes.
     */
    private val PREVIEW_CAGES = listOf(0, 0, 1, 2, 2, 1, 3, 3, 4)
    private val PREVIEW_SUMS = listOf(8, 6, 13, 10, 8)
    private val PREVIEW_DIGITS = listOf(0, 0, 0, 0, 7, 0, 0, 0, 8)

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        val (hueA, hueB) = BoardHues.HUE_PAIRS[0]
        val measurer = rememberTextMeasurer()
        BoxWithConstraints(modifier) {
            val cell = minOf(maxWidth, maxHeight) / 3
            for (r in 0 until 3) for (c in 0 until 3) {
                val i = r * 3 + c
                Box(
                    Modifier
                        .padding(start = cell * c, top = cell * r)
                        .size(cell)
                        .padding(1.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(scheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (PREVIEW_DIGITS[i] != 0) {
                        Text(
                            text = PREVIEW_DIGITS[i].toString(),
                            fontSize = (cell.value * 0.5f).sp,
                            fontWeight = FontWeight.Normal,
                            color = BoardHues.ink(hueB, dark),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            val ink = BoardHues.ink(hueA, dark)
            val label = TextStyle(fontSize = (cell.value * 0.26f).sp, fontWeight = FontWeight.Medium, color = scheme.onSurface)
            Canvas(Modifier.fillMaxSize()) {
                val px = size.width.coerceAtMost(size.height) / 3f
                drawCages(PREVIEW_CAGES, 3, px, ink, stroke = px * 0.045f)
                drawTotals(PREVIEW_CAGES, PREVIEW_SUMS, 3, px, measurer, label)
            }
        }
    }

    override fun withoutSelection(state: PuzzleState): PuzzleState =
        (state as CageState).let { if (it.grid.selected == null) it else it.copy(grid = it.grid.copy(selected = null)) }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as CageState
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        val seed = s.grid.solution.hashCode()
        val (hueA, _) = BoardHues.pair(seed)
        val measurer = rememberTextMeasurer()
        // The square a cage's total sits in: the first of each, in reading order.
        val labelled = remember(s.cageOf) {
            val seen = BooleanArray(s.sums.size)
            s.cageOf.indices.filter { i -> !seen[s.cageOf[i]].also { seen[s.cageOf[i]] = true } }.toSet()
        }
        SudokuGridBoard(
            s = s.grid,
            conflicts = s.conflicts(),
            hueSeed = seed,
            interactive = interactive,
            onSelect = { onState(s.select(it)) },
            onEnter = { cell, digit, notesMode ->
                val next = s.enter(cell, digit, notesMode)
                if (next !== s) onState(next)
            },
            onKey = { action, notesMode -> s.applyKey(action, notesMode)?.let(onState) },
            topBand = { if (it in labelled) LABEL_BAND else 0f },
            overlay = { cell -> CageOverlay(s, cell, BoardHues.ink(hueA, dark), scheme.onSurface.copy(alpha = 0.85f), measurer) },
        )
    }

    @Composable
    private fun BoxScope.CageOverlay(s: CageState, cell: Dp, ink: Color, labelInk: Color, measurer: TextMeasurer) {
        val label = TextStyle(fontSize = (cell.value * 0.26f).sp, fontWeight = FontWeight.Medium, color = labelInk)
        // Not a pointer target: taps fall through to the squares underneath.
        Canvas(Modifier.fillMaxSize()) {
            val px = size.width / 9f
            drawCages(s.cageOf, 9, px, ink, stroke = 1.5.dp.toPx())
            drawTotals(s.cageOf, s.sums, 9, px, measurer, label)
        }
    }
}
