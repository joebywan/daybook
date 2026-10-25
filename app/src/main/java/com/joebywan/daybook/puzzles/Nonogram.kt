package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.ui.rememberHintPulse
import com.joebywan.daybook.ui.lineUnits
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.feelSquare
import com.joebywan.daybook.ui.rememberMoveFeel
import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.PenKeyAction
import com.joebywan.daybook.core.penKeyAction
import com.joebywan.daybook.ui.Sweep
import com.joebywan.daybook.ui.swept
import com.joebywan.daybook.ui.penKeys
import com.joebywan.daybook.ui.penGrid
import com.joebywan.daybook.ui.PenRow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Rect
import com.joebywan.daybook.ui.hintRims
import com.joebywan.daybook.ui.squareRim
import com.joebywan.daybook.ui.rememberSettledFlags
import com.joebywan.daybook.ui.HintGlow

/** Each board draws its filled squares as a diagonal blend of two colours; the pair is the board's own. */
private val PALETTES = listOf(
    0xFF2E9E8F to 0xFFE0A030, 0xFF3E7CB1 to 0xFFD0507F, 0xFFE0703F to 0xFF7A52C8,
    0xFF3FA55A to 0xFF2F9CC8, 0xFFD6A026 to 0xFFC0453F, 0xFF8A55C8 to 0xFF30B0A0,
    0xFFCF4F7A to 0xFF3E7CB1,
)

/** F fills, X crosses; the other keys are every pen board's ([penKeyAction]). */
private val NONOGRAM_KEYS = mapOf(Key.F to NonogramLogic.FILLED, Key.X to NonogramLogic.CROSSED)

/** What a physical key asks of a Nonogram board. */
internal fun nonogramKeyAction(key: Key, shift: Boolean): PenKeyAction? = penKeyAction(key, shift, NONOGRAM_KEYS)

/**
 * Nonogram: numbers beside a grid say how long the runs of filled squares are in each row and
 * column; fill the squares that satisfy all of them and a picture appears.
 *
 * Every board is dealt only if it can be finished by looking at one row or column at a time
 * ([NonogramLogic.generateVerified]), so nobody ever has to guess, and the hints teach that
 * line-by-line reasoning ([NonogramTeacher]).
 *
 * Tiers differ in size alone (5x5, 10x10, 15x15): the same line logic, a longer way to carry it.
 */
object Nonogram : PuzzleType {

    override val id = "nonogram"
    override val displayName = "Nonogram"
    override val tagline = "Numbers reveal the picture"
    override val accent = 0xFF3E7CB1

    override val rules = listOf(
        "The numbers beside a row, or above a column, give the lengths of its runs of filled squares, in order.",
        "Two runs always have at least one empty square between them.",
        "Pick Fill or Cross under the board and tap squares. Drag along a row or column to sweep several at once.",
        "A cross just means \"empty\": it is a note to yourself and never counts against you.",
        "Solved when every row and column shows its numbers. Every board can be worked out one line at a time, with no guessing.",
    )

    override val keyboardHelp = listOf(
        "Arrows move, F fills, X crosses, Space cycles, Backspace clears; Shift+arrow carries a mark along.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        NonogramLogic.generateVerified(seed, difficulty) ?: NonogramLogic.lastResort(difficulty)

    // ---- the walkthrough ----------------------------------------------------------------------

    /** A heart, five wide: two rows are full-width, so the first lesson is a plain sweep. */
    internal const val TUTORIAL_PICTURE =
        "01010" +
            "11111" +
            "11111" +
            "01110" +
            "00100"

    private const val TUTORIAL_N = 5

    private fun tutorialBoard(cells: String = ".".repeat(TUTORIAL_N * TUTORIAL_N)) =
        NonogramState(TUTORIAL_N, TUTORIAL_N, TUTORIAL_PICTURE, cells)

    /** [base] with [marks] (cell to mark) written over it. */
    private fun marked(base: NonogramState, marks: Map<Int, Char>): NonogramState {
        val next = base.cells.toCharArray()
        marks.forEach { (i, c) -> next[i] = c }
        return base.copy(cells = next.concatToString())
    }

    /**
     * Accepts exactly [base] with [changes] made and nothing else. Strict on purpose, as Kings'
     * is: each frame's board is written for the one before it.
     */
    private fun only(base: NonogramState, changes: Map<Int, Char>): (PuzzleState) -> Boolean = { next ->
        next is NonogramState && next.cells == marked(base, changes).cells
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val rowIndex = { r: Int -> NonogramLogic.rowClueIndex(TUTORIAL_N, TUTORIAL_N, r) }
        val colIndex = { c: Int -> NonogramLogic.colClueIndex(TUTORIAL_N, TUTORIAL_N, c) }
        val solved = tutorialBoard(TUTORIAL_PICTURE.map { if (it == '1') NonogramLogic.FILLED else NonogramLogic.UNMARKED }
            .joinToString(""))
        val empty = tutorialBoard()
        val row2 = (5..9).toList()
        val row3 = (10..14).toList()
        val afterRow3 = marked(empty, (row2 + row3).associateWith { NonogramLogic.FILLED })
        val afterMiddle = marked(afterRow3, mapOf(17 to NonogramLogic.FILLED))
        val afterCross = marked(afterMiddle, mapOf(20 to NonogramLogic.CROSSED))
        listOf(
            TutorialFrame(
                state = solved,
                caption = "Each number is a run of filled squares in that row or column, in order. " +
                    "Row 1 reads \"1 1\": a single square, a gap, then another single square.",
                highlight = BoardHighlight(strong = setOf(1, 3, rowIndex(0))),
            ),
            TutorialFrame(
                state = empty,
                caption = "Rows 2 and 3 read 5, and the board is five squares wide, so every square in them is " +
                    "filled. Drag along each row to fill it in one sweep.",
                // The rows still to sweep glow.
                glow = { b ->
                    val cells = (b as NonogramState).cells
                    BoardHighlight(strong = listOf(row2, row3).filter { r -> r.any { cells[it] != NonogramLogic.FILLED } }
                        .flatMap { r -> r + rowIndex(r.first() / TUTORIAL_N) }.toSet())
                },
                steps = { next ->
                    when {
                        only(empty, (row2 + row3).associateWith { NonogramLogic.FILLED })(next) -> TutorialStep.DONE
                        only(empty, row2.associateWith { NonogramLogic.FILLED })(next) ||
                            only(empty, row3.associateWith { NonogramLogic.FILLED })(next) -> TutorialStep.PROGRESS
                        else -> TutorialStep.REJECT
                    }
                },
                retry = "Drag along the whole glowing row in one sweep.",
                done = "Filled. A run as wide as its row has nowhere to move.",
            ),
            TutorialFrame(
                state = afterRow3,
                caption = "Row 4 reads 3 in five squares. Slide the 3 to the far left, then the far right: " +
                    "the middle square is covered both times, so it is filled. Tap it.",
                highlight = BoardHighlight(strong = setOf(17, rowIndex(3)), soft = (15..19).toSet() - 17),
                accepts = only(afterRow3, mapOf(17 to NonogramLogic.FILLED)),
                retry = "Tap the glowing square once.",
                done = "Filled. Sliding a run both ways shows which squares it cannot avoid.",
            ),
            TutorialFrame(
                state = afterMiddle,
                caption = "Column 1 reads 2, and rows 2 and 3 already give it that 2, so the rest of the column " +
                    "is empty. Choose Cross under the board, then tap the bottom square.",
                highlight = BoardHighlight(strong = setOf(20, colIndex(0)), soft = setOf(0, 5, 10, 15)),
                accepts = only(afterMiddle, mapOf(20 to NonogramLogic.CROSSED)),
                retry = "Switch to Cross under the board, then tap the glowing square.",
                done = "Crossed. A cross is only a note that the square is empty.",
            ),
            TutorialFrame(
                state = afterCross,
                caption = "Your turn: finish the picture. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. Every board works out one line at a time.",
            ),
        )
    }

    // ---- teaching -------------------------------------------------------------------------------

    /** A mistake to take back or a step to reason out; see [NonogramTeacher]. */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as NonogramState
        val step = NonogramTeacher.teach(s) ?: return null
        val before = s.cells
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = step.targets,
            mistake = step.technique == NonogramTeacher.MISTAKE,
            applyTo = { now -> applyStep(now as NonogramState, step) },
            reachedBy = { now ->
                val cells = (now as NonogramState).cells
                step.fills.all { cells[it] == NonogramLogic.FILLED } &&
                    step.crosses.all { cells[it] == NonogramLogic.CROSSED } &&
                    step.clears.all { cells[it] != before[it] }
            },
        )
    }

    /** "Show me": the step, made on the board as it is now. One state, so one undo entry. */
    private fun applyStep(s: NonogramState, step: NonogramTeacher.Step): NonogramState =
        s.paint(step.clears, NonogramLogic.UNMARKED).paint(step.fills, NonogramLogic.FILLED).paint(step.crosses, NonogramLogic.CROSSED)

    // ---- drawing --------------------------------------------------------------------------------

    /** How wide a clue number's slot is, and how tall, as fractions of a square. */
    private const val SLOT_W = 0.62f
    private const val SLOT_H = 0.56f

    /** Margin between the clues and the grid, and around the clue block, in squares. */
    private const val CLUE_GAP = 0.18f

    /**
     * Where everything sits in a [maxWidth] x [maxHeight] box, in squares ([u] pixels each): the clue
     * block to the left and above, then the grid. Sized from both axes (CLAUDE.md): a 15x15 board with
     * its clues is wider than a phone's screen allows by height alone on a short one, and the reverse
     * on a tall one. Shared by the board and the home motif so both draw the same thing.
     */
    private class Geometry(s: NonogramState, clues: List<List<Int>>, maxWidth: Dp, maxHeight: Dp, density: Density) {
        private val rowSlots = clues.take(s.height).maxOf { maxOf(it.size, 1) }
        private val colSlots = clues.drop(s.height).maxOf { maxOf(it.size, 1) }
        private val left = rowSlots * SLOT_W + CLUE_GAP
        private val top = colSlots * SLOT_H + CLUE_GAP
        private val across = s.width + rowSlots * SLOT_W + CLUE_GAP
        private val down = s.height + colSlots * SLOT_H + CLUE_GAP
        private val unit = minOf(maxWidth / across, maxHeight / down)
        val u = with(density) { unit.toPx() }
        val gridX = left * u
        val gridY = top * u

        /** The whole drawing, and the grid within it, in dp. */
        val box = Modifier.size(unit * across, unit * down)
        val grid = Modifier.offset(unit * left, unit * top).size(unit * s.width, unit * s.height)
    }

    /** The clue numbers of every row, then column, as measured text, one layout per number. */
    private fun measureClues(measurer: TextMeasurer, clues: List<List<Int>>, fontPx: Float, density: Density): List<List<TextLayoutResult>> {
        val style = TextStyle(fontSize = with(density) { fontPx.toSp() }, fontWeight = FontWeight.SemiBold)
        return clues.map { line -> (if (line.isEmpty()) listOf(0) else line).map { measurer.measure(it.toString(), style) } }
    }

    /** [text], [done] and [bad] are per line, rows then columns. */
    private fun DrawScope.drawBoard(
        s: NonogramState,
        g: Geometry,
        text: List<List<TextLayoutResult>>,
        done: BooleanArray,
        scheme: androidx.compose.material3.ColorScheme,
        palette: Pair<Color, Color>,
        bad: BooleanArray = BooleanArray(s.height + s.width),
        overlay: HintGlow = HintGlow(),
        feel: MoveFeel = MoveFeel.Still,
        accent: Color = Color.Unspecified,
    ) {
        val u = g.u
        val highlight = overlay.highlight
        val lit = !highlight.isEmpty
        val n = s.width * s.height

        fun named(i: Int) = i in highlight.strong || i in highlight.soft

        // Squares.
        for (r in 0 until s.height) {
            for (c in 0 until s.width) {
                val x = g.gridX + c * u
                val y = g.gridY + r * u
                drawRect(scheme.surface, Offset(x, y), Size(u, u))
                feelSquare(feel, r * s.width + c, Offset(x, y), Size(u, u), accent) {
                    when (s.cells[r * s.width + c]) {
                        NonogramLogic.FILLED -> drawRoundRect(
                            lerp(palette.first, palette.second, (r + c).toFloat() / (s.width + s.height - 2).coerceAtLeast(1)), Offset(x + u * 0.07f, y + u * 0.07f), Size(u * 0.86f, u * 0.86f), CornerRadius(u * 0.14f),
                        )
                        NonogramLogic.CROSSED -> {
                            val a = u * 0.30f
                            val stroke = maxOf(u * 0.09f, 1.2f)
                            val ink = scheme.onSurfaceVariant.copy(alpha = 0.8f)
                            drawLine(ink, Offset(x + a, y + a), Offset(x + u - a, y + u - a), stroke, StrokeCap.Round)
                            drawLine(ink, Offset(x + u - a, y + a), Offset(x + a, y + u - a), stroke, StrokeCap.Round)
                        }
                    }
                }
            }
        }

        // Grid lines, heavier every five squares so a 15-wide board can be counted.
        val thin = maxOf(u * 0.03f, 1f)
        fun gridLine(i: Int, from: Offset, to: Offset) {
            val heavy = i % 5 == 0
            drawLine(if (heavy) scheme.onSurfaceVariant.copy(alpha = 0.7f) else scheme.outline, from, to, if (heavy) thin * 2f else thin)
        }
        for (i in 0..s.width) gridLine(i, Offset(g.gridX + i * u, g.gridY), Offset(g.gridX + i * u, g.gridY + s.height * u))
        for (i in 0..s.height) gridLine(i, Offset(g.gridX, g.gridY + i * u), Offset(g.gridX + s.width * u, g.gridY + i * u))

        // Clues. A line that already shows its numbers fades, the way a ticked-off list does.
        // [centre] places a number by how many numbers come after it in the line.
        fun clues(line: Int, band: Offset, bandSize: Size, centre: (after: Int) -> Offset) {
            val lit1 = lit && named(n + line)
            if (lit1) drawRoundRect(overlay.glow.copy(alpha = 0.16f * overlay.pulse), band, bandSize, CornerRadius(u * 0.2f))
            val tint = if (bad[line]) scheme.error else if (done[line]) scheme.primary else Color.Unspecified
            if (tint.isSpecified) drawRoundRect(tint.copy(alpha = 0.16f), band, bandSize, CornerRadius(u * 0.2f))
            val numbers = text[line]
            for ((k, layout) in numbers.withIndex()) {
                val (cx, cy) = centre(numbers.size - 1 - k)
                val alpha = if (lit && !lit1) 0.35f else 1f
                drawText(layout, if (tint.isSpecified) tint.copy(alpha = alpha) else scheme.onSurface.copy(alpha = alpha), Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
            }
        }
        for (r in 0 until s.height) {
            clues(r, Offset(0f, g.gridY + r * u), Size(g.gridX, u)) { after ->
                Offset(g.gridX - CLUE_GAP * u - after * SLOT_W * u - SLOT_W * u / 2f, g.gridY + (r + 0.5f) * u)
            }
        }
        for (c in 0 until s.width) {
            clues(s.height + c, Offset(g.gridX + c * u, 0f), Size(u, g.gridY)) { after ->
                Offset(g.gridX + (c + 0.5f) * u, g.gridY - CLUE_GAP * u - after * SLOT_H * u - SLOT_H * u / 2f)
            }
        }

        if (!lit) return

        // Everything the hint does not name steps back, so a glowing line is findable on 225 squares.
        for (i in 0 until n) {
            if (named(i)) continue
            drawRect(
                scheme.background.copy(alpha = 0.55f),
                Offset(g.gridX + (i % s.width) * u, g.gridY + (i / s.width) * u),
                Size(u, u),
            )
        }

        hintRims(highlight, u, overlay.glow, overlay.pulse, scheme.background) { set, width, colour ->
            squareRim(set, width, colour, s.width, s.height, { i -> Rect(Offset(g.gridX + (i % s.width) * u, g.gridY + (i / s.width) * u), Size(u, u)) })
        }
    }

    /** Which lines (rows, then columns) show their clue. */
    private fun linesDone(s: NonogramState, clues: List<List<Int>>) = BooleanArray(clues.size) { NonogramLogic.lineDone(s, it, clues[it]) }

    /** Which lines (rows, then columns) no legal layout of their clue can hold. */
    private fun linesImpossible(s: NonogramState, clues: List<List<Int>>) =
        BooleanArray(clues.size) { NonogramLogic.lineImpossible(clues[it], s.cells, NonogramLogic.lineCells(s.width, s.height, it)) }

    private fun paletteFor(s: NonogramState): Pair<Color, Color> =
        PALETTES[(s.solution.hashCode() and Int.MAX_VALUE) % PALETTES.size].let { Color(it.first) to Color(it.second) }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as NonogramState
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current

        // The pen, the sweep and the keyboard cursor are conveniences of the screen, not the board
        // (see PenBoard): a pen stored in the state would make "switch to Cross" a step to walk back.
        var pen by rememberSaveable(s.solution) { mutableStateOf(NonogramLogic.FILLED) }
        val sweep = remember(s.solution) { mutableStateOf<Sweep?>(null) }
        val cursor = remember(s.solution) { mutableStateOf<Int?>(null) }
        val clues = remember(s.solution) { NonogramLogic.clues(s) }

        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        // A rim that breathes is findable at a glance on a 15x15 board. Only runs while something
        // glows, and is read in the draw phase.
        val pulse = rememberHintPulse(highlight.strong.isNotEmpty())

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .penKeys(s, interactive, NONOGRAM_KEYS, cursor, onState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val g = Geometry(s, clues, maxWidth, maxHeight, density)
                val text = remember(s.solution, g.u) { measureClues(measurer, clues, g.u * 0.52f, density) }

                val shown = s.swept(sweep.value)
                val palette = remember(s.solution) { paletteFor(s) }
                // Lines finished by their clues, on the board as handed (not the sweep still under the finger).
                val units = remember(s.solution) { lineUnits(s.width, s.height) }
                val done = linesDone(s, clues)
                val feel = rememberMoveFeel(s.cells.toList(), units) { done[it] }

                // A line must stay impossible for a second, and be so now, before it turns red.
                // The next change cancels the wait and starts it over.
                val bad = rememberSettledFlags(linesImpossible(shown, clues), s.solution, shown.cells)

                Box(g.box) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawBoard(shown, g, text, linesDone(shown, clues), scheme, palette, bad, HintGlow(highlight, glow, pulse.value), feel, Color(accent))
                    }
                    // Taps and sweeps are read over the grid alone, in its own coordinates; the
                    // clues beside it are not part of the play area.
                    Box(g.grid.penGrid(s, interactive, pen, g.u, sweep, cursor, scheme.primary, onState))
                }
            }

            PenRow(s.pens, pen, interactive, Color(accent), { if (it == NonogramLogic.FILLED) "Fill" else "Cross" }, { pen = it }) { option ->
                if (option == NonogramLogic.FILLED) {
                    drawRoundRect(Color(accent), Offset(2f, 2f), Size(size.width - 4f, size.height - 4f), CornerRadius(size.width * 0.18f))
                } else {
                    val a = size.width * 0.2f
                    val ink = scheme.onSurface.copy(alpha = 0.85f)
                    drawLine(ink, Offset(a, a), Offset(size.width - a, size.height - a), 3f, StrokeCap.Round)
                    drawLine(ink, Offset(size.width - a, a), Offset(a, size.height - a), 3f, StrokeCap.Round)
                }
            }
        }
    }

    // ---- the home motif -------------------------------------------------------------------------

    /**
     * A fixed little board for the home grid, drawn by the board's own code so the tile shows the
     * real thing: numbers, filled squares, a cross. The picture is a legal one: its clues are the
     * clues of what is filled in. Never calls `generate()`.
     */
    private val motif = NonogramState(
        4, 4, "0110" + "1111" + "1001" + "0110",
        cells = ".##." + "####" + "#xx#" + "....",
    )

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val clues = remember { NonogramLogic.clues(motif) }
        val done = remember { linesDone(motif, clues) }
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val g = Geometry(motif, clues, maxWidth, maxHeight, density)
            val text = remember(g.u) { measureClues(measurer, clues, g.u * 0.52f, density) }
            Canvas(g.box) {
                drawBoard(motif, g, text, done, scheme, paletteFor(motif))
            }
        }
    }
}
