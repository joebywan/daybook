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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.DigitKeyAction
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialPath
import com.joebywan.daybook.core.applyDigitKey
import com.joebywan.daybook.core.placementDeduction
import com.joebywan.daybook.ui.BoardGlyph
import com.joebywan.daybook.ui.lineUnits
import com.joebywan.daybook.ui.rememberSettled
import com.joebywan.daybook.ui.style
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.ArcadeFill
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.classicLook
import com.joebywan.daybook.ui.wall

/** The share of a labelled square's height its cage's label takes from the top, which the notes and the digit leave clear. */
private const val LABEL_BAND = 0.34f

/** How far in from a square's top-left corner a label sits, as a share of the square: clear of the wall's half. */
private const val LABEL_INSET = 0.08f

/** Empties [cell] and its notes, as the clear key does; `this` if there was nothing to empty. */
fun ReckonState.clearCell(cell: Int): ReckonState = when {
    isGiven(cell) -> this
    cells[cell] != 0 -> withCell(cell, 0)
    notes[cell] != 0 -> copy(notes = notes.toMutableList().also { it[cell] = 0 }, moves = moves + 1)
    else -> this
}

/** The board after [action] (from `digitKeyAction(key, size)`), or null when it changes nothing; see [applyDigitKey]. */
fun ReckonState.applyKey(action: DigitKeyAction, notesMode: Boolean): ReckonState? =
    applyDigitKey(action, selected, size, { select(it) }, { cell, d -> enter(cell, d, notesMode) }, { clearCell(it) })

/**
 * Reckon: arithmetic cages on a Latin square. The grid, the pad, the notes and the keys are Sudoku's own
 * ([SudokuGridBoard], at size n with no boxes); this draws the cages' walls and labels over it. Rules, count and generator
 * are [ReckonLogic], the hints [ReckonTeacher]. `id = "reckon"` is hashed into every seed: never rename it.
 */
object Reckon : PuzzleType {

    override val id = "reckon"
    override val displayName = "Reckon"
    override val tagline = "Make every cage's number, 1 to 6 once per line"

    /** Shared with Inequality on purpose (the set is full; `AccentSpacingTest.SHARED`): both are Latin squares, far apart on the home grid. */
    override val accent = 0xFF1E76D8
    override val rules = listOf(
        "Fill every square with a digit from 1 to the size of the grid: no repeat in a row or a column.",
        "Bold lines mark cages. The number and sign in a cage's corner is what its digits make: added (+), " +
            "multiplied (×), or in a cage of two, the bigger less (−) or over (÷) the smaller.",
        "A cage may repeat a digit when the two squares share no row or column.",
        "A cage of one square is given.",
        "Tap a square, then tap a digit. Tap the digit again to clear it.",
        "Or pick the digit first: with no square selected, tap a digit, then every square it goes in. Tap the selected square to let go of it, and the digit again to finish.",
        "Tap the pencil to take notes: while it is lit, a digit is pencilled small in the square instead of placed.",
        "A repeat, or a cage that can no longer make its number, turns red after a moment.",
    )

    override val keyboardHelp = listOf("Arrows move, 1-6 place a digit (or a note in pencil mode), Backspace clears.")

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        ReckonLogic.generateVerified(seed, difficulty) ?: ReckonLogic.lastResort(difficulty)

    // ---- teaching --------------------------------------------------------------------------

    override fun teach(state: PuzzleState): Deduction? {
        val s = state as ReckonState
        val step = ReckonTeacher.teach(s) ?: return null
        return placementDeduction(
            s, step,
            mistake = step.technique == ReckonTeacher.MISTAKE,
            fallback = step.technique == ReckonTeacher.FALLBACK,
            valueAt = { t, cell -> t.cells[cell] },
            place = { t, cell, digit -> t.withCell(cell, digit).select(cell) },
        )
    }

    /** A cage in a line or a pair on Hard, a what-if on Expert (the deepest the walk from the givens took); null below. */
    override fun neededTechnique(state: PuzzleState): String? {
        val s = state as ReckonState
        val given = List(s.size * s.size) { if (s.isGiven(it)) s.cells[it] else 0 }
        val most = ReckonTeacher.depth(s.size, given, s.cageOf, s.ops, s.targets) ?: return null
        return if (most >= ReckonTeacher.rung(ReckonTeacher.CAGE_LINE)) ReckonTeacher.TECHNIQUES[most] else null
    }

    // ---- walkthrough -----------------------------------------------------------------------

    /**
     * A 4x4 with one answer (`ReckonTutorialTest` enumerates it): an L of 18× (3·2·3, the two 3s in corners that share no
     * line), 4÷, 2÷, a given 4, 3−, 1−, 7+ and 6×.
     */
    internal val TUTORIAL_SOLUTION = listOf(
        3, 4, 1, 2,
        2, 3, 4, 1,
        1, 2, 3, 4,
        4, 1, 2, 3,
    )
    internal val TUTORIAL_CAGES = listOf(
        0, 1, 1, 2,
        0, 0, 3, 2,
        4, 5, 6, 6,
        4, 5, 7, 7,
    )
    internal const val TUTORIAL_OPS = "*//=--+*"
    internal val TUTORIAL_TARGETS = listOf(18, 4, 2, 4, 3, 1, 7, 6)

    /** The 7+'s 4: its other square sits under the given 4. */
    internal const val TUTORIAL_PLUS = 11

    /** The 3−'s 1: its row now has the 4. */
    internal const val TUTORIAL_MINUS = 8

    /** The bend of the 18× L: a 2, between the two 3s. */
    internal const val TUTORIAL_BEND = 4

    /** Where the wrong 4 goes: above the given 4, in its column. Then a 1 is pencilled there (4÷ leaves it 1 or 4). */
    internal const val TUTORIAL_WRONG = 2
    internal const val TUTORIAL_NOTED = 1

    internal fun tutorialBoard(entered: Map<Int, Int> = emptyMap(), selected: Int? = null, noted: Map<Int, Int> = emptyMap()): ReckonState =
        ReckonState(
            size = 4,
            cells = TUTORIAL_SOLUTION.indices.map { i -> if (TUTORIAL_OPS[TUTORIAL_CAGES[i]] == ReckonLogic.GIVEN) TUTORIAL_SOLUTION[i] else entered[i] ?: 0 },
            solution = TUTORIAL_SOLUTION,
            cageOf = TUTORIAL_CAGES,
            ops = TUTORIAL_OPS,
            targets = TUTORIAL_TARGETS,
            notes = List(16) { i -> noted[i]?.let { 1 shl (it - 1) } ?: 0 },
            selected = selected,
        )

    /** Tap [cell], then [digit]: a frame's two states, the glow walking from the square to the key. */
    private fun placing(from: ReckonState, cell: Int, digit: Int): TutorialPath {
        val to = from.withCell(cell, digit)
        return TutorialPath(
            { it is ReckonState && it.cells == from.cells && it.selected == cell },
            { it is ReckonState && it.cells == to.cells },
        )
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val pad = { d: Int -> ReckonTeacher.pad(4, d) }
        val cageSquares = ReckonLogic.cages(TUTORIAL_CAGES)
        fun cage(i: Int) = cageSquares[TUTORIAL_CAGES[i]].toSet()
        val start = tutorialBoard()
        val p1 = mapOf(TUTORIAL_PLUS to 4)
        val p2 = p1 + (TUTORIAL_MINUS to 1)
        val p3 = p2 + (TUTORIAL_BEND to 2)
        val plus = placing(start, TUTORIAL_PLUS, 4)
        val minus = placing(tutorialBoard(p1), TUTORIAL_MINUS, 1)
        val bend = placing(tutorialBoard(p2), TUTORIAL_BEND, 2)
        val done = tutorialBoard(p3)
        val clash = TutorialPath(
            { it is ReckonState && it.cells == done.cells && it.selected == TUTORIAL_WRONG },
            { it is ReckonState && it.cells == tutorialBoard(p3 + (TUTORIAL_WRONG to 4)).cells },
            // Back where it started, but by way of the 4: only a move counted since says so.
            { it is ReckonState && it.cells == done.cells && it.moves > 0 },
        )
        fun glowing(path: TutorialPath, cell: Int, digit: Int, soft: Set<Int>) = { s: PuzzleState ->
            BoardHighlight(strong = setOf(if (path.stage(s) < 0) cell else pad(digit)), soft = soft)
        }
        listOf(
            TutorialFrame(
                state = start,
                caption = "Fill the grid with 1 to 4, once in every row and column. Bold lines mark cages; " +
                    "the bold 4 is a cage of one, given.",
                highlight = BoardHighlight(strong = setOf(6)),
            ),
            TutorialFrame(
                state = start,
                caption = "A cage's corner says what its digits make: 7+ adds up to 7, 18× multiplies to 18. " +
                    "3− and 4÷ are the bigger digit less, or over, the smaller.",
                highlight = BoardHighlight(strong = cage(TUTORIAL_PLUS) + cage(TUTORIAL_BEND) + cage(TUTORIAL_MINUS) + cage(1)),
            ),
            TutorialFrame(
                state = start,
                caption = "Two squares making 7+ can only be 3 and 4. The 4 can't sit under the given 4, " +
                    "so it goes here. Tap the square, then 4 below.",
                glow = glowing(plus, TUTORIAL_PLUS, 4, cage(TUTORIAL_PLUS) + 6),
                steps = plus.steps,
                retry = "Tap the glowing square, then the 4.",
                done = "The 3 goes beside it.",
            ),
            TutorialFrame(
                state = tutorialBoard(p1, selected = TUTORIAL_PLUS),
                caption = "3− can only be 4 less 1, either way round. This row has its 4 now, so this square " +
                    "is the 1. Tap it, then 1.",
                glow = glowing(minus, TUTORIAL_MINUS, 1, cage(TUTORIAL_MINUS) + TUTORIAL_PLUS),
                steps = minus.steps,
                retry = "Tap the glowing square, then the 1.",
                done = "And the 4 goes under it.",
            ),
            TutorialFrame(
                state = tutorialBoard(p2, selected = TUTORIAL_MINUS),
                caption = "18× from 1 to 4 is only 3 × 2 × 3. A cage may repeat a digit where its squares share " +
                    "no row or column: the 3s take the two ends. Put 2 at the bend.",
                glow = glowing(bend, TUTORIAL_BEND, 2, cage(TUTORIAL_BEND)),
                steps = bend.steps,
                retry = "Tap the glowing square, then the 2.",
                done = "The two 3s go at the ends.",
            ),
            TutorialFrame(
                state = tutorialBoard(p3, selected = TUTORIAL_BEND),
                caption = "A digit that breaks a rule turns red after a moment. Put a 4 in the glowing square: " +
                    "its column has the given 4. Then tap 4 again to clear it.",
                glow = { s ->
                    when (clash.stage(s)) {
                        -1 -> BoardHighlight(strong = setOf(TUTORIAL_WRONG), soft = setOf(6))
                        0 -> BoardHighlight(strong = setOf(pad(4)), soft = setOf(6))
                        else -> BoardHighlight(strong = setOf(TUTORIAL_WRONG, pad(4)), soft = setOf(6), warning = true)
                    }
                },
                steps = clash.steps,
                retry = "Tap the glowing square, then the 4, then the 4 again.",
                done = "Cleared. Tapping the digit again takes it out.",
            ),
            TutorialFrame(
                state = tutorialBoard(p3, selected = TUTORIAL_WRONG),
                caption = "Not sure of a digit? Tap the pencil, then a digit, to jot it small. 4÷ leaves this " +
                    "square 1 or 4, and its column has the 4: pencil a 1 here.",
                highlight = BoardHighlight(strong = setOf(TUTORIAL_WRONG, ReckonTeacher.pencil(4), pad(TUTORIAL_NOTED)), soft = cage(TUTORIAL_WRONG)),
                accepts = { next ->
                    next is ReckonState && next.cells == done.cells &&
                        next.notes == tutorialBoard(p3, noted = mapOf(TUTORIAL_WRONG to TUTORIAL_NOTED)).notes
                },
                retry = "Tap the glowing pencil, then the 1.",
                done = "Penciled. Tap the pencil again to go back to placing digits.",
            ),
            TutorialFrame(
                state = tutorialBoard(p3, selected = TUTORIAL_WRONG, noted = mapOf(TUTORIAL_WRONG to TUTORIAL_NOTED)),
                caption = "Your turn: finish the board. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. That's all there is to it.",
            ),
        )
    }

    override fun withoutSelection(state: PuzzleState): PuzzleState =
        (state as ReckonState).let { if (it.selected == null) it else it.copy(selected = null) }

    // ---- drawing ---------------------------------------------------------------------------

    /** The first square of each cage of more than one square, in reading order: where its label goes. */
    internal fun labelled(cageOf: List<Int>, ops: String): Set<Int> {
        val seen = BooleanArray(ops.length)
        return cageOf.indices.filter { i -> !seen[cageOf[i]].also { seen[cageOf[i]] = true } && ops[cageOf[i]] != ReckonLogic.GIVEN }.toSet()
    }

    /**
     * Heavy walls between cages of a grid of [n] squares of [step] pixels, and round it where [frame], clipped so a wall's
     * end never pokes out.
     */
    private fun DrawScope.walls(n: Int, cageOf: List<Int>, step: Float, colour: Color, width: Float, frame: Boolean = true) {
        clipRect(0f, 0f, step * n, step * n) {
            for (i in 0 until n * n) {
                val x = i % n * step
                val y = i / n * step
                if (i % n < n - 1 && cageOf[i] != cageOf[i + 1]) drawLine(colour, Offset(x + step, y), Offset(x + step, y + step), width, StrokeCap.Square)
                if (i / n < n - 1 && cageOf[i] != cageOf[i + n]) drawLine(colour, Offset(x, y + step), Offset(x + step, y + step), width, StrokeCap.Square)
            }
            if (frame) drawRect(colour, style = Stroke(width * 2), topLeft = Offset.Zero, size = Size(step * n, step * n))
        }
    }

    /** The lines between squares of one cage, over Arcade's frame. */
    private fun DrawScope.seams(n: Int, cageOf: List<Int>, step: Float, colour: Color, width: Float) {
        for (i in 0 until n * n) {
            val x = i % n * step
            val y = i / n * step
            if (i % n < n - 1 && cageOf[i] == cageOf[i + 1]) drawLine(colour, Offset(x + step, y), Offset(x + step, y + step), width)
            if (i / n < n - 1 && cageOf[i] == cageOf[i + n]) drawLine(colour, Offset(x, y + step), Offset(x + step, y + step), width)
        }
    }

    /** Each labelled square's label in its top-left corner, on a chip of [chip] where [chipped] (a filled square's colour needs one). */
    private fun DrawScope.labels(
        n: Int, s: ReckonState, at: Set<Int>, step: Float, measurer: TextMeasurer, style: TextStyle, chip: Color, chipped: (Int) -> Boolean,
    ) {
        for (i in at) {
            val c = s.cageOf[i]
            val laid = measurer.measure(ReckonLogic.label(s.ops[c], s.targets[c]), style, maxLines = 1)
            val o = Offset(i % n * step + step * LABEL_INSET, i / n * step + step * LABEL_INSET * 0.5f)
            if (chipped(i)) drawRoundRect(chip, o, Size(laid.size.width.toFloat(), laid.lastBaseline + step * 0.02f), CornerRadius(step * 0.06f))
            drawText(laid, topLeft = o)
        }
    }

    // ---- home-grid motif -------------------------------------------------------------------

    /** The walkthrough's top-left 3x3: the 18× L (3, 2, 3), 4÷, 7+ and 1− (a legal crop; cages of the crop only). */
    private val PREVIEW_CAGES = listOf(0, 1, 1, 0, 0, 2, 3, 3, 2)
    private val PREVIEW_DIGITS = listOf(3, 0, 0, 2, 3, 0, 0, 0, 0)
    private val PREVIEW_OPS = "*/+-"
    private val PREVIEW_TARGETS = listOf(18, 4, 7, 1)

    @Composable
    override fun Preview(modifier: Modifier) {
        val a = ArcadeBoard.current
        val classic = classicLook()
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val preview = remember {
            ReckonState(3, PREVIEW_DIGITS, PREVIEW_DIGITS, PREVIEW_CAGES, PREVIEW_OPS, PREVIEW_TARGETS)
        }
        val at = remember { labelled(PREVIEW_CAGES, PREVIEW_OPS) }
        BoxWithConstraints(modifier) {
            val cell = minOf(maxWidth, maxHeight) / 3
            for (i in 0 until 9) {
                val fill = if (classic) ArcadeFill(scheme.surfaceVariant, scheme.surfaceVariant, scheme.onSurface)
                else if (PREVIEW_DIGITS[i] != 0) a.control else ArcadeFill(a.square, a.square, a.squareInk)
                Box(
                    Modifier
                        .padding(start = cell * (i % 3), top = cell * (i / 3))
                        .size(cell)
                        .padding(1.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(fill.brush),
                    contentAlignment = Alignment.Center,
                ) {
                    if (PREVIEW_DIGITS[i] != 0) {
                        Text(
                            PREVIEW_DIGITS[i].toString(),
                            modifier = Modifier.padding(top = cell * LABEL_BAND * 0.7f),
                            style = BoardGlyph.ENTERED.style(cell * 0.85f),
                            color = fill.ink,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            val label = BoardGlyph.CORNER.style(cell).copy(color = if (classic) scheme.onSurface else a.squareInk)
            val wallInk = if (classic) scheme.wall else a.frame
            Canvas(Modifier.size(cell * 3)) {
                val px = size.width / 3f
                walls(3, PREVIEW_CAGES, px, wallInk, px * 0.07f)
                labels(3, preview, at, px, measurer, label, a.square) { !classic && PREVIEW_DIGITS[it] != 0 }
            }
        }
    }

    // ---- the board ---------------------------------------------------------------------------

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as ReckonState
        val n = s.size
        val measurer = rememberTextMeasurer()
        val at = remember(s.cageOf, s.ops) { labelled(s.cageOf, s.ops) }
        // Sudoku's grid draws a SudokuState: the same digits, notes and selection, a given wherever a cage is one square.
        val grid = remember(s) { SudokuState(List(n * n) { s.isGiven(it) }, s.cells, s.solution, s.selected, s.moves, s.notes) }
        val lines = remember(n) { lineUnits(n, n) }
        val units = remember(s.cageOf) { lines + ReckonLogic.cages(s.cageOf).map { it.toIntArray() } }
        // A break shows a second after the board last changed (a player passing through one is not shouted at); a repair at once.
        val live = s.conflicts()
        val shown = live intersect rememberSettled(live, reset = s.cageOf, change = s.cells)
        SudokuGridBoard(
            s = grid,
            conflicts = shown,
            interactive = interactive,
            accent = accent,
            onSelect = { onState(s.select(it)) },
            onDeselect = { onState(withoutSelection(s)) },
            onEnter = { cell, digit, notesMode ->
                val next = s.enter(cell, digit, notesMode)
                if (next !== s) onState(next)
            },
            onKey = { action, notesMode -> s.applyKey(action, notesMode)?.let(onState) },
            topBand = { if (it in at) LABEL_BAND else 0f },
            // The rows and columns, then the cages: a line is finished when it holds 1..n, a cage when it makes its number.
            units = units,
            complete = { u ->
                val sq = units[u]
                if (u < lines.size) sq.map { s.cells[it] }.toSet() == (1..n).toSet()
                else {
                    val c = s.cageOf[sq[0]]
                    sq.all { s.cells[it] != 0 } && ReckonLogic.makes(s.ops[c], s.targets[c], sq.map { s.cells[it] })
                }
            },
            overlay = { cell -> CageOverlay(s, at, shown, cell, measurer) },
            n = n,
            wall = { p, q -> s.cageOf[p] != s.cageOf[q] },
            padKey = { ReckonTeacher.pad(n, it) },
            notesKey = ReckonTeacher.pencil(n),
            wallGap = 2.dp,
        )
    }

    @Composable
    private fun BoxScope.CageOverlay(s: ReckonState, at: Set<Int>, wrong: Set<Int>, cell: Dp, measurer: TextMeasurer) {
        val a = ArcadeBoard.current
        val classic = classicLook()
        val scheme = MaterialTheme.colorScheme
        val label = BoardGlyph.CORNER.style(cell).copy(color = if (classic) scheme.onSurface.copy(alpha = 0.85f) else a.squareInk)
        // Arcade's walls sit in its frame's wider gaps between cages, in Mosaic's wall ink (the dark frame alone hardly parts them).
        val arcadeWall = if (BoardHues.isDark(scheme)) Color.White.copy(alpha = 0.8f) else a.frame
        // Not a pointer target: taps fall through to the squares underneath.
        Canvas(Modifier.fillMaxSize()) {
            val px = size.width / s.size
            if (classic) walls(s.size, s.cageOf, px, scheme.wall, 3.dp.toPx())
            else {
                // Inside a cage the frame's gap is eased to a hairline, so the walls are the only heavy lines.
                seams(s.size, s.cageOf, px, lerp(a.frame, a.square, 0.65f), 1.dp.toPx())
                walls(s.size, s.cageOf, px, arcadeWall, 3.dp.toPx(), frame = false)
            }
            labels(s.size, s, at, px, measurer, label, a.square) { !classic && (s.cells[it] != 0 || s.selected == it || it in wrong) }
        }
    }
}
